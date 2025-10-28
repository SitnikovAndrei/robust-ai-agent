package com.robustpatcher.services

import com.robustpatcher.models.MatchOptions

/**
 * Сервис для нормализации и обработки текста
 */
class TextNormalizer {
    
    companion object {
        private val BLOCK_COMMENT_REGEX = Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL)
        private val LINE_COMMENT_CHARS = listOf("//", "#")
        private val OPERATOR_REGEX = Regex("([=+\\-*/:<>!])")
        private val WHITESPACE_REGEX = Regex("\\s+")
    }
    
    /**
     * Определяет тип окончания строк в тексте
     */
    fun detectLineEnding(content: String): String {
        return if (content.contains("\r\n")) "\r\n" else "\n"
    }
    
    /**
     * Нормализует окончания строк к \n
     */
    fun normalizeLineEndings(text: String): String {
        return text.replace("\r\n", "\n").replace("\r", "\n")
    }
    
    /**
     * Конвертирует окончания строк к указанному формату
     */
    fun convertLineEndings(text: String, lineEnding: String): String {
        return if (lineEnding == "\r\n") {
            text.replace("\n", "\r\n")
        } else {
            text
        }
    }
    
    /**
     * Подготавливает текст для поиска с учетом окончаний строк
     */
    fun prepareForSearch(text: String, fileLineEnding: String): String {
        val normalized = normalizeLineEndings(text)
        return convertLineEndings(normalized, fileLineEnding)
    }
    
    /**
     * Удаляет комментарии из кода
     */
    fun removeComments(text: String): String {
        var result = text
        
        // Удаляем блочные комментарии
        result = BLOCK_COMMENT_REGEX.replace(result, "")
        
        // Удаляем строчные комментарии
        result = result.lines().joinToString("\n") { line ->
            val commentIndex = line.indexOfAny(LINE_COMMENT_CHARS)
            if (commentIndex >= 0) line.take(commentIndex) else line
        }
        
        return result
    }
    
    /**
     * Нормализует текст для fuzzy matching
     */
    fun normalizeForFuzzy(
        text: String,
        ignoreComments: Boolean,
        ignoreEmptyLines: Boolean
    ): String {
        var result = text
        
        if (ignoreComments) {
            result = removeComments(result)
        }
        
        val lines = result.lines().map { line ->
            var normalized = line.replace("\t", "    ")
            normalized = OPERATOR_REGEX.replace(normalized, " $1 ")
            normalized = WHITESPACE_REGEX.replace(normalized, " ")
            normalized.trim()
        }
        
        val filteredLines = if (ignoreEmptyLines) {
            lines.filter { it.isNotBlank() }
        } else {
            lines
        }
        
        return filteredLines.joinToString("\n")
    }
    
    /**
     * Извлекает отступ из строки
     */
    fun extractIndent(line: String): String {
        return line.takeWhile { it.isWhitespace() }
    }
    
    /**
     * Применяет отступ к каждой строке текста
     */
    fun applyIndent(text: String, indent: String, lineEnding: String): String {
        return text.lines().mapIndexed { index, line ->
            if (line.isBlank()) {
                line
            } else {
                indent + line.trimStart()
            }
        }.joinToString(lineEnding)
    }
}