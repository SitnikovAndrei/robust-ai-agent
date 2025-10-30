package com.robustpatcher.services

import com.robustpatcher.models.*

class PatchParser {

    // Константы для названий блоков - больше никакой путаницы!
    private object BlockNames {
        const val ANCHOR = "ANCHOR"
        const val FIND = "FIND"           // что ищем (для replace, delete)
        const val REPLACE_WITH = "REPLACE_WITH"  // на что заменяем
        const val INSERT_CONTENT = "INSERT_CONTENT"  // что вставляем
        const val DESTINATION = "DESTINATION"  // куда перемещаем
    }

    fun parse(content: String): Pair<PatchMetadata, List<FilePatch>> {
        val lines = content.lines()
        var currentLine = 0

        // Шаг 1: Найти заголовок патча
        currentLine = findPatchHeader(lines, currentLine)

        // Шаг 2: Прочитать метаданные
        val (metadata, nextLine) = parsePatchMetadata(lines, currentLine)
        currentLine = nextLine

        // Шаг 3: Прочитать все файлы
        val filePatches = parseAllFiles(lines, currentLine)

        return Pair(metadata, filePatches)
    }

    // ==================== ПАРСИНГ МЕТАДАННЫХ ====================

    private fun findPatchHeader(lines: List<String>, start: Int): Int {
        var i = start
        while (i < lines.size && !lines[i].trim().startsWith("### PATCH:")) {
            i++
        }
        if (i >= lines.size) {
            throw IllegalArgumentException("PATCH header not found")
        }
        return i
    }

    private fun parsePatchMetadata(lines: List<String>, start: Int): Pair<PatchMetadata, Int> {
        var i = start

        val patchName = lines[i].trim().substringAfter("### PATCH:").trim()
        i++

        var description = ""
        var author = "Unknown"
        var version = "1.0"

        // Читаем метаданные до разделителя ---
        while (i < lines.size) {
            val line = lines[i].trim()

            if (line == "---") {
                i++ // пропускаем разделитель
                break
            }

            when {
                line.startsWith("**Description:**") ->
                    description = line.substringAfter("**Description:**").trim()
                line.startsWith("**Author:**") ->
                    author = line.substringAfter("**Author:**").trim()
                line.startsWith("**Version:**") ->
                    version = line.substringAfter("**Version:**").trim()
            }

            i++
        }

        val metadata = PatchMetadata(
            name = patchName,
            description = description,
            author = author,
            version = version
        )

        return Pair(metadata, i)
    }

    // ==================== ПАРСИНГ ФАЙЛОВ ====================

    private fun parseAllFiles(lines: List<String>, start: Int): List<FilePatch> {
        val patches = mutableListOf<FilePatch>()
        var i = start

        while (i < lines.size) {
            // Пропускаем разделители
            if (lines[i].trim() == "---") {
                i++
                continue
            }

            // Ищем начало секции файла
            if (lines[i].trim().startsWith("#### File:")) {
                val (filePatch, nextLine) = parseFilePatch(lines, i)
                patches.add(filePatch)
                i = nextLine
            } else {
                i++
            }
        }

        return patches
    }

    private fun String.removeBackticks(): String {
        return if (this.startsWith("`") && this.endsWith("`") && this.length >= 2) {
            this.substring(1, this.length - 1)
        } else {
            this
        }
    }

    private fun parseFilePatch(lines: List<String>, start: Int): Pair<FilePatch, Int> {
        var i = start

        // Парсим заголовок файла
        val fileHeader = lines[i].trim()
            .substringAfter("#### File:")
            .trim()

        val isMove = fileHeader.contains("->")
        val filePath = if (isMove) {
            fileHeader.substringBefore("->").trim().removeBackticks()
        } else {
            fileHeader.removeBackticks()
        }
        i++

        // Парсим действие
        var action = ""
        if (i < lines.size && lines[i].trim().startsWith("**Action:**")) {
            action = lines[i].trim().substringAfter("**Action:**").trim()
            i++
        }

        // Парсим описание (опционально)
        var description = ""
        if (i < lines.size && lines[i].trim().startsWith("**Description:**")) {
            description = lines[i].trim().substringAfter("**Description:**").trim()
            i++
        }

        // Парсим опции и блоки кода
        val (options, blocks, nextLine) = parseFileContent(lines, i, action, isMove, fileHeader)
        i = nextLine

        // Создаём действие
        val patchAction = createPatchAction(action, blocks, options)

        return Pair(FilePatch(filePath, description, patchAction), i)
    }

    // ==================== ПАРСИНГ СОДЕРЖИМОГО ФАЙЛА ====================

    private data class FileContent(
        val options: Map<String, String>,
        val blocks: Map<String, String>,
        val nextLine: Int
    )

    private fun parseFileContent(
        lines: List<String>,
        start: Int,
        action: String,
        isMove: Boolean,
        fileHeader: String
    ): FileContent {
        var i = start
        val options = mutableMapOf<String, String>()
        val blocks = mutableMapOf<String, String>()

        // Для move добавляем destination из заголовка
        if (isMove) {
            val destination = fileHeader.substringAfter("->").trim().removeBackticks()
            blocks[BlockNames.DESTINATION] = destination
        }

        // Читаем содержимое до следующего файла или конца
        while (i < lines.size) {
            val line = lines[i].trim()

            // Конец секции файла
            if (line == "---" || line.startsWith("#### File:")) {
                break
            }

            when {
                // Блок опций
                line.startsWith("**Options:**") -> {
                    i++
                    i = parseOptionsBlock(lines, i, options)
                }

                // Блок якоря
                line.startsWith("**Anchor:**") -> {
                    i++
                    val (code, nextLine) = extractCodeBlock(lines, i)
                    blocks[BlockNames.ANCHOR] = code
                    i = nextLine
                }

                // Блок "что ищем" - для replace, delete, insert_before, insert_after
                line.startsWith("**From:**") ||
                        line.startsWith("**Remove this:**") ||
                        line.startsWith("**Before this:**") ||
                        line.startsWith("**After this:**") -> {
                    i++
                    val (code, nextLine) = extractCodeBlock(lines, i)
                    blocks[BlockNames.FIND] = code
                    i = nextLine
                }

                // Блок "на что заменяем" или "что вставляем"
                line.startsWith("**To:**") && action != "move" -> {
                    i++
                    val (code, nextLine) = extractCodeBlock(lines, i)
                    blocks[BlockNames.REPLACE_WITH] = code
                    i = nextLine
                }
                line.startsWith("**Replace:**") -> {
                    i++
                    val (code, nextLine) = extractCodeBlock(lines, i)
                    blocks[BlockNames.REPLACE_WITH] = code
                    i = nextLine
                }
                line.startsWith("**Insert:**") -> {
                    i++
                    val (code, nextLine) = extractCodeBlock(lines, i)
                    blocks[BlockNames.INSERT_CONTENT] = code
                    i = nextLine
                }

                // Для create_file - просто код без метки
                line.startsWith("```") && blocks.isEmpty() && action == "create_file" -> {
                    val (code, nextLine) = extractCodeBlock(lines, i)
                    blocks[BlockNames.INSERT_CONTENT] = code
                    i = nextLine
                }

                else -> i++
            }
        }

        return FileContent(options, blocks, i)
    }

    private fun parseOptionsBlock(
        lines: List<String>,
        start: Int,
        options: MutableMap<String, String>
    ): Int {
        var i = start

        while (i < lines.size) {
            val line = lines[i].trim()

            // Конец блока опций
            if (line.isEmpty() ||
                line.startsWith("**") ||
                line.startsWith("```") ||
                line == "---") {
                break
            }

            // Парсим опцию формата "- KEY: value"
            if (line.startsWith("-") && line.contains(":")) {
                val optionText = line.substring(1).trim()
                val colonIndex = optionText.indexOf(":")
                val key = optionText.substring(0, colonIndex).trim()
                val value = optionText.substring(colonIndex + 1).trim()
                options[key] = value
            }

            i++
        }

        return i
    }

    private fun extractCodeBlock(lines: List<String>, start: Int): Pair<String, Int> {
        var i = start

        // Пропускаем пустые строки
        while (i < lines.size && lines[i].trim().isEmpty()) {
            i++
        }

        // Должен начинаться с ```
        if (i >= lines.size || !lines[i].trim().startsWith("```")) {
            return Pair("", i)
        }

        i++ // пропускаем открывающий ```

        val code = StringBuilder()
        while (i < lines.size) {
            val line = lines[i]

            // Закрывающий ```
            if (line.trim().startsWith("```")) {
                i++ // пропускаем закрывающий ```
                break
            }

            code.append(line).append("\n")
            i++
        }

        return Pair(code.toString().trimEnd(), i)
    }

    // ==================== СОЗДАНИЕ ДЕЙСТВИЙ ====================

    private fun createPatchAction(
        action: String,
        blocks: Map<String, String>,
        optionsMap: Map<String, String>
    ): PatchAction {
        val matchOptions = buildMatchOptions(optionsMap, blocks)

        return when (action.lowercase()) {
            "replace" -> PatchAction.Replace(
                find = blocks[BlockNames.FIND]
                    ?: error("Missing FIND block for replace action"),
                replace = blocks[BlockNames.REPLACE_WITH]
                    ?: error("Missing REPLACE_WITH block for replace action"),
                matchOptions = matchOptions
            )

            "insert_before" -> PatchAction.InsertBefore(
                marker = blocks[BlockNames.FIND]
                    ?: error("Missing FIND block for insert_before action"),
                content = blocks[BlockNames.INSERT_CONTENT]
                    ?: error("Missing INSERT_CONTENT block for insert_before action"),
                matchOptions = matchOptions
            )

            "insert_after" -> PatchAction.InsertAfter(
                marker = blocks[BlockNames.FIND]
                    ?: error("Missing FIND block for insert_after action"),
                content = blocks[BlockNames.INSERT_CONTENT]
                    ?: error("Missing INSERT_CONTENT block for insert_after action"),
                matchOptions = matchOptions
            )

            "delete" -> PatchAction.Delete(
                find = blocks[BlockNames.FIND]
                    ?: error("Missing FIND block for delete action"),
                matchOptions = matchOptions
            )

            "create_file" -> PatchAction.CreateFile(
                content = blocks[BlockNames.INSERT_CONTENT]
                    ?: error("Missing INSERT_CONTENT block for create_file action")
            )

            "replace_file" -> PatchAction.ReplaceFile(
                content = blocks[BlockNames.INSERT_CONTENT]
                    ?: error("Missing INSERT_CONTENT block for replace_file action")
            )

            "delete_file" -> PatchAction.DeleteFile()

            "move" -> PatchAction.MoveFile(
                destination = blocks[BlockNames.DESTINATION]
                    ?: error("Missing DESTINATION for move action"),
                overwrite = optionsMap["OVERWRITE"]?.toBooleanOrFalse() ?: false
            )

            else -> error("Unknown action: $action")
        }
    }

    // ==================== ПОСТРОЕНИЕ MATCH OPTIONS ====================

    private fun buildMatchOptions(
        options: Map<String, String>,
        blocks: Map<String, String>
    ): MatchOptions {
        val matchMode = parseMatchMode(options["MATCH_MODE"])
        val fuzzyThreshold = options["FUZZY_THRESHOLD"]?.toDoubleOrNull() ?: 0.85
        val ignoreComments = options["IGNORE_COMMENTS"]?.toBooleanOrFalse() ?: false
        val ignoreEmptyLines = options["IGNORE_EMPTY_LINES"]?.toBooleanOrTrue() ?: true
        val caseSensitive = options["CASE_SENSITIVE"]?.toBooleanOrTrue() ?: true
        val tokenWindowSize = options["TOKEN_WINDOW_SIZE"]?.toIntOrNull() ?: 0

        val matchFunctionName = options["MATCH_FUNCTION_NAME"]?.toBooleanOrTrue() ?: true
        val matchClassName = options["MATCH_CLASS_NAME"]?.toBooleanOrTrue() ?: true
        val matchParameterTypes = options["MATCH_PARAMETER_TYPES"]?.toBooleanOrTrue() ?: true
        val matchParameterNames = options["MATCH_PARAMETER_NAMES"]?.toBooleanOrFalse() ?: false
        val matchReturnType = options["MATCH_RETURN_TYPE"]?.toBooleanOrTrue() ?: true
        val matchModifiers = options["MATCH_MODIFIERS"]?.toBooleanOrFalse() ?: false

        val anchor = blocks[BlockNames.ANCHOR]?.let { anchorText ->
            buildAnchorOptions(anchorText, options)
        }

        return MatchOptions(
            mode = matchMode,
            fuzzyThreshold = fuzzyThreshold,
            ignoreComments = ignoreComments,
            ignoreEmptyLines = ignoreEmptyLines,
            caseSensitive = caseSensitive,
            tokenWindowSize = tokenWindowSize,
            matchFunctionName = matchFunctionName,
            matchClassName = matchClassName,
            matchParameterTypes = matchParameterTypes,
            matchParameterNames = matchParameterNames,
            matchReturnType = matchReturnType,
            matchModifiers = matchModifiers,
            anchor = anchor
        )
    }

    private fun parseMatchMode(value: String?): MatchMode {
        return when (value?.lowercase()) {
            "normalized" -> MatchMode.NORMALIZED
            "fuzzy" -> MatchMode.FUZZY
            "tokenized" -> MatchMode.TOKENIZED
            "semantic" -> MatchMode.SEMANTIC
            "regex" -> MatchMode.REGEX
            "contains" -> MatchMode.CONTAINS
            "line_range" -> MatchMode.LINE_RANGE
            else -> MatchMode.NORMALIZED
        }
    }

    private fun buildAnchorOptions(
        anchorText: String,
        options: Map<String, String>
    ): AnchorOptions {
        val matchMode = parseMatchMode(options["ANCHOR_MATCH_MODE"])

        val scope = when (options["ANCHOR_SCOPE"]?.lowercase()) {
            "function" -> AnchorScope.FUNCTION
            "class" -> AnchorScope.CLASS
            "block" -> AnchorScope.BLOCK
            "file" -> AnchorScope.FILE
            else -> AnchorScope.AUTO
        }

        val searchDepth = options["ANCHOR_SEARCH_DEPTH"]?.toIntOrNull() ?: 1

        return AnchorOptions(
            anchorText = anchorText,
            matchMode = matchMode,
            scope = scope,
            searchDepth = searchDepth
        )
    }

    // ==================== ВСПОМОГАТЕЛЬНЫЕ ФУНКЦИИ ====================

    private fun String.toBooleanOrFalse(): Boolean {
        return this.lowercase() == "true" || this.lowercase() == "yes"
    }

    private fun String.toBooleanOrTrue(): Boolean {
        return this.lowercase() != "false" && this.lowercase() != "no"
    }
}