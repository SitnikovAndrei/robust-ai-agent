package com.robustpatcher.services.matchers

import com.robustpatcher.models.MatchOptions
import com.robustpatcher.models.MatchResult
import com.robustpatcher.services.LevenshteinCalculator
import com.robustpatcher.services.TextNormalizer

/**
 * Стратегия приближенного поиска с использованием расстояния Левенштейна
 */
class FuzzyMatcher(
    private val normalizer: TextNormalizer,
    private val levenshtein: LevenshteinCalculator
) : MatcherStrategy {
    
    override fun findMatch(
        content: String,
        pattern: String,
        options: MatchOptions,
        lineEnding: String
    ): MatchResult? {
        // Если threshold >= 1.0, используем точное совпадение
        if (options.fuzzyThreshold >= 1.0) {
            return NormalizedMatcher(normalizer).findMatch(content, pattern, options, lineEnding)
        }
        
        return findApproximateMatch(content, pattern, options, lineEnding)
    }
    
    private fun findApproximateMatch(
        content: String,
        pattern: String,
        options: MatchOptions,
        lineEnding: String
    ): MatchResult? {
        val contentLines = content.lines()
        val patternLines = pattern.lines()
        val patternLineCount = patternLines.size
        
        var bestMatch: MatchResult? = null
        var bestSimilarity = 0.0
        
        // Скользящее окно точно по количеству строк pattern
        for (startLine in 0..contentLines.size - patternLineCount) {
            val endLine = startLine + patternLineCount
            val windowLines = contentLines.subList(startLine, endLine)
            val window = windowLines.joinToString(lineEnding)
            
            val similarity = calculateSimilarity(pattern, window, options)
            
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
    
    private fun calculateSimilarity(
        text1: String,
        text2: String,
        options: MatchOptions
    ): Double {
        val norm1 = normalizer.normalizeForFuzzy(text1, options.ignoreComments, options.ignoreEmptyLines)
        val norm2 = normalizer.normalizeForFuzzy(text2, options.ignoreComments, options.ignoreEmptyLines)
        
        val s1 = if (options.caseSensitive) norm1 else norm1.lowercase()
        val s2 = if (options.caseSensitive) norm2 else norm2.lowercase()
        
        return levenshtein.calculateSimilarity(s1, s2, options.fuzzyThreshold)
    }
}