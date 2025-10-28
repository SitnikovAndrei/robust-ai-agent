package com.robustpatcher.services.matchers

import com.robustpatcher.models.MatchOptions
import com.robustpatcher.models.MatchResult

/**
 * Стратегия поиска совпадений в тексте
 */
interface MatcherStrategy {
    
    /**
     * Ищет совпадение паттерна в контенте
     * 
     * @param content Исходный текст для поиска
     * @param pattern Паттерн для поиска
     * @param options Опции поиска
     * @param lineEnding Тип окончания строк
     * @return Результат поиска или null
     */
    fun findMatch(
        content: String,
        pattern: String,
        options: MatchOptions,
        lineEnding: String
    ): MatchResult?
}