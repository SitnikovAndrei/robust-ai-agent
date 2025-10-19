package com.robustpatcher.services

import com.robustpatcher.models.*
import java.io.File

class PatchExecutor(private val baseDir: File) {

    private fun detectLineEnding(content: String): String {
        return if (content.contains("\r\n")) "\r\n" else "\n"
    }

    private fun normalizeLineEndings(text: String): String {
        return text.replace("\r\n", "\n").replace("\r", "\n")
    }

    private fun convertLineEndings(text: String, lineEnding: String): String {
        return if (lineEnding == "\r\n") text.replace("\n", "\r\n") else text
    }

    private fun prepareForSearch(text: String, fileLineEnding: String): String {
        val normalized = normalizeLineEndings(text)
        return convertLineEndings(normalized, fileLineEnding)
    }

    private fun removeComments(text: String): String {
        var result = text
        result = result.replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")
        result = result.lines().map { line ->
            val commentIndex = line.indexOfAny(listOf("//", "#"))
            if (commentIndex >= 0) line.substring(0, commentIndex) else line
        }.joinToString("\n")
        return result
    }

    private fun normalizeForFuzzy(text: String, ignoreComments: Boolean, ignoreEmptyLines: Boolean): String {
        var result = text
        if (ignoreComments) result = removeComments(result)
        val lines = result.lines().map { line ->
            val normalized = line.replace("\t", "    ")
            normalized.trimEnd()
        }
        val filteredLines = if (ignoreEmptyLines) lines.filter { it.isNotBlank() } else lines
        return filteredLines.joinToString("\n")
    }

    private fun calculateFuzzySimilarity(text1: String, text2: String, options: MatchOptions): Double {
        val norm1 = normalizeForFuzzy(text1, options.ignoreComments, options.ignoreEmptyLines)
        val norm2 = normalizeForFuzzy(text2, options.ignoreComments, options.ignoreEmptyLines)
        val s1 = if (options.caseSensitive) norm1 else norm1.lowercase()
        val s2 = if (options.caseSensitive) norm2 else norm2.lowercase()
        if (s1 == s2) return 1.0
        val maxLen = maxOf(s1.length, s2.length)
        if (maxLen == 0) return 1.0
        var matches = 0
        val minLen = minOf(s1.length, s2.length)
        for (i in 0 until minLen) {
            if (s1[i] == s2[i]) matches++
        }
        return matches.toDouble() / maxLen
    }

    private fun findFuzzyMatch(
        content: String,
        pattern: String,
        options: MatchOptions,
        lineEnding: String
    ): MatchResult? {
        val normalizedPattern = normalizeForFuzzy(pattern, options.ignoreComments, options.ignoreEmptyLines)
        val patternLines = normalizedPattern.lines().filter { it.isNotBlank() }
        if (patternLines.isEmpty()) return null

        // Оптимизация: если threshold = 1.0, используем точное сравнение
        if (options.fuzzyThreshold >= 1.0) {
            val normalizedContent = normalizeForFuzzy(content, options.ignoreComments, options.ignoreEmptyLines)
            val s1 = if (options.caseSensitive) normalizedPattern else normalizedPattern.lowercase()
            val s2 = if (options.caseSensitive) normalizedContent else normalizedContent.lowercase()

            val index = s2.indexOf(s1)
            if (index >= 0) {
                // Найдено точное совпадение после нормализации
                // Находим соответствующую позицию в оригинальном контенте
                val contentLines = content.lines()
                val patternLineCount = patternLines.size

                for (startLine in contentLines.indices) {
                    if (startLine + patternLineCount > contentLines.size) break
                    val windowLines = contentLines.subList(startLine, startLine + patternLineCount)
                    val window = windowLines.joinToString("\n")
                    val normalizedWindow = normalizeForFuzzy(window, options.ignoreComments, options.ignoreEmptyLines)
                    val sw = if (options.caseSensitive) normalizedWindow else normalizedWindow.lowercase()

                    if (sw == s1) {
                        val beforeLines = contentLines.subList(0, startLine)
                        val offset =
                            beforeLines.joinToString(lineEnding).length + (if (beforeLines.isNotEmpty()) lineEnding.length else 0)
                        return MatchResult(offset, window.length, window)
                    }
                }
            }
            return null
        }

        // Для threshold < 1.0 используем сравнение с вычислением similarity
        val contentLines = content.lines()
        for (startLine in contentLines.indices) {
            if (startLine + patternLines.size > contentLines.size) break
            val windowLines = contentLines.subList(startLine, startLine + patternLines.size)
            val window = windowLines.joinToString("\n")
            val similarity = calculateFuzzySimilarity(pattern, window, options)
            if (similarity >= options.fuzzyThreshold) {
                val beforeLines = contentLines.subList(0, startLine)
                val offset =
                    beforeLines.joinToString(lineEnding).length + (if (beforeLines.isNotEmpty()) lineEnding.length else 0)
                val matchLength = window.length
                return MatchResult(offset, matchLength, window)
            }
        }
        return null
    }

    private fun findSemanticMatch(
        content: String,
        pattern: String,
        options: MatchOptions
    ): MatchResult? {
        val parser = SignatureParser()
        parser.extractFunctionSignature(pattern)?.let { targetSignature ->
            parser.findFunctionBySignature(content, targetSignature, options)?.let { (index, length) ->
                val matchedText = content.substring(index, index + length)
                return MatchResult(index, length, matchedText)
            }
        }
        parser.extractClassSignature(pattern)?.let { targetSignature ->
            parser.findClassBySignature(content, targetSignature, options)?.let { (index, length) ->
                val matchedText = content.substring(index, index + length)
                return MatchResult(index, length, matchedText)
            }
        }
        return null
    }

    private fun findAnchorContext(content: String, anchor: AnchorOptions, lineEnding: String): String? {
        val anchorIndex = when (anchor.matchMode) {
            MatchMode.STRICT -> content.indexOf(anchor.anchorText)
            MatchMode.FUZZY -> {
                val match = findFuzzyMatch(
                    content,
                    anchor.anchorText,
                    MatchOptions(mode = MatchMode.FUZZY, fuzzyThreshold = 0.9),
                    lineEnding
                )
                match?.index ?: -1
            }

            MatchMode.SEMANTIC -> {
                val match = findSemanticMatch(content, anchor.anchorText, MatchOptions(mode = MatchMode.SEMANTIC))
                match?.index ?: -1
            }

            else -> content.indexOf(anchor.anchorText)
        }
        if (anchorIndex < 0) return null
        return when (anchor.scope) {
            AnchorScope.FUNCTION, AnchorScope.CLASS, AnchorScope.BLOCK, AnchorScope.AUTO -> {
                var braceIndex = content.indexOf('{', anchorIndex)
                if (braceIndex < 0) return null
                var braceCount = 1
                var endIndex = braceIndex + 1
                var depth = 0
                while (endIndex < content.length && (braceCount > 0 || depth < anchor.searchDepth)) {
                    when (content[endIndex]) {
                        '{' -> {
                            braceCount++
                            if (braceCount == 1) depth++
                        }

                        '}' -> {
                            braceCount--
                            if (braceCount == 0 && (anchor.searchDepth == -1 || depth >= anchor.searchDepth)) {
                                endIndex++
                                break
                            }
                        }
                    }
                    endIndex++
                }
                if (braceCount == 0 || anchor.searchDepth == -1) content.substring(anchorIndex, endIndex) else null
            }

            AnchorScope.FILE -> content
        }
    }

    private fun findLineRangeMatch(content: String, range: String, lineEnding: String): MatchResult? {
        val lines = content.split(lineEnding)
        val parts = range.split("-")
        val startLine = parts[0].trim().toIntOrNull() ?: return null
        val endLine = if (parts.size > 1) parts[1].trim().toIntOrNull() ?: startLine else startLine
        if (startLine < 1 || endLine > lines.size || startLine > endLine) return null
        val selectedLines = lines.subList(startLine - 1, endLine)
        val matchText = selectedLines.joinToString(lineEnding)
        val beforeLines = lines.subList(0, startLine - 1)
        val offset =
            beforeLines.joinToString(lineEnding).length + (if (beforeLines.isNotEmpty()) lineEnding.length else 0)
        return MatchResult(offset, matchText.length, matchText)
    }

    private fun findMatch(
        content: String,
        searchPattern: String,
        options: MatchOptions,
        lineEnding: String
    ): MatchResult? {
        val searchContent = if (options.anchor != null) {
            findAnchorContext(content, options.anchor, lineEnding) ?: return null
        } else content
        return when (options.mode) {
            MatchMode.STRICT -> {
                val prepared = prepareForSearch(searchPattern, lineEnding)
                val index = searchContent.indexOf(prepared)
                if (index >= 0) MatchResult(index, prepared.length, prepared) else null
            }

            MatchMode.FUZZY -> findFuzzyMatch(searchContent, searchPattern, options, lineEnding)
            MatchMode.SEMANTIC -> findSemanticMatch(searchContent, searchPattern, options)
            MatchMode.REGEX -> {
                val pattern =
                    if (options.caseSensitive) Regex(searchPattern) else Regex(searchPattern, RegexOption.IGNORE_CASE)
                val match = pattern.find(searchContent)
                match?.let { MatchResult(it.range.first, it.value.length, it.value) }
            }

            MatchMode.CONTAINS -> {
                val search = if (options.caseSensitive) searchPattern else searchPattern.lowercase()
                val target = if (options.caseSensitive) searchContent else searchContent.lowercase()
                val index = target.indexOf(search)
                if (index >= 0) MatchResult(
                    index,
                    searchPattern.length,
                    searchContent.substring(index, index + searchPattern.length)
                ) else null
            }

            MatchMode.LINE_RANGE -> findLineRangeMatch(searchContent, searchPattern, lineEnding)
        }
    }

    data class MatchResult(val index: Int, val length: Int, val matchedText: String)

    fun execute(filePatch: FilePatch, dryRun: Boolean): FilePatchResult {
        val file = File(baseDir, filePatch.file)
        when (val action = filePatch.action) {
            is PatchAction.CreateFile -> {
                if (file.exists()) {
                    return FilePatchResult(
                        filePatch.file,
                        filePatch.description,
                        "create_file",
                        PatchStatus.SKIPPED,
                        "Файл уже существует"
                    )
                }
                if (!dryRun) {
                    try {
                        file.parentFile?.mkdirs()
                        file.writeText(action.content)
                    } catch (e: Exception) {
                        return FilePatchResult(
                            filePatch.file,
                            filePatch.description,
                            "create_file",
                            PatchStatus.FAILED,
                            "Ошибка создания: ${e.message}"
                        )
                    }
                }
                return FilePatchResult(
                    filePatch.file,
                    filePatch.description,
                    "create_file",
                    PatchStatus.SUCCESS,
                    "Файл создан (${action.content.length} символов)"
                )
            }

            is PatchAction.ReplaceFile -> {
                if (!file.exists()) {
                    return FilePatchResult(
                        filePatch.file,
                        filePatch.description,
                        "replace_file",
                        PatchStatus.FILE_NOT_FOUND,
                        "Файл не существует"
                    )
                }
                if (!dryRun) {
                    try {
                        file.writeText(action.content)
                    } catch (e: Exception) {
                        return FilePatchResult(
                            filePatch.file,
                            filePatch.description,
                            "replace_file",
                            PatchStatus.FAILED,
                            "Ошибка замены: ${e.message}"
                        )
                    }
                }
                return FilePatchResult(
                    filePatch.file,
                    filePatch.description,
                    "replace_file",
                    PatchStatus.SUCCESS,
                    "Содержимое файла полностью заменено (${action.content.length} символов)"
                )
            }

            is PatchAction.DeleteFile -> {
                if (!file.exists()) {
                    return FilePatchResult(
                        filePatch.file,
                        filePatch.description,
                        "delete_file",
                        PatchStatus.SKIPPED,
                        "Файл не существует"
                    )
                }
                if (!action.confirm) {
                    return FilePatchResult(
                        filePatch.file,
                        filePatch.description,
                        "delete_file",
                        PatchStatus.FAILED,
                        "Требуется подтверждение (CONFIRM: true)"
                    )
                }
                if (!dryRun) {
                    try {
                        file.delete()
                    } catch (e: Exception) {
                        return FilePatchResult(
                            filePatch.file,
                            filePatch.description,
                            "delete_file",
                            PatchStatus.FAILED,
                            "Ошибка удаления: ${e.message}"
                        )
                    }
                }
                return FilePatchResult(
                    filePatch.file,
                    filePatch.description,
                    "delete_file",
                    PatchStatus.SUCCESS,
                    "Файл удалён"
                )
            }

            is PatchAction.MoveFile -> {
                if (!file.exists()) {
                    return FilePatchResult(
                        filePatch.file,
                        filePatch.description,
                        "move_file",
                        PatchStatus.FILE_NOT_FOUND,
                        "Исходный файл не существует"
                    )
                }
                val destination = File(baseDir, action.destination)
                if (destination.exists() && !action.overwrite) {
                    return FilePatchResult(
                        filePatch.file,
                        filePatch.description,
                        "move_file",
                        PatchStatus.FAILED,
                        "Файл назначения существует (OVERWRITE: true)"
                    )
                }
                if (!dryRun) {
                    try {
                        destination.parentFile?.mkdirs()
                        file.copyTo(destination, overwrite = true)
                        file.delete()
                    } catch (e: Exception) {
                        return FilePatchResult(
                            filePatch.file,
                            filePatch.description,
                            "move_file",
                            PatchStatus.FAILED,
                            "Ошибка перемещения: ${e.message}"
                        )
                    }
                }
                return FilePatchResult(
                    filePatch.file,
                    filePatch.description,
                    "move_file",
                    PatchStatus.SUCCESS,
                    "Файл перемещён в: ${action.destination}"
                )
            }

            else -> {
                if (!file.exists()) {
                    return FilePatchResult(
                        filePatch.file,
                        filePatch.description,
                        getActionName(filePatch.action),
                        PatchStatus.FILE_NOT_FOUND,
                        "Файл не существует"
                    )
                }
            }
        }
        val content = file.readText()
        val result = processContentAction(filePatch, content)
        if (result.status == PatchStatus.SUCCESS && !dryRun) {
            try {
                file.writeText(result.message)
            } catch (e: Exception) {
                return FilePatchResult(
                    filePatch.file,
                    filePatch.description,
                    getActionName(filePatch.action),
                    PatchStatus.FAILED,
                    "Ошибка записи: ${e.message}"
                )
            }
        }
        return result
    }

    private fun processContentAction(filePatch: FilePatch, content: String): FilePatchResult {
        val actionName = getActionName(filePatch.action)
        val lineEnding = detectLineEnding(content)
        val newContent = when (val action = filePatch.action) {
            is PatchAction.Replace -> {
                val matchResult = findMatch(content, action.find, action.matchOptions, lineEnding)
                if (matchResult == null) {
                    return FilePatchResult(
                        filePatch.file,
                        filePatch.description,
                        actionName,
                        PatchStatus.SKIPPED,
                        "Блок не найден (режим: ${action.matchOptions.mode})"
                    )
                }
                val finalReplace = prepareForSearch(action.replace, lineEnding)
                content.replaceRange(matchResult.index, matchResult.index + matchResult.length, finalReplace)
            }

            is PatchAction.InsertBefore -> {
                val matchResult = findMatch(content, action.marker, action.matchOptions, lineEnding)
                if (matchResult == null) {
                    return FilePatchResult(
                        filePatch.file,
                        filePatch.description,
                        actionName,
                        PatchStatus.SKIPPED,
                        "Маркер не найден (режим: ${action.matchOptions.mode})"
                    )
                }
                val finalContent = prepareForSearch(action.content, lineEnding)
                content.replaceRange(matchResult.index, matchResult.index, finalContent + lineEnding)
            }

            is PatchAction.InsertAfter -> {
                val matchResult = findMatch(content, action.marker, action.matchOptions, lineEnding)
                if (matchResult == null) {
                    return FilePatchResult(
                        filePatch.file,
                        filePatch.description,
                        actionName,
                        PatchStatus.SKIPPED,
                        "Маркер не найден (режим: ${action.matchOptions.mode})"
                    )
                }
                val finalContent = prepareForSearch(action.content, lineEnding)
                content.replaceRange(
                    matchResult.index + matchResult.length,
                    matchResult.index + matchResult.length,
                    lineEnding + finalContent
                )
            }

            is PatchAction.Delete -> {
                val matchResult = findMatch(content, action.find, action.matchOptions, lineEnding)
                if (matchResult == null) {
                    return FilePatchResult(
                        filePatch.file,
                        filePatch.description,
                        actionName,
                        PatchStatus.SKIPPED,
                        "Блок не найден (режим: ${action.matchOptions.mode})"
                    )
                }
                content.replaceRange(matchResult.index, matchResult.index + matchResult.length, "")
            }

            else -> content
        }
        if (newContent == content) {
            return FilePatchResult(
                filePatch.file,
                filePatch.description,
                actionName,
                PatchStatus.SKIPPED,
                "Содержимое не изменилось"
            )
        }
        return FilePatchResult(filePatch.file, filePatch.description, actionName, PatchStatus.SUCCESS, newContent)
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