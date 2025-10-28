package com.robustpatcher.services

import com.robustpatcher.models.*
import com.robustpatcher.services.matchers.TokenMatcher
import java.io.File

/**
 * Исполнитель патчей с внедрёнными зависимостями
 *
 * @param baseDir Базовая директория для применения патчей
 * @param textNormalizer Сервис нормализации текста
 * @param levenshteinCalculator Калькулятор расстояния Левенштейна
 * @param signatureParser Парсер сигнатур функций и классов
 * @param tokenMatcher Matcher для токенизированного поиска
 */
class PatchExecutor(
    private val baseDir: File,
    private val textNormalizer: TextNormalizer = TextNormalizer(),
    private val levenshteinCalculator: LevenshteinCalculator = LevenshteinCalculator(),
    private val signatureParser: SignatureParser = SignatureParser(),
    private val tokenMatcher: TokenMatcher = TokenMatcher()
) {

    // ============================================
    // Public API
    // ============================================

    /**
     * Выполняет патч для файла
     */
    fun execute(filePatch: FilePatch, dryRun: Boolean): FilePatchResult {
        val file = File(baseDir, filePatch.file)

        return when (val action = filePatch.action) {
            is PatchAction.CreateFile -> executeCreateFile(file, filePatch, action, dryRun)
            is PatchAction.ReplaceFile -> executeReplaceFile(file, filePatch, action, dryRun)
            is PatchAction.DeleteFile -> executeDeleteFile(file, filePatch, dryRun)
            is PatchAction.MoveFile -> executeMoveFile(file, filePatch, action, dryRun)
            else -> executeContentAction(file, filePatch, dryRun)
        }
    }

    /**
     * Находит совпадение паттерна в контенте
     */
    fun findMatch(
        content: String,
        searchPattern: String,
        options: MatchOptions,
        lineEnding: String
    ): MatchResult? {
        val searchContent = options.anchor?.let {
            findAnchorContext(content, it, lineEnding) ?: return null
        } ?: content

        return when (options.mode) {
            MatchMode.NORMALIZED -> findNormalizedMatch(searchContent, searchPattern, options, lineEnding)
            MatchMode.FUZZY -> findFuzzyMatch(searchContent, searchPattern, options, lineEnding)
            MatchMode.TOKENIZED -> findTokenizedMatch(searchContent, searchPattern, options)
            MatchMode.SEMANTIC -> findSemanticMatch(searchContent, searchPattern, options)
            MatchMode.REGEX -> findRegexMatch(searchContent, searchPattern, options)
            MatchMode.CONTAINS -> findContainsMatch(searchContent, searchPattern, options)
            MatchMode.LINE_RANGE -> findLineRangeMatch(searchContent, searchPattern, lineEnding)
        }
    }

    // ============================================
    // File Operations
    // ============================================

    private fun executeCreateFile(
        file: File,
        filePatch: FilePatch,
        action: PatchAction.CreateFile,
        dryRun: Boolean
    ): FilePatchResult {
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

    private fun executeReplaceFile(
        file: File,
        filePatch: FilePatch,
        action: PatchAction.ReplaceFile,
        dryRun: Boolean
    ): FilePatchResult {
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

    private fun executeDeleteFile(
        file: File,
        filePatch: FilePatch,
        dryRun: Boolean
    ): FilePatchResult {
        if (!file.exists()) {
            return FilePatchResult(
                filePatch.file,
                filePatch.description,
                "delete_file",
                PatchStatus.SKIPPED,
                "Файл не существует"
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

    private fun executeMoveFile(
        file: File,
        filePatch: FilePatch,
        action: PatchAction.MoveFile,
        dryRun: Boolean
    ): FilePatchResult {
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

    // ============================================
    // Content Operations
    // ============================================

    private fun executeContentAction(
        file: File,
        filePatch: FilePatch,
        dryRun: Boolean
    ): FilePatchResult {
        if (!file.exists()) {
            return FilePatchResult(
                filePatch.file,
                filePatch.description,
                getActionName(filePatch.action),
                PatchStatus.FILE_NOT_FOUND,
                "Файл не существует"
            )
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
        val lineEnding = textNormalizer.detectLineEnding(content)

        val newContent = when (val action = filePatch.action) {
            is PatchAction.Replace -> processReplace(content, action, lineEnding)
            is PatchAction.InsertBefore -> processInsertBefore(content, action, lineEnding)
            is PatchAction.InsertAfter -> processInsertAfter(content, action, lineEnding)
            is PatchAction.Delete -> processDelete(content, action, lineEnding)
            else -> content
        } ?: return FilePatchResult(
            filePatch.file,
            filePatch.description,
            actionName,
            PatchStatus.SKIPPED,
            "Блок не найден (режим: ${(filePatch.action as? PatchAction.Replace)?.matchOptions?.mode ?: "unknown"})"
        )

        if (newContent == content) {
            return FilePatchResult(
                filePatch.file,
                filePatch.description,
                actionName,
                PatchStatus.SKIPPED,
                "Содержимое не изменилось"
            )
        }

        return FilePatchResult(
            filePatch.file,
            filePatch.description,
            actionName,
            PatchStatus.SUCCESS,
            newContent
        )
    }

    private fun processReplace(
        content: String,
        action: PatchAction.Replace,
        lineEnding: String
    ): String? {
        val matchResult = findMatch(content, action.find, action.matchOptions, lineEnding)
            ?: return null

        val finalReplace = textNormalizer.prepareForSearch(action.replace, lineEnding)
        return replaceMatchPreservingIndent(content, matchResult, finalReplace, lineEnding)
    }

    private fun processInsertBefore(
        content: String,
        action: PatchAction.InsertBefore,
        lineEnding: String
    ): String? {
        val matchResult = findMatch(content, action.marker, action.matchOptions, lineEnding)
            ?: return null

        val finalContent = textNormalizer.prepareForSearch(action.content, lineEnding)
        return content.replaceRange(matchResult.index, matchResult.index, finalContent + lineEnding)
    }

    private fun processInsertAfter(
        content: String,
        action: PatchAction.InsertAfter,
        lineEnding: String
    ): String? {
        val matchResult = findMatch(content, action.marker, action.matchOptions, lineEnding)
            ?: return null

        val finalContent = textNormalizer.prepareForSearch(action.content, lineEnding)
        return content.replaceRange(
            matchResult.index + matchResult.length,
            matchResult.index + matchResult.length,
            lineEnding + finalContent
        )
    }

    private fun processDelete(
        content: String,
        action: PatchAction.Delete,
        lineEnding: String
    ): String? {
        val matchResult = findMatch(content, action.find, action.matchOptions, lineEnding)
            ?: return null

        return content.replaceRange(matchResult.index, matchResult.index + matchResult.length, "")
    }

    // ============================================
    // Match Strategies
    // ============================================

    /**
     * Точное совпадение после нормализации
     */
    private fun findNormalizedMatch(
        content: String,
        pattern: String,
        options: MatchOptions,
        lineEnding: String
    ): MatchResult? {
        val normalizedPattern = textNormalizer.normalizeForFuzzy(
            pattern,
            options.ignoreComments,
            options.ignoreEmptyLines
        )

        val patternLines = pattern.lines()
        val contentLines = content.lines()

        for (startLine in contentLines.indices) {
            val endLine = minOf(startLine + patternLines.size, contentLines.size)
            val windowLines = contentLines.subList(startLine, endLine)
            val window = windowLines.joinToString(lineEnding)

            val normalizedWindow = textNormalizer.normalizeForFuzzy(
                window,
                options.ignoreComments,
                options.ignoreEmptyLines
            )

            val s1 = if (options.caseSensitive) normalizedPattern else normalizedPattern.lowercase()
            val s2 = if (options.caseSensitive) normalizedWindow else normalizedWindow.lowercase()

            if (s1 == s2) {
                val beforeLines = contentLines.subList(0, startLine)
                val offset = beforeLines.joinToString(lineEnding).length +
                        (if (beforeLines.isNotEmpty()) lineEnding.length else 0)

                return MatchResult(offset, window.length, window)
            }
        }

        return null
    }

    /**
     * Приближенное совпадение с использованием Levenshtein
     */
    private fun findFuzzyMatch(
        content: String,
        pattern: String,
        options: MatchOptions,
        lineEnding: String
    ): MatchResult? {
        // Если threshold >= 1.0, используем точное совпадение
        if (options.fuzzyThreshold >= 1.0) {
            return findNormalizedMatch(content, pattern, options, lineEnding)
        }

        val contentLines = content.lines()
        val patternLines = pattern.lines()
        val patternLineCount = patternLines.size

        var bestMatch: MatchResult? = null
        var bestSimilarity = 0.0

        // Скользящее окно по количеству строк pattern
        for (startLine in 0..contentLines.size - patternLineCount) {
            val endLine = startLine + patternLineCount
            val windowLines = contentLines.subList(startLine, endLine)
            val window = windowLines.joinToString(lineEnding)

            val similarity = calculateFuzzySimilarity(pattern, window, options)

            if (similarity >= options.fuzzyThreshold && similarity > bestSimilarity) {
                val beforeLines = contentLines.subList(0, startLine)
                val offset = beforeLines.joinToString(lineEnding).length +
                        (if (beforeLines.isNotEmpty()) lineEnding.length else 0)

                bestMatch = MatchResult(offset, window.length, window)
                bestSimilarity = similarity
            }
        }

        return bestMatch
    }

    /**
     * Поиск по токенам
     */
    private fun findTokenizedMatch(
        content: String,
        pattern: String,
        options: MatchOptions
    ): MatchResult? {
        val result = if (options.fuzzyThreshold >= 1.0) {
            tokenMatcher.findTokenizedMatch(content, pattern, options)
        } else {
            tokenMatcher.findTokenizedMatchFuzzy(content, pattern, options, options.fuzzyThreshold)
        }

        return result?.let {
            MatchResult(it.index, it.length, it.matchedText)
        }
    }

    /**
     * Поиск по сигнатуре функции/класса
     */
    private fun findSemanticMatch(
        content: String,
        pattern: String,
        options: MatchOptions
    ): MatchResult? {
        signatureParser.extractFunctionSignature(pattern)?.let { targetSignature ->
            signatureParser.findFunctionBySignature(content, targetSignature, options)?.let { (index, length) ->
                val matchedText = content.substring(index, index + length)
                return MatchResult(index, length, matchedText)
            }
        }

        signatureParser.extractClassSignature(pattern)?.let { targetSignature ->
            signatureParser.findClassBySignature(content, targetSignature, options)?.let { (index, length) ->
                val matchedText = content.substring(index, index + length)
                return MatchResult(index, length, matchedText)
            }
        }

        return null
    }

    /**
     * Поиск по регулярному выражению
     */
    private fun findRegexMatch(
        content: String,
        pattern: String,
        options: MatchOptions
    ): MatchResult? {
        val regex = if (options.caseSensitive) {
            Regex(pattern)
        } else {
            Regex(pattern, RegexOption.IGNORE_CASE)
        }

        val match = regex.find(content)
        return match?.let {
            MatchResult(it.range.first, it.value.length, it.value)
        }
    }

    /**
     * Поиск подстроки (contains)
     */
    private fun findContainsMatch(
        content: String,
        pattern: String,
        options: MatchOptions
    ): MatchResult? {
        val search = if (options.caseSensitive) pattern else pattern.lowercase()
        val target = if (options.caseSensitive) content else content.lowercase()

        val index = target.indexOf(search)
        return if (index >= 0) {
            MatchResult(
                index,
                pattern.length,
                content.substring(index, index + pattern.length)
            )
        } else {
            null
        }
    }

    /**
     * Поиск по диапазону строк
     */
    private fun findLineRangeMatch(
        content: String,
        range: String,
        lineEnding: String
    ): MatchResult? {
        val lines = content.split(lineEnding)
        val parts = range.split("-")
        val startLine = parts[0].trim().toIntOrNull() ?: return null
        val endLine = if (parts.size > 1) {
            parts[1].trim().toIntOrNull() ?: startLine
        } else {
            startLine
        }

        if (startLine < 1 || endLine > lines.size || startLine > endLine) {
            return null
        }

        val selectedLines = lines.subList(startLine - 1, endLine)
        val matchText = selectedLines.joinToString(lineEnding)

        val beforeLines = lines.subList(0, startLine - 1)
        val offset = beforeLines.joinToString(lineEnding).length +
                (if (beforeLines.isNotEmpty()) lineEnding.length else 0)

        return MatchResult(offset, matchText.length, matchText)
    }

    // ============================================
    // Helper Methods
    // ============================================

    private fun calculateFuzzySimilarity(
        text1: String,
        text2: String,
        options: MatchOptions
    ): Double {
        val norm1 = textNormalizer.normalizeForFuzzy(
            text1,
            options.ignoreComments,
            options.ignoreEmptyLines
        )
        val norm2 = textNormalizer.normalizeForFuzzy(
            text2,
            options.ignoreComments,
            options.ignoreEmptyLines
        )

        val s1 = if (options.caseSensitive) norm1 else norm1.lowercase()
        val s2 = if (options.caseSensitive) norm2 else norm2.lowercase()

        return levenshteinCalculator.calculateSimilarity(s1, s2, options.fuzzyThreshold)
    }

    private fun findAnchorContext(
        content: String,
        anchor: AnchorOptions,
        lineEnding: String
    ): String? {
        val anchorIndex = when (anchor.matchMode) {
            MatchMode.NORMALIZED -> {
                findFuzzyMatch(
                    content,
                    anchor.anchorText,
                    MatchOptions(mode = MatchMode.NORMALIZED, fuzzyThreshold = 1.0),
                    lineEnding
                )?.index ?: -1
            }
            MatchMode.FUZZY -> {
                findFuzzyMatch(
                    content,
                    anchor.anchorText,
                    MatchOptions(mode = MatchMode.FUZZY, fuzzyThreshold = 0.85),
                    lineEnding
                )?.index ?: -1
            }
            MatchMode.SEMANTIC -> {
                findSemanticMatch(
                    content,
                    anchor.anchorText,
                    MatchOptions(mode = MatchMode.SEMANTIC)
                )?.index ?: -1
            }
            else -> {
                findFuzzyMatch(
                    content,
                    anchor.anchorText,
                    MatchOptions(mode = MatchMode.NORMALIZED, fuzzyThreshold = 1.0),
                    lineEnding
                )?.index ?: -1
            }
        }

        if (anchorIndex < 0) return null

        return when (anchor.scope) {
            AnchorScope.FUNCTION, AnchorScope.CLASS, AnchorScope.BLOCK, AnchorScope.AUTO -> {
                extractBlock(content, anchorIndex, anchor.searchDepth)
            }
            AnchorScope.FILE -> content
        }
    }

    private fun extractBlock(content: String, startIndex: Int, searchDepth: Int): String? {
        var braceIndex = content.indexOf('{', startIndex)
        if (braceIndex < 0) return null

        var braceCount = 1
        var endIndex = braceIndex + 1
        var depth = 0

        while (endIndex < content.length && (braceCount > 0 || depth < searchDepth)) {
            when (content[endIndex]) {
                '{' -> {
                    braceCount++
                    if (braceCount == 1) depth++
                }
                '}' -> {
                    braceCount--
                    if (braceCount == 0 && (searchDepth == -1 || depth >= searchDepth)) {
                        endIndex++
                        break
                    }
                }
            }
            endIndex++
        }

        return if (braceCount == 0 || searchDepth == -1) {
            content.substring(startIndex, endIndex)
        } else {
            null
        }
    }

    private fun replaceMatchPreservingIndent(
        content: String,
        matchResult: MatchResult,
        replacement: String,
        lineEnding: String
    ): String {
        // Находим начало первой строки
        val firstLineStart = if (matchResult.index == 0) {
            0
        } else {
            val lastNewline = content.lastIndexOf(lineEnding, matchResult.index - 1)
            if (lastNewline < 0) 0 else lastNewline + lineEnding.length
        }

        // Находим конец последней строки match
        val matchEnd = matchResult.index + matchResult.length
        val lastLineEnd = content.indexOf(lineEnding, matchEnd - 1).let {
            if (it < 0 || it < matchEnd) {
                val nextNewline = content.indexOf(lineEnding, matchEnd)
                if (nextNewline < 0) content.length else nextNewline
            } else {
                it
            }
        }

        // Извлекаем первую строку для определения отступа
        val firstLine = content.substring(
            firstLineStart,
            minOf(
                content.indexOf(lineEnding, firstLineStart).let {
                    if (it < 0) content.length else it
                },
                content.length
            )
        )
        val indent = textNormalizer.extractIndent(firstLine)

        // Применяем отступ ко всем строкам replacement
        val indentedReplacement = textNormalizer.applyIndent(replacement, indent, lineEnding)

        return content.replaceRange(firstLineStart, lastLineEnd, indentedReplacement)
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