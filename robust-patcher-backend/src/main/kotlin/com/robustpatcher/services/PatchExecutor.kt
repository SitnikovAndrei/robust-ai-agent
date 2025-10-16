package com.robustpatcher.services

import com.robustpatcher.models.*
import java.io.File
import kotlin.text.replace


class PatchExecutor(private val baseDir: File) {

    /**
     * Определяет тип переноса строк используемый в файле
     * @return "\r\n" для Windows, "\n" для Unix/Mac
     */
    private fun detectLineEnding(content: String): String {
        return if (content.contains("\r\n")) "\r\n" else "\n"
    }

    /**
     * Нормализует текст к Unix формату (LF)
     */
    private fun normalizeLineEndings(text: String): String {
        return text.replace("\r\n", "\n").replace("\r", "\n")
    }

    /**
     * Конвертирует текст в нужный формат переносов строк
     * @param text текст с LF переносами
     * @param lineEnding целевой формат ("\r\n" или "\n")
     */
    private fun convertLineEndings(text: String, lineEnding: String): String {
        return if (lineEnding == "\r\n") {
            text.replace("\n", "\r\n")
        } else {
            text
        }
    }

    /**
     * Подготавливает текст для поиска в файле с определённым форматом переносов
     * @param text текст из патча
     * @param fileLineEnding формат переносов файла
     */
    private fun prepareForSearch(text: String, fileLineEnding: String): String {
        val normalized = normalizeLineEndings(text)
        return convertLineEndings(normalized, fileLineEnding)
    }

    fun execute(filePatch: FilePatch, dryRun: Boolean): FilePatchResult {
        val file = File(baseDir, filePatch.file)

        // Специальная обработка для create_file, delete_file и move_file
        when (val action = filePatch.action) {
            is PatchAction.CreateFile -> {
                if (file.exists()) {
                    return FilePatchResult(
                        file = filePatch.file,
                        description = filePatch.description,
                        action = "create_file",
                        status = PatchStatus.SKIPPED,
                        message = "Файл уже существует"
                    )
                }

                if (!dryRun) {
                    try {
                        file.parentFile?.mkdirs()
                        file.writeText(action.content)
                    } catch (e: Exception) {
                        return FilePatchResult(
                            file = filePatch.file,
                            description = filePatch.description,
                            action = "create_file",
                            status = PatchStatus.FAILED,
                            message = "Ошибка создания: ${e.message}"
                        )
                    }
                }

                return FilePatchResult(
                    file = filePatch.file,
                    description = filePatch.description,
                    action = "create_file",
                    status = PatchStatus.SUCCESS,
                    message = "Файл создан (${action.content.length} символов)"
                )
            }

            is PatchAction.ReplaceFile -> {
                if (!file.exists()) {
                    return FilePatchResult(
                        file = filePatch.file,
                        description = filePatch.description,
                        action = "replace_file",
                        status = PatchStatus.FILE_NOT_FOUND,
                        message = "Файл не существует"
                    )
                }

                if (!dryRun) {
                    try {
                        file.writeText(action.content)
                    } catch (e: Exception) {
                        return FilePatchResult(
                            file = filePatch.file,
                            description = filePatch.description,
                            action = "replace_file",
                            status = PatchStatus.FAILED,
                            message = "Ошибка замены: ${e.message}"
                        )
                    }
                }

                return FilePatchResult(
                    file = filePatch.file,
                    description = filePatch.description,
                    action = "replace_file",
                    status = PatchStatus.SUCCESS,
                    message = "Содержимое файла полностью заменено (${action.content.length} символов)"
                )
            }

            is PatchAction.DeleteFile -> {
                if (!file.exists()) {
                    return FilePatchResult(
                        file = filePatch.file,
                        description = filePatch.description,
                        action = "delete_file",
                        status = PatchStatus.SKIPPED,
                        message = "Файл не существует"
                    )
                }

                if (!action.confirm) {
                    return FilePatchResult(
                        file = filePatch.file,
                        description = filePatch.description,
                        action = "delete_file",
                        status = PatchStatus.FAILED,
                        message = "Требуется подтверждение (CONFIRM: true)"
                    )
                }

                if (!dryRun) {
                    try {
                        file.delete()
                    } catch (e: Exception) {
                        return FilePatchResult(
                            file = filePatch.file,
                            description = filePatch.description,
                            action = "delete_file",
                            status = PatchStatus.FAILED,
                            message = "Ошибка удаления: ${e.message}"
                        )
                    }
                }

                return FilePatchResult(
                    file = filePatch.file,
                    description = filePatch.description,
                    action = "delete_file",
                    status = PatchStatus.SUCCESS,
                    message = "Файл удалён"
                )
            }

            is PatchAction.MoveFile -> {
                if (!file.exists()) {
                    return FilePatchResult(
                        file = filePatch.file,
                        description = filePatch.description,
                        action = "move_file",
                        status = PatchStatus.FILE_NOT_FOUND,
                        message = "Исходный файл не существует"
                    )
                }

                val destination = File(baseDir, action.destination)

                if (destination.exists() && !action.overwrite) {
                    return FilePatchResult(
                        file = filePatch.file,
                        description = filePatch.description,
                        action = "move_file",
                        status = PatchStatus.FAILED,
                        message = "Файл назначения существует (OVERWRITE: true)"
                    )
                }

                if (!dryRun) {
                    try {
                        destination.parentFile?.mkdirs()
                        file.copyTo(destination, overwrite = true)
                        file.delete()
                    } catch (e: Exception) {
                        return FilePatchResult(
                            file = filePatch.file,
                            description = filePatch.description,
                            action = "move_file",
                            status = PatchStatus.FAILED,
                            message = "Ошибка перемещения: ${e.message}"
                        )
                    }
                }

                return FilePatchResult(
                    file = filePatch.file,
                    description = filePatch.description,
                    action = "move_file",
                    status = PatchStatus.SUCCESS,
                    message = "Файл перемещён в: ${action.destination}"
                )
            }

            else -> {
                if (!file.exists()) {
                    return FilePatchResult(
                        file = filePatch.file,
                        description = filePatch.description,
                        action = getActionName(filePatch.action),
                        status = PatchStatus.FILE_NOT_FOUND,
                        message = "Файл не существует"
                    )
                }
            }
        }

        val content = file.readText()
        val result = processContentAction(filePatch, content)

        if (result.status == PatchStatus.SUCCESS && !dryRun) {
            try {
                file.writeText(result.message) // В message храним новый контент
            } catch (e: Exception) {
                return FilePatchResult(
                    file = filePatch.file,
                    description = filePatch.description,
                    action = getActionName(filePatch.action),
                    status = PatchStatus.FAILED,
                    message = "Ошибка записи: ${e.message}"
                )
            }
        }

        return result
    }

    private fun processContentAction(filePatch: FilePatch, content: String): FilePatchResult {
        val actionName = getActionName(filePatch.action)

        val newContent = when (val action = filePatch.action) {
            is PatchAction.Replace -> {
                val lineEnding = detectLineEnding(content)
                val searchFind = prepareForSearch(action.find, lineEnding)

                if (!content.contains(searchFind)) {
                    return FilePatchResult(
                        file = filePatch.file,
                        description = filePatch.description,
                        action = actionName,
                        status = PatchStatus.SKIPPED,
                        message = "Блок не найден"
                    )
                }
                val finalReplace = prepareForSearch(action.replace, lineEnding)
                content.replace(searchFind, finalReplace)
            }

            is PatchAction.InsertBefore -> {
                val lineEnding = detectLineEnding(content)
                val searchMarker = prepareForSearch(action.marker, lineEnding)

                if (!content.contains(searchMarker)) {
                    return FilePatchResult(
                        file = filePatch.file,
                        description = filePatch.description,
                        action = actionName,
                        status = PatchStatus.SKIPPED,
                        message = "Маркер не найден"
                    )
                }

                val finalContent = prepareForSearch(action.content, lineEnding)
                content.replace(searchMarker, finalContent + lineEnding + searchMarker)
            }

            is PatchAction.InsertAfter -> {
                val lineEnding = detectLineEnding(content)
                val searchMarker = prepareForSearch(action.marker, lineEnding)

                if (!content.contains(searchMarker)) {
                    return FilePatchResult(
                        file = filePatch.file,
                        description = filePatch.description,
                        action = actionName,
                        status = PatchStatus.SKIPPED,
                        message = "Маркер не найден"
                    )
                }

                val finalContent = prepareForSearch(action.content, lineEnding)
                content.replace(searchMarker, searchMarker + lineEnding + finalContent)
            }

            is PatchAction.Delete -> {
                val lineEnding = detectLineEnding(content)
                val searchFind = prepareForSearch(action.find, lineEnding)

                if (!content.contains(searchFind)) {
                    return FilePatchResult(
                        file = filePatch.file,
                        description = filePatch.description,
                        action = actionName,
                        status = PatchStatus.SKIPPED,
                        message = "Блок не найден"
                    )
                }
                content.replace(searchFind, "")
            }

            else -> content
        }

        if (newContent == content) {
            return FilePatchResult(
                file = filePatch.file,
                description = filePatch.description,
                action = actionName,
                status = PatchStatus.SKIPPED,
                message = "Содержимое не изменилось"
            )
        }

        return FilePatchResult(
            file = filePatch.file,
            description = filePatch.description,
            action = actionName,
            status = PatchStatus.SUCCESS,
            message = newContent // Сохраняем новый контент
        )
    }

    private fun normalizedEnding(lineEnding: String, actionBlock: String): String {
        val normalizedActionBlock : String = actionBlock
            .replace("\r\n", "\n")
            .replace("\r", "\n")

        return if (lineEnding == "\r\n") {
            normalizedActionBlock.replace("\n", "\r\n")
        } else {
            normalizedActionBlock
        }
    }

    private fun getActionName(action: PatchAction): String {
        return when (action) {
            is PatchAction.Replace -> "replace"
            is PatchAction.InsertBefore -> "insert_before"
            is PatchAction.InsertAfter -> "insert_after"
            is PatchAction.Delete -> "delete"
            is PatchAction.CreateFile -> "create_file"
            is PatchAction.ReplaceFile -> "replace_file"
            is PatchAction.DeleteFile -> "delete_file"
            is PatchAction.MoveFile -> "move_file"
        }
    }
}