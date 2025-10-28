package com.robustpatcher.services

import com.robustpatcher.models.MatchOptions

/**
 * Парсер сигнатур функций и классов для Kotlin и Java
 */
class SignatureParser {

    data class FunctionSignature(
        val modifiers: List<String>,
        val returnType: String?,
        val name: String,
        val parameters: List<Parameter>
    )

    data class ClassSignature(
        val modifiers: List<String>,
        val name: String,
        val typeParameters: List<String>,
        val extends: String?,
        val implements: List<String>
    )

    data class Parameter(
        val type: String,
        val name: String
    )

    fun extractFunctionSignature(code: String): FunctionSignature? {
        val trimmed = code.trim()

        val kotlinPattern = Regex(
            """((?:public|private|protected|internal|open|override|suspend|inline|operator|infix)\s+)*fun\s+(\w+)\s*\((.*?)\)\s*(?::\s*([^\s{]+))?""",
            RegexOption.DOT_MATCHES_ALL
        )

        val javaPattern = Regex(
            """((?:public|private|protected|static|final|synchronized|native|abstract)\s+)*(\w+(?:<[^>]+>)?)\s+(\w+)\s*\((.*?)\)""",
            RegexOption.DOT_MATCHES_ALL
        )

        kotlinPattern.find(trimmed)?.let { match ->
            val modifiers = match.groupValues[1].split(Regex("\\s+")).filter { it.isNotBlank() }
            val name = match.groupValues[2]
            val paramsStr = match.groupValues[3]
            val returnType = match.groupValues[4].takeIf { it.isNotBlank() }
            val parameters = parseKotlinParameters(paramsStr)
            return FunctionSignature(modifiers, returnType, name, parameters)
        }

        javaPattern.find(trimmed)?.let { match ->
            val modifiers = match.groupValues[1].split(Regex("\\s+")).filter { it.isNotBlank() }
            val returnType = match.groupValues[2]
            val name = match.groupValues[3]
            val paramsStr = match.groupValues[4]
            val parameters = parseJavaParameters(paramsStr)
            return FunctionSignature(modifiers, returnType, name, parameters)
        }

        return null
    }

    fun extractClassSignature(code: String): ClassSignature? {
        val trimmed = code.trim()

        val kotlinPattern = Regex(
            """((?:public|private|internal|open|abstract|sealed|data|inner)\s+)*class\s+(\w+)(?:<([^>]+)>)?\s*(?::\s*([^{]+))?""",
            RegexOption.DOT_MATCHES_ALL
        )

        val javaPattern = Regex(
            """((?:public|private|protected|static|final|abstract)\s+)*class\s+(\w+)(?:<([^>]+)>)?(?:\s+extends\s+(\w+))?(?:\s+implements\s+([^{]+))?""",
            RegexOption.DOT_MATCHES_ALL
        )

        kotlinPattern.find(trimmed)?.let { match ->
            val modifiers = match.groupValues[1].split(Regex("\\s+")).filter { it.isNotBlank() }
            val name = match.groupValues[2]
            val typeParams = match.groupValues[3].split(",").map { it.trim() }.filter { it.isNotBlank() }
            val inheritance = match.groupValues[4].trim()
            val parts = inheritance.split(",").map { it.trim() }
            val extends = parts.firstOrNull()
            val implements = parts.drop(1)
            return ClassSignature(modifiers, name, typeParams, extends, implements)
        }

        javaPattern.find(trimmed)?.let { match ->
            val modifiers = match.groupValues[1].split(Regex("\\s+")).filter { it.isNotBlank() }
            val name = match.groupValues[2]
            val typeParams = match.groupValues[3].split(",").map { it.trim() }.filter { it.isNotBlank() }
            val extends = match.groupValues[4].takeIf { it.isNotBlank() }
            val implements = match.groupValues[5].split(",").map { it.trim() }.filter { it.isNotBlank() }
            return ClassSignature(modifiers, name, typeParams, extends, implements)
        }

        return null
    }

    private fun parseKotlinParameters(paramsStr: String): List<Parameter> {
        if (paramsStr.isBlank()) return emptyList()
        return paramsStr.split(",").map { it.trim() }.filter { it.isNotBlank() }.mapNotNull { param ->
            val parts = param.split(":")
            if (parts.size >= 2) {
                val name = parts[0].trim()
                val type = parts[1].split("=")[0].trim()
                Parameter(type, name)
            } else null
        }
    }

    private fun parseJavaParameters(paramsStr: String): List<Parameter> {
        if (paramsStr.isBlank()) return emptyList()
        return paramsStr.split(",").map { it.trim() }.filter { it.isNotBlank() }.mapNotNull { param ->
            val parts = param.replace("...", "").split(Regex("\\s+"))
            if (parts.size >= 2) {
                val type = parts[0].trim()
                val name = parts[1].trim()
                Parameter(type, name)
            } else null
        }
    }

    fun matchFunctionSignatures(sig1: FunctionSignature, sig2: FunctionSignature, options: MatchOptions): Boolean {
        if (options.matchFunctionName && sig1.name != sig2.name) return false
        if (options.matchReturnType) {
            val type1 = normalizeType(sig1.returnType ?: "Unit")
            val type2 = normalizeType(sig2.returnType ?: "Unit")
            if (type1 != type2) return false
        }
        if (options.matchModifiers) {
            val mods1 = sig1.modifiers.toSet()
            val mods2 = sig2.modifiers.toSet()
            if (mods1 != mods2) return false
        }
        if (sig1.parameters.size != sig2.parameters.size) return false
        for (i in sig1.parameters.indices) {
            val p1 = sig1.parameters[i]
            val p2 = sig2.parameters[i]
            if (options.matchParameterTypes) {
                val type1 = normalizeType(p1.type)
                val type2 = normalizeType(p2.type)
                if (type1 != type2) return false
            }
            if (options.matchParameterNames && p1.name != p2.name) return false
        }
        return true
    }

    fun matchClassSignatures(sig1: ClassSignature, sig2: ClassSignature, options: MatchOptions): Boolean {
        if (options.matchClassName && sig1.name != sig2.name) return false
        if (options.matchModifiers) {
            val mods1 = sig1.modifiers.toSet()
            val mods2 = sig2.modifiers.toSet()
            if (mods1 != mods2) return false
        }
        return true
    }

    private fun normalizeType(type: String): String {
        return type.replace(Regex("\\s+"), "").replace("?", "").trim()
    }

    fun findFunctionBySignature(content: String, targetSignature: FunctionSignature, options: MatchOptions): Pair<Int, Int>? {
        val lines = content.lines()
        var currentOffset = 0

        for (i in lines.indices) {
            val line = lines[i]

            if (!line.contains("fun ") && !line.contains("(")) {
                currentOffset += line.length + 1  // +1 для \n
                continue
            }

            val contextStart = maxOf(0, i - 1)
            val contextEnd = minOf(lines.size, i + 5)
            val context = lines.subList(contextStart, contextEnd).joinToString("\n")

            extractFunctionSignature(context)?.let { foundSignature ->
                if (matchFunctionSignatures(foundSignature, targetSignature, options)) {
                    // Используем currentOffset вместо indexOf
                    val startIndex = currentOffset

                    var braceIndex = content.indexOf('{', startIndex)
                    if (braceIndex < 0) {
                        val eolIndex = content.indexOf('\n', startIndex)
                        return if (eolIndex > 0) Pair(startIndex, eolIndex - startIndex)
                        else Pair(startIndex, content.length - startIndex)
                    }
                    var braceCount = 1
                    var endIndex = braceIndex + 1
                    while (endIndex < content.length && braceCount > 0) {
                        when (content[endIndex]) {
                            '{' -> braceCount++
                            '}' -> braceCount--
                        }
                        endIndex++
                    }
                    return Pair(startIndex, endIndex - startIndex)
                }
            }

            currentOffset += line.length + 1  // +1 для \n
        }
        return null
    }

    fun findClassBySignature(content: String, targetSignature: ClassSignature, options: MatchOptions): Pair<Int, Int>? {
        val lines = content.lines()
        var currentOffset = 0

        for (i in lines.indices) {
            val line = lines[i]

            if (!line.contains("class ")) {
                currentOffset += line.length + 1  // +1 для \n
                continue
            }

            val contextStart = maxOf(0, i - 1)
            val contextEnd = minOf(lines.size, i + 3)
            val context = lines.subList(contextStart, contextEnd).joinToString("\n")

            extractClassSignature(context)?.let { foundSignature ->
                if (matchClassSignatures(foundSignature, targetSignature, options)) {
                    // Используем currentOffset вместо indexOf
                    val startIndex = currentOffset

                    var braceIndex = content.indexOf('{', startIndex)
                    if (braceIndex < 0) {
                        currentOffset += line.length + 1
                        return null
                    }
                    var braceCount = 1
                    var endIndex = braceIndex + 1
                    while (endIndex < content.length && braceCount > 0) {
                        when (content[endIndex]) {
                            '{' -> braceCount++
                            '}' -> braceCount--
                        }
                        endIndex++
                    }
                    return Pair(startIndex, endIndex - startIndex)
                }
            }

            currentOffset += line.length + 1  // +1 для \n
        }
        return null
    }
}