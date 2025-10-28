package com.robustpatcher.services.matchers

import com.robustpatcher.models.MatchOptions
import com.robustpatcher.models.MatchResult

class TokenMatcher {

    data class TokenWithPosition(
        val token: String,
        val startPos: Int,
        val endPos: Int
    )

    /**
     * Извлекает токены (непрерывные последовательности не-пробельных символов)
     */
    private fun extractTokens(text: String, options: MatchOptions): List<String> {
        var working = text

        if (options.ignoreComments) {
            working = removeComments(working)
        }

        // Разбиваем на токены по пробельным символам
        val tokens = working.split(Regex("\\s+"))
            .filter { it.isNotBlank() }

        return if (options.caseSensitive) {
            tokens
        } else {
            tokens.map { it.lowercase() }
        }
    }

    /**
     * Извлекает токены с позициями в оригинальном тексте
     */
    private fun extractTokensWithPositions(
        text: String,
        options: MatchOptions
    ): List<TokenWithPosition> {
        var working = text

        if (options.ignoreComments) {
            working = removeComments(working)
        }

        val tokens = mutableListOf<TokenWithPosition>()
        val regex = Regex("\\S+") // Непрерывные не-пробельные символы

        regex.findAll(working).forEach { match ->
            val token = if (options.caseSensitive) {
                match.value
            } else {
                match.value.lowercase()
            }

            tokens.add(
                TokenWithPosition(
                    token = token,
                    startPos = match.range.first,
                    endPos = match.range.last + 1
                )
            )
        }

        return tokens
    }

    /**
     * Удаляет комментарии из кода
     */
    private fun removeComments(text: String): String {
        var result = text

        // Удаляем блочные комментарии /* ... */
        result = result.replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")

        // Удаляем строчные комментарии // и #
        result = result.lines().joinToString("\n") { line ->
            val commentIndex = line.indexOfAny(listOf("//", "#"))
            if (commentIndex >= 0) line.take(commentIndex) else line
        }

        return result
    }

    /**
     * Находит совпадение по токенам
     * Возвращает позицию и длину в ОРИГИНАЛЬНОМ тексте
     */
    fun findTokenizedMatch(
        content: String,
        pattern: String,
        options: MatchOptions
    ): MatchResult? {
        val patternTokens = extractTokens(pattern, options)
        if (patternTokens.isEmpty()) return null

        val windowSize = if (options.tokenWindowSize > 0) {
            options.tokenWindowSize
        } else {
            patternTokens.size
        }

        val contentTokens = extractTokensWithPositions(content, options)

        if (contentTokens.size < windowSize) {
            return null
        }

        for (i in 0..contentTokens.size - windowSize) {
            val windowTokens = contentTokens.subList(i, i + windowSize)

            if (tokensExactMatch(patternTokens, windowTokens.map { it.token })) {
                val firstToken = windowTokens.first()
                val lastToken = windowTokens.last()
                val matchedText = content.substring(firstToken.startPos, lastToken.endPos)

                return MatchResult(firstToken.startPos, matchedText.length, matchedText)
            }
        }

        return null
    }

    private fun tokensExactMatch(tokens1: List<String>, tokens2: List<String>): Boolean {
        if (tokens1.size != tokens2.size) return false
        return tokens1.indices.all { i -> tokens1[i] == tokens2[i] }
    }


    /**
     * Fuzzy совпадение по токенам (с порогом similarity)
     */
    fun findTokenizedMatchFuzzy(
        content: String,
        pattern: String,
        options: MatchOptions,
        threshold: Double = 0.8
    ): MatchResult? {
        val patternTokens = extractTokens(pattern, options)
        if (patternTokens.isEmpty()) return null

        val baseWindowSize = if (options.tokenWindowSize > 0) {
            options.tokenWindowSize
        } else {
            patternTokens.size
        }

        println("Base window size: $baseWindowSize")

        val contentTokens = extractTokensWithPositions(content, options)
        println("Content tokens: ${contentTokens.map { it.token }} (count=${contentTokens.size})")

        var bestMatch: MatchResult? = null
        var bestSimilarity = 0.0

        val minWindow = maxOf(1, (baseWindowSize * 0.8).toInt())
        val maxWindow = (baseWindowSize * 1.2).toInt()

        println("Window range: $minWindow - $maxWindow")

        for (i in contentTokens.indices) {
            for (currentWindowSize in minWindow..minOf(maxWindow, contentTokens.size - i)) {
                val windowTokens = contentTokens.subList(i, i + currentWindowSize)

                val similarity = calculateTokenSequenceSimilarity(
                    patternTokens,
                    windowTokens.map { it.token },
                    threshold
                )

                println("Window [$i, size=$currentWindowSize]: similarity=$similarity")

                if (similarity >= threshold && similarity > bestSimilarity) {
                    println("  ✅ New best match! similarity=$similarity")
                    val firstToken = windowTokens.first()
                    val lastToken = windowTokens.last()
                    val matchedText = content.substring(firstToken.startPos, lastToken.endPos)

                    bestMatch = MatchResult(firstToken.startPos, matchedText.length, matchedText)
                    bestSimilarity = similarity
                }
            }
        }

        if (bestMatch != null) {
            println("✅ FINAL MATCH: similarity=$bestSimilarity")
        } else {
            println("❌ NO MATCH FOUND")
        }

        return bestMatch
    }


    private fun calculateTokenSequenceSimilarity(
        tokens1: List<String>,
        tokens2: List<String>,
        threshold: Double
    ): Double {
        println("    calculateTokenSequenceSimilarity:")
        println("      tokens1: $tokens1")
        println("      tokens2: $tokens2")
        println("      threshold: $threshold")

        val len1 = tokens1.size
        val len2 = tokens2.size

        if (len1 == 0 && len2 == 0) return 1.0
        if (len1 == 0 || len2 == 0) return 0.0

        if (threshold >= 1.0) {
            val result = if (tokens1 == tokens2) 1.0 else 0.0
            println("      threshold>=1.0, result=$result")
            return result
        }

        val maxLen = maxOf(len1, len2)
        val maxAllowedDistance = ((1.0 - threshold) * maxLen).toInt()

        println("      maxLen=$maxLen, maxAllowedDistance=$maxAllowedDistance")

        if (maxAllowedDistance < 1) {
            val result = if (tokens1 == tokens2) 1.0 else 0.0
            println("      maxAllowedDistance<1, result=$result")
            return result
        }

        val distance = levenshteinDistanceTokens(tokens1, tokens2, maxAllowedDistance)

        println("      distance=$distance")

        if (distance > maxAllowedDistance) {
            println("      distance>maxAllowed, returning 0.0")
            return 0.0
        }

        val similarity = 1.0 - (distance.toDouble() / maxLen)
        println("      final similarity=$similarity")
        return similarity
    }

    private fun levenshteinDistanceTokens(
        tokens1: List<String>,
        tokens2: List<String>,
        maxDistance: Int = Int.MAX_VALUE
    ): Int {
        println("=== Levenshtein Debug ===")
        println("tokens1: $tokens1")
        println("tokens2: $tokens2")
        println("maxDistance: $maxDistance")

        val len1 = tokens1.size
        val len2 = tokens2.size

        println("len1: $len1, len2: $len2")

        if (len1 == 0) {
            println("len1 == 0, returning $len2")
            return len2
        }
        if (len2 == 0) {
            println("len2 == 0, returning $len1")
            return len1
        }

        if (Math.abs(len1 - len2) > maxDistance) {
            println("Diff too large: ${Math.abs(len1 - len2)} > $maxDistance")
            return maxDistance + 1
        }

        val (shorter, longer) = if (len1 <= len2) tokens1 to tokens2 else tokens2 to tokens1
        val shortLen = shorter.size
        val longLen = longer.size

        println("shorter: $shorter (len=$shortLen)")
        println("longer: $longer (len=$longLen)")

        var prevRow = IntArray(shortLen + 1) { it }
        var currRow = IntArray(shortLen + 1)

        println("Initial prevRow: ${prevRow.contentToString()}")

        for (i in 1..longLen) {
            currRow[0] = i
            var minInRow = i

            for (j in 1..shortLen) {
                val cost = if (longer[i - 1] == shorter[j - 1]) 0 else 1
                currRow[j] = minOf(
                    prevRow[j] + 1,
                    currRow[j - 1] + 1,
                    prevRow[j - 1] + cost
                )

                if (currRow[j] < minInRow) {
                    minInRow = currRow[j]
                }
            }

            println("i=$i: currRow=${currRow.contentToString()}, minInRow=$minInRow")

            if (minInRow > maxDistance) {
                println("Early exit: minInRow=$minInRow > maxDistance=$maxDistance")
                return maxDistance + 1
            }

            val temp = prevRow
            prevRow = currRow
            currRow = temp

            println("After swap: prevRow=${prevRow.contentToString()}")
        }

        val result = prevRow[shortLen]
        println("Final result: $result")
        return result
    }
}