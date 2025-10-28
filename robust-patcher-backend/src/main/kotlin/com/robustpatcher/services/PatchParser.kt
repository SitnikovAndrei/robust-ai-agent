package com.robustpatcher.services

import com.robustpatcher.models.*

class PatchParser {

    fun parse(content: String): Pair<PatchMetadata, List<FilePatch>> {
        val lines = content.lines()
        var i = 0

        while (i < lines.size && lines[i].trim() != "=== PATCH START ===") {
            i++
        }
        if (i >= lines.size) {
            throw IllegalArgumentException("PATCH START marker not found")
        }
        i++

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

        val patches = mutableListOf<FilePatch>()

        while (i < lines.size) {
            val line = lines[i].trim()
            if (line == "=== PATCH END ===") break
            if (line.startsWith("--- FILE:")) {
                val file = line.substringAfter("--- FILE:").substringBefore("---").trim()
                i++

                var action = ""
                var description = ""
                val params = mutableMapOf<String, String>()

                while (i < lines.size) {
                    val paramLine = lines[i].trim()
                    if (paramLine.startsWith("<<<")) break
                    when {
                        paramLine.startsWith("ACTION:") -> action = paramLine.substringAfter(":").trim()
                        paramLine.startsWith("DESCRIPTION:") -> description = paramLine.substringAfter(":").trim()
                        paramLine.startsWith("MATCH_MODE:") -> params["MATCH_MODE"] =
                            paramLine.substringAfter(":").trim()

                        paramLine.startsWith("FUZZY_THRESHOLD:") -> params["FUZZY_THRESHOLD"] =
                            paramLine.substringAfter(":").trim()

                        paramLine.startsWith("IGNORE_COMMENTS:") -> params["IGNORE_COMMENTS"] =
                            paramLine.substringAfter(":").trim()

                        paramLine.startsWith("IGNORE_EMPTY_LINES:") -> params["IGNORE_EMPTY_LINES"] =
                            paramLine.substringAfter(":").trim()

                        paramLine.startsWith("CASE_SENSITIVE:") -> params["CASE_SENSITIVE"] =
                            paramLine.substringAfter(":").trim()

                        paramLine.startsWith("MATCH_FUNCTION_NAME:") -> params["MATCH_FUNCTION_NAME"] =
                            paramLine.substringAfter(":").trim()

                        paramLine.startsWith("MATCH_CLASS_NAME:") -> params["MATCH_CLASS_NAME"] =
                            paramLine.substringAfter(":").trim()

                        paramLine.startsWith("MATCH_PARAMETER_TYPES:") -> params["MATCH_PARAMETER_TYPES"] =
                            paramLine.substringAfter(":").trim()

                        paramLine.startsWith("MATCH_PARAMETER_NAMES:") -> params["MATCH_PARAMETER_NAMES"] =
                            paramLine.substringAfter(":").trim()

                        paramLine.startsWith("MATCH_RETURN_TYPE:") -> params["MATCH_RETURN_TYPE"] =
                            paramLine.substringAfter(":").trim()

                        paramLine.startsWith("MATCH_MODIFIERS:") -> params["MATCH_MODIFIERS"] =
                            paramLine.substringAfter(":").trim()

                        paramLine.startsWith("ANCHOR_MATCH_MODE:") -> params["ANCHOR_MATCH_MODE"] =
                            paramLine.substringAfter(":").trim()

                        paramLine.startsWith("ANCHOR_SCOPE:") -> params["ANCHOR_SCOPE"] =
                            paramLine.substringAfter(":").trim()

                        paramLine.startsWith("ANCHOR_SEARCH_DEPTH:") -> params["ANCHOR_SEARCH_DEPTH"] =
                            paramLine.substringAfter(":").trim()
                    }
                    i++
                }
                val blocks = mutableMapOf<String, String>()
                while (i < lines.size) {
                    val blockLine = lines[i].trim()
                    if (blockLine.startsWith("---") || blockLine == "=== PATCH END ===") break

                    // Поддержка нового формата @@ BLOCK_TYPE
                    if (blockLine.startsWith("@@")) {
                        val blockType = blockLine.substring(2).trim()
                        i++
                        val blockContent = StringBuilder()
                        while (i < lines.size) {
                            val contentLine = lines[i]
                            if (contentLine.trim() == "@@") break
                            blockContent.append(contentLine).append("\n")
                            i++
                        }
                        blocks[blockType] = blockContent.toString().trimEnd()
                        i++
                    }
                    // Поддержка старого формата <<< >>> для обратной совместимости
                    else if (blockLine.startsWith("<<<")) {
                        val blockType = blockLine.substring(3).trim()
                        i++
                        val blockContent = StringBuilder()
                        while (i < lines.size) {
                            val contentLine = lines[i]
                            if (contentLine.trim().endsWith("$blockType >>>")) break
                            blockContent.append(contentLine).append("\n")
                            i++
                        }
                        blocks[blockType] = blockContent.toString().trimEnd()
                        i++
                    } else {
                        i++
                    }
                }

                val patchAction = createPatchAction(action, blocks, params)
                patches.add(FilePatch(file, description, patchAction))
            } else {
                i++
            }
        }

        return Pair(patchMetadata, patches)
    }

    private fun createPatchAction(
        action: String,
        blocks: Map<String, String>,
        params: Map<String, String>
    ): PatchAction {
        val matchOptions = parseMatchOptions(params, blocks)
        return when (action.lowercase()) {
            "replace" -> PatchAction.Replace(
                find = blocks["TARGET"] ?: throw IllegalArgumentException("Missing TARGET block"),
                replace = blocks["CONTENT"] ?: blocks["REPLACE"]
                ?: throw IllegalArgumentException("Missing CONTENT block"),
                matchOptions = matchOptions
            )

            "insert_before" -> PatchAction.InsertBefore(
                marker = blocks["TARGET"] ?: throw IllegalArgumentException("Missing TARGET block"),
                content = blocks["CONTENT"] ?: throw IllegalArgumentException("Missing CONTENT block"),
                matchOptions = matchOptions
            )

            "insert_after" -> PatchAction.InsertAfter(
                marker = blocks["TARGET"] ?: throw IllegalArgumentException("Missing TARGET block"),
                content = blocks["CONTENT"] ?: throw IllegalArgumentException("Missing CONTENT block"),
                matchOptions = matchOptions
            )

            "delete" -> PatchAction.Delete(
                find = blocks["TARGET"] ?: throw IllegalArgumentException("Missing TARGET block"),
                matchOptions = matchOptions
            )

            "create_file" -> PatchAction.CreateFile(
                content = blocks["CONTENT"] ?: throw IllegalArgumentException("Missing CONTENT block")
            )

            "replace_file" -> PatchAction.ReplaceFile(
                content = blocks["CONTENT"] ?: throw IllegalArgumentException("Missing CONTENT block")
            )

            "delete_file" -> PatchAction.DeleteFile()
            "move_file" -> {
                val overwriteStr = params["OVERWRITE"]?.trim()?.lowercase()
                PatchAction.MoveFile(
                    destination = blocks["TO"] ?: throw IllegalArgumentException("Missing TO block"),
                    overwrite = overwriteStr == "true" || overwriteStr == "yes"
                )
            }

            else -> throw IllegalArgumentException("Unknown action: $action")
        }
    }

    private fun parseMatchOptions(params: Map<String, String>, blocks: Map<String, String>): MatchOptions {
        val matchMode = when (params["MATCH_MODE"]?.lowercase()) {
            "normalized" -> MatchMode.NORMALIZED
            "fuzzy" -> MatchMode.FUZZY
            "semantic" -> MatchMode.SEMANTIC
            "regex" -> MatchMode.REGEX
            "contains" -> MatchMode.CONTAINS
            "line_range" -> MatchMode.LINE_RANGE
            else -> MatchMode.NORMALIZED
        }
        val fuzzyThreshold = params["FUZZY_THRESHOLD"]?.toDoubleOrNull() ?: 0.85
        val ignoreComments = params["IGNORE_COMMENTS"]?.lowercase() == "true"
        val ignoreEmptyLines = params["IGNORE_EMPTY_LINES"]?.lowercase() != "false"
        val caseSensitive = params["CASE_SENSITIVE"]?.lowercase() != "false"

        val matchFunctionName = params["MATCH_FUNCTION_NAME"]?.lowercase() != "false"
        val matchClassName = params["MATCH_CLASS_NAME"]?.lowercase() != "false"
        val matchParameterTypes = params["MATCH_PARAMETER_TYPES"]?.lowercase() != "false"
        val matchParameterNames = params["MATCH_PARAMETER_NAMES"]?.lowercase() == "true"
        val matchReturnType = params["MATCH_RETURN_TYPE"]?.lowercase() != "false"
        val matchModifiers = params["MATCH_MODIFIERS"]?.lowercase() == "true"
        val anchor = if (blocks.containsKey("ANCHOR")) {
            val anchorMatchMode = when (params["ANCHOR_MATCH_MODE"]?.lowercase()) {
                "normalized" -> MatchMode.NORMALIZED
                "fuzzy" -> MatchMode.FUZZY
                "semantic" -> MatchMode.SEMANTIC
                else -> MatchMode.NORMALIZED
            }
            val anchorScope = when (params["ANCHOR_SCOPE"]?.lowercase()) {
                "function" -> AnchorScope.FUNCTION
                "class" -> AnchorScope.CLASS
                "block" -> AnchorScope.BLOCK
                "file" -> AnchorScope.FILE
                else -> AnchorScope.AUTO
            }
            val searchDepth = params["ANCHOR_SEARCH_DEPTH"]?.toIntOrNull() ?: 1
            AnchorOptions(
                anchorText = blocks["ANCHOR"]!!,
                matchMode = anchorMatchMode,
                scope = anchorScope,
                searchDepth = searchDepth
            )
        } else null

        return MatchOptions(
            mode = matchMode,
            fuzzyThreshold = fuzzyThreshold,
            ignoreComments = ignoreComments,
            ignoreEmptyLines = ignoreEmptyLines,
            caseSensitive = caseSensitive,
            matchFunctionName = matchFunctionName,
            matchClassName = matchClassName,
            matchParameterTypes = matchParameterTypes,
            matchParameterNames = matchParameterNames,
            matchReturnType = matchReturnType,
            matchModifiers = matchModifiers,
            anchor = anchor
        )
    }
}