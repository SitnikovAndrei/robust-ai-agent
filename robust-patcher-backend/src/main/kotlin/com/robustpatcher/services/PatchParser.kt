package com.robustpatcher.services

import com.robustpatcher.models.*

class PatchParser {
    
    fun parse(content: String): Pair<PatchMetadata, List<FilePatch>> {
        val lines = content.lines()
        var i = 0
        
        // Ищем начало патча
        while (i < lines.size && lines[i].trim() != "=== PATCH START ===") {
            i++
        }
        if (i >= lines.size) {
            throw IllegalArgumentException("PATCH START marker not found")
        }
        i++
        
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
                
                val patchAction = createPatchAction(action, blocks)
                patches.add(FilePatch(file, description, patchAction))
            } else {
                i++
            }
        }
        
        return Pair(patchMetadata, patches)
    }
    
    private fun createPatchAction(action: String, blocks: Map<String, String>): PatchAction {
        return when (action.lowercase()) {
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
            "replace_file" -> PatchAction.ReplaceFile(
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
    }
}