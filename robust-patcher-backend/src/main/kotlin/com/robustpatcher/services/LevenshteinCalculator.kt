package com.robustpatcher.services

import kotlin.math.abs
import kotlin.math.min

/**
 * Оптимизированный калькулятор расстояния Левенштейна
 * с early exit и минимизацией памяти
 */
class LevenshteinCalculator {
    
    /**
     * Вычисляет расстояние Левенштейна с порогом для early exit
     * 
     * @param s1 Первая строка
     * @param s2 Вторая строка
     * @param maxDistance Максимальное допустимое расстояние
     * @return Расстояние или maxDistance + 1 если превышен порог
     */
    fun calculateWithThreshold(
        s1: String,
        s2: String,
        maxDistance: Int
    ): Int {
        val len1 = s1.length
        val len2 = s2.length
        
        // Базовые случаи
        if (len1 == 0) return len2
        if (len2 == 0) return len1
        
        // Early exit: если разница длин больше порога
        if (abs(len1 - len2) > maxDistance) {
            return maxDistance + 1
        }
        
        // Оптимизация: работаем с более короткой строкой
        val (shorter, longer) = if (len1 <= len2) s1 to s2 else s2 to s1
        val shortLen = shorter.length
        val longLen = longer.length
        
        // Используем только две строки вместо матрицы
        var prevRow = IntArray(shortLen + 1) { it }
        var currRow = IntArray(shortLen + 1)
        
        for (i in 1..longLen) {
            currRow[0] = i
            var minInRow = i
            
            for (j in 1..shortLen) {
                val cost = if (longer[i - 1] == shorter[j - 1]) 0 else 1
                
                currRow[j] = min(
                    min(prevRow[j] + 1, currRow[j - 1] + 1),
                    prevRow[j - 1] + cost
                )
                
                if (currRow[j] < minInRow) {
                    minInRow = currRow[j]
                }
            }
            
            // Early exit: если минимум в строке больше порога
            if (minInRow > maxDistance) {
                return maxDistance + 1
            }
            
            // Swap arrays
            val temp = prevRow
            prevRow = currRow
            currRow = temp
        }
        
        return prevRow[shortLen]
    }
    
    /**
     * Вычисляет коэффициент схожести (0.0 - 1.0)
     * 
     * @param s1 Первая строка
     * @param s2 Вторая строка
     * @param threshold Минимальный порог схожести для расчета
     * @return Коэффициент схожести
     */
    fun calculateSimilarity(
        s1: String,
        s2: String,
        threshold: Double = 0.0
    ): Double {
        if (s1 == s2) return 1.0
        
        val maxLen = max(s1.length, s2.length)
        if (maxLen == 0) return 1.0
        
        val maxDistance = if (threshold > 0) {
            ((1.0 - threshold) * maxLen).toInt()
        } else {
            Int.MAX_VALUE
        }
        
        val distance = calculateWithThreshold(s1, s2, maxDistance)
        
        if (distance > maxDistance) return 0.0
        
        return 1.0 - (distance.toDouble() / maxLen)
    }
    
    /**
     * Вычисляет расстояние для последовательности токенов
     */
    fun <T> calculateTokenDistance(
        tokens1: List<T>,
        tokens2: List<T>,
        maxDistance: Int = Int.MAX_VALUE
    ): Int {
        val len1 = tokens1.size
        val len2 = tokens2.size
        
        if (len1 == 0) return len2
        if (len2 == 0) return len1
        
        if (abs(len1 - len2) > maxDistance) {
            return maxDistance + 1
        }
        
        val (shorter, longer) = if (len1 <= len2) tokens1 to tokens2 else tokens2 to tokens1
        val shortLen = shorter.size
        val longLen = longer.size
        
        var prevRow = IntArray(shortLen + 1) { it }
        var currRow = IntArray(shortLen + 1)
        
        for (i in 1..longLen) {
            currRow[0] = i
            var minInRow = i
            
            for (j in 1..shortLen) {
                val cost = if (longer[i - 1] == shorter[j - 1]) 0 else 1
                
                currRow[j] = min(
                    min(prevRow[j] + 1, currRow[j - 1] + 1),
                    prevRow[j - 1] + cost
                )
                
                minInRow = min(minInRow, currRow[j])
            }
            
            if (minInRow > maxDistance) {
                return maxDistance + 1
            }
            
            val temp = prevRow
            prevRow = currRow
            currRow = temp
        }
        
        return prevRow[shortLen]
    }
    
    private fun max(a: Int, b: Int): Int = if (a > b) a else b
}