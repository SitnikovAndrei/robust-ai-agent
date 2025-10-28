package com.robustpatcher.services.matchers

import com.robustpatcher.models.MatchOptions
import com.robustpatcher.models.MatchResult
import com.robustpatcher.services.TextNormalizer

/**
 * Стратегия точного поиска после нормализации текста
 */
class NormalizedMatcher(
    private val normalizer: TextNormalizer
) : MatcherStrategy {
    
    override fun findMatch(
        content: String,
        pattern: String,
        options: MatchOptions,
        lineEnding: String
    ): MatchResult? {
        val normalizedPattern = normalizer.normalizeForFuzzy(
            pattern,
            options.ignoreComments,
            options.ignoreEmptyLines
        )
        
        val patternLines = pattern.lines()
        val contentLines = content.lines()
        
        // Поиск окна строк
        for (startLine in contentLines.indices) {
            val endLine = minOf(startLine + patternLines.size, contentLines.size)
            val windowLines = contentLines.subList(startLine, endLine)
            val window = windowLines.joinToString(lineEnding)
            
            // Нормализация и сравнение
            val normalizedWindow = normalizer.normalizeForFuzzy(
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
}