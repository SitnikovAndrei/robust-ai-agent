#!/usr/bin/env kotlin

/**
 * Улучшенный патчер с надёжным форматом
 * 
 * Формат патча:
 * 
 * === PATCH START ===
 * NAME: Fix Windows interrupt
 * DESCRIPTION: Исправляет обработку Ctrl+C
 * AUTHOR: DevOps
 * VERSION: 1.0
 * 
 * --- FILE: src/main/kotlin/Example.kt ---
 * ACTION: replace
 * DESCRIPTION: Замена метода
 * 
 * <<< FIND
 * fun oldMethod() {
 *     println("old")
 * }
 * FIND >>>
 * 
 * <<< REPLACE
 * fun newMethod() {
 *     println("new")
 * }
 * REPLACE >>>
 * 
 * --- FILE: old/path/file.kt ---
 * ACTION: move_file
 * DESCRIPTION: Переместить файл
 * 
 * <<< TO
 * new/path/file.kt
 * TO >>>
 * 
 * === PATCH END ===
 */

import java.io.File

data class PatchMetadata(
    val name: String,
    val description: String,
    val author: String,
    val version: String
)

sealed class PatchAction {
    data class Replace(val find: String, val replace: String) : PatchAction()
    data class InsertBefore(val marker: String, val content: String) : PatchAction()
    data class InsertAfter(val marker: String, val content: String) : PatchAction()
    data class Delete(val find: String) : PatchAction()
    data class CreateFile(val content: String) : PatchAction()
    data class DeleteFile(val confirm: Boolean = false) : PatchAction()
    data class MoveFile(val destination: String, val overwrite: Boolean = false) : PatchAction()
}

data class FilePatch(
    val file: String,
    val description: String,
    val action: PatchAction
)

enum class PatchStatus {
    SUCCESS, SKIPPED, FAILED, FILE_NOT_FOUND
}

data class FilePatchResult(
    val file: String,
    val status: PatchStatus,
    val error: String? = null
)

class RobustPatcher(private val baseDir: File = File(".")) {
    
    private val stats = mutableMapOf<String, Int>()
    
    fun applyPatch(patchContent: String, dryRun: Boolean = false) {
        val (metadata, patches) = parsePatch(patchContent)
        
        println("=".repeat(70))
        println("📦 Патч: ${metadata.name}")
        println("📝 Описание: ${metadata.description}")
        println("👤 Автор: ${metadata.author}")
        println("🔢 Версия: ${metadata.version}")
        if (dryRun) {
            println("🔍 Режим: DRY RUN (без изменений)")
        }
        println("=".repeat(70))
        println()
        
        patches.forEachIndexed { index, filePatch ->
            println("[${index + 1}/${patches.size}] 📄 ${filePatch.file}")
            println("    ➜ ${filePatch.description}")
            
            val result = applyFilePatch(filePatch, dryRun)
            
            when (result.status) {
                PatchStatus.SUCCESS -> {
                    println("    ✅ Успешно применён")
                    stats.merge("success", 1, Int::plus)
                }
                PatchStatus.SKIPPED -> {
                    println("    ⭐️ Пропущен: ${result.error}")
                    stats.merge("skipped", 1, Int::plus)
                }
                PatchStatus.FAILED -> {
                    println("    ❌ Ошибка: ${result.error}")
                    stats.merge("failed", 1, Int::plus)
                }
                PatchStatus.FILE_NOT_FOUND -> {
                    println("    ❌ Файл не найден")
                    stats.merge("failed", 1, Int::plus)
                }
            }
            println()
        }
        
        printSummary(dryRun)
    }
    
    private fun applyFilePatch(filePatch: FilePatch, dryRun: Boolean): FilePatchResult {
        val file = File(baseDir, filePatch.file)
        
        // Специальная обработка для create_file, delete_file и move
        when (val action = filePatch.action) {
            is PatchAction.CreateFile -> {
                if (file.exists()) {
                    return FilePatchResult(
                        file = filePatch.file,
                        status = PatchStatus.SKIPPED,
                        error = "Файл уже существует"
                    )
                }
                
                if (!dryRun) {
                    try {
                        file.parentFile?.mkdirs()
                        file.writeText(action.content)
                    } catch (e: Exception) {
                        return FilePatchResult(
                            file = filePatch.file,
                            status = PatchStatus.FAILED,
                            error = "Ошибка создания: ${e.message}"
                        )
                    }
                }
                
                return FilePatchResult(
                    file = filePatch.file,
                    status = PatchStatus.SUCCESS
                )
            }
            
            is PatchAction.DeleteFile -> {
                if (!file.exists()) {
                    return FilePatchResult(
                        file = filePatch.file,
                        status = PatchStatus.SKIPPED,
                        error = "Файл не существует"
                    )
                }
                
                if (!action.confirm) {
                    return FilePatchResult(
                        file = filePatch.file,
                        status = PatchStatus.FAILED,
                        error = "Требуется подтверждение удаления (CONFIRM: true)"
                    )
                }
                
                if (!dryRun) {
                    try {
                        val backupFile = File(file.absolutePath + ".deleted")
                        file.copyTo(backupFile, overwrite = true)
                        file.delete()
                    } catch (e: Exception) {
                        return FilePatchResult(
                            file = filePatch.file,
                            status = PatchStatus.FAILED,
                            error = "Ошибка удаления: ${e.message}"
                        )
                    }
                }
                
                return FilePatchResult(
                    file = filePatch.file,
                    status = PatchStatus.SUCCESS
                )
            }
            
            is PatchAction.MoveFile -> {
                if (!file.exists()) {
                    return FilePatchResult(
                        file = filePatch.file,
                        status = PatchStatus.FILE_NOT_FOUND,
                        error = "Исходный файл не существует"
                    )
                }
                
                val destination = File(baseDir, action.destination)
                
                if (destination.exists() && !action.overwrite) {
                    return FilePatchResult(
                        file = filePatch.file,
                        status = PatchStatus.FAILED,
                        error = "Файл назначения уже существует (используйте OVERWRITE: true)"
                    )
                }
                
                if (!dryRun) {
                    try {
                        destination.parentFile?.mkdirs()
                        
                        // Создаём резервную копию если перезаписываем
                        if (destination.exists()) {
                            val backupFile = File(destination.absolutePath + ".backup")
                            destination.copyTo(backupFile, overwrite = true)
                        }
                        
                        file.copyTo(destination, overwrite = true)
                        file.delete()
                    } catch (e: Exception) {
                        return FilePatchResult(
                            file = filePatch.file,
                            status = PatchStatus.FAILED,
                            error = "Ошибка перемещения: ${e.message}"
                        )
                    }
                }
                
                return FilePatchResult(
                    file = filePatch.file,
                    status = PatchStatus.SUCCESS
                )
            }
            
            else -> {
                // Для остальных операций файл должен существовать
                if (!file.exists()) {
                    return FilePatchResult(
                        file = filePatch.file,
                        status = PatchStatus.FILE_NOT_FOUND,
                        error = "Файл не существует"
                    )
                }
            }
        }
        
        val content = file.readText()
        val newContent = when (val action = filePatch.action) {
            is PatchAction.Replace -> {
                if (!content.contains(action.find)) {
                    return FilePatchResult(
                        file = filePatch.file,
                        status = PatchStatus.SKIPPED,
                        error = "Блок не найден. Первые 50 символов искомого: ${action.find.take(50).replace("\n", "\\n")}"
                    )
                }
                content.replace(action.find, action.replace)
            }
            
            is PatchAction.InsertBefore -> {
                if (!content.contains(action.marker)) {
                    return FilePatchResult(
                        file = filePatch.file,
                        status = PatchStatus.SKIPPED,
                        error = "Маркер не найден"
                    )
                }
                content.replace(action.marker, action.content + "\n" + action.marker)
            }
            
            is PatchAction.InsertAfter -> {
                if (!content.contains(action.marker)) {
                    return FilePatchResult(
                        file = filePatch.file,
                        status = PatchStatus.SKIPPED,
                        error = "Маркер не найден"
                    )
                }
                content.replace(action.marker, action.marker + "\n" + action.content)
            }
            
            is PatchAction.Delete -> {
                if (!content.contains(action.find)) {
                    return FilePatchResult(
                        file = filePatch.file,
                        status = PatchStatus.SKIPPED,
                        error = "Блок для удаления не найден"
                    )
                }
                content.replace(action.find, "")
            }
            
            else -> content
        }
        
        if (newContent == content) {
            return FilePatchResult(
                file = filePatch.file,
                status = PatchStatus.SKIPPED,
                error = "Содержимое не изменилось"
            )
        }
        
        if (!dryRun) {
            try {
                file.writeText(newContent)
            } catch (e: Exception) {
                return FilePatchResult(
                    file = filePatch.file,
                    status = PatchStatus.FAILED,
                    error = "Ошибка записи: ${e.message}"
                )
            }
        }
        
        return FilePatchResult(
            file = filePatch.file,
            status = PatchStatus.SUCCESS
        )
    }
    
    private fun parsePatch(content: String): Pair<PatchMetadata, List<FilePatch>> {
        val lines = content.lines()
        var i = 0
        
        // Ищем начало патча
        while (i < lines.size && lines[i].trim() != "=== PATCH START ===") {
            i++
        }
        i++ // Пропускаем строку с PATCH START
        
        // Парсим метаданные
        val metadata = mutableMapOf<String, String>()
        while (i < lines.size) {
            val line = lines[i].trim()
            if (line.isEmpty() || line.startsWith("---")) break
            
            if (line.contains(":")) {
                val (key, value) = line.split(":", limit = 2)
                metadata[key.trim()] = value.trim()
            }
            i++
        }
        
        val patchMetadata = PatchMetadata(
            name = metadata["NAME"] ?: "Unnamed",
            description = metadata["DESCRIPTION"] ?: "",
            author = metadata["AUTHOR"] ?: "Unknown",
            version = metadata["VERSION"] ?: "1.0"
        )
        
        // Парсим патчи для файлов
        val patches = mutableListOf<FilePatch>()
        
        while (i < lines.size) {
            val line = lines[i].trim()
            
            if (line == "=== PATCH END ===") break
            
            if (line.startsWith("--- FILE:")) {
                val file = line.substringAfter("--- FILE:").substringBefore("---").trim()
                i++
                
                // Читаем параметры патча
                var action = ""
                var description = ""
                
                while (i < lines.size) {
                    val paramLine = lines[i].trim()
                    if (paramLine.startsWith("<<<")) break
                    
                    when {
                        paramLine.startsWith("ACTION:") -> action = paramLine.substringAfter(":").trim()
                        paramLine.startsWith("DESCRIPTION:") -> description = paramLine.substringAfter(":").trim()
                    }
                    i++
                }
                
                // Читаем блоки контента
                val blocks = mutableMapOf<String, String>()
                
                while (i < lines.size) {
                    val blockLine = lines[i].trim()
                    
                    if (blockLine.startsWith("---") || blockLine == "=== PATCH END ===") break
                    
                    if (blockLine.startsWith("<<<")) {
                        val blockType = blockLine.substring(3).trim()
                        i++
                        
                        val blockContent = StringBuilder()
                        while (i < lines.size) {
                            val contentLine = lines[i]
                            if (contentLine.trim().endsWith("$blockType >>>")) {
                                break
                            }
                            blockContent.append(contentLine).append("\n")
                            i++
                        }
                        
                        blocks[blockType] = blockContent.toString().trimEnd()
                        i++
                    } else {
                        i++
                    }
                }
                
                // Создаём патч
                val patchAction = when (action.lowercase()) {
                    "replace" -> PatchAction.Replace(
                        find = blocks["FIND"] ?: throw IllegalArgumentException("Missing FIND block"),
                        replace = blocks["REPLACE"] ?: throw IllegalArgumentException("Missing REPLACE block")
                    )
                    "insert_before" -> PatchAction.InsertBefore(
                        marker = blocks["MARKER"] ?: throw IllegalArgumentException("Missing MARKER block"),
                        content = blocks["CONTENT"] ?: throw IllegalArgumentException("Missing CONTENT block")
                    )
                    "insert_after" -> PatchAction.InsertAfter(
                        marker = blocks["MARKER"] ?: throw IllegalArgumentException("Missing MARKER block"),
                        content = blocks["CONTENT"] ?: throw IllegalArgumentException("Missing CONTENT block")
                    )
                    "delete" -> PatchAction.Delete(
                        find = blocks["FIND"] ?: throw IllegalArgumentException("Missing FIND block")
                    )
                    "create_file" -> PatchAction.CreateFile(
                        content = blocks["CONTENT"] ?: throw IllegalArgumentException("Missing CONTENT block")
                    )
                    "delete_file" -> {
                        val confirmStr = blocks["CONFIRM"]?.trim()?.lowercase()
                        PatchAction.DeleteFile(
                            confirm = confirmStr == "true" || confirmStr == "yes"
                        )
                    }
                    "move_file" -> {
                        val overwriteStr = blocks["OVERWRITE"]?.trim()?.lowercase()
                        PatchAction.MoveFile(
                            destination = blocks["TO"] ?: throw IllegalArgumentException("Missing TO block"),
                            overwrite = overwriteStr == "true" || overwriteStr == "yes"
                        )
                    }
                    else -> throw IllegalArgumentException("Unknown action: $action")
                }
                
                patches.add(FilePatch(file, description, patchAction))
            } else {
                i++
            }
        }
        
        return Pair(patchMetadata, patches)
    }
    
    private fun printSummary(dryRun: Boolean) {
        println("=".repeat(70))
        println("📊 ИТОГИ")
        println("=".repeat(70))
        println("✅ Успешно:          ${stats["success"] ?: 0}")
        println("⭐️ Пропущено:        ${stats["skipped"] ?: 0}")
        println("❌ Ошибки:           ${stats["failed"] ?: 0}")
        println("=".repeat(70))
        
        if (dryRun) {
            println()
            println("💡 Это был пробный запуск. Запустите без --dry-run для применения изменений.")
        }
    }
}

// Main execution
if (args.isEmpty()) {
    println("""
        Использование: kotlinc -script RobustPatcher.kts -- <patch-file> [--dry-run]
        
        Примеры:
            kotlinc -script RobustPatcher.kts -- fix.patch
            kotlinc -script RobustPatcher.kts -- fix.patch --dry-run
            
        Доступные действия (ACTION):
            - replace: Замена текста
            - insert_before: Вставка перед маркером
            - insert_after: Вставка после маркера
            - delete: Удаление текста
            - create_file: Создание нового файла
            - delete_file: Удаление файла (требует CONFIRM: true)
            - move_file: Перемещение/переименование файла (блок TO)
    """.trimIndent())
} else {
    val patchFilePath = args[0]
    val dryRun = args.contains("--dry-run")
    
    val patchFile = File(patchFilePath)
    if (!patchFile.exists()) {
        println("❌ Файл патча не найден: $patchFilePath")
    } else {
        println("📂 Базовая директория: ${File(".").absolutePath}")
        println("📄 Файл патча: ${patchFile.absolutePath}")
        println()
        
        val patcher = RobustPatcher(File("."))
        val patchContent = patchFile.readText()
        
        try {
            patcher.applyPatch(patchContent, dryRun)
        } catch (e: Exception) {
            println("❌ Ошибка: ${e.message}")
            e.printStackTrace()
        }
    }
}