package com.robustpatcher.models

data class PatchMetadata(
    val name: String,
    val description: String,
    val author: String,
    val version: String
)

enum class MatchMode {
    NORMALIZED,   // Нормализация пробелов, точное совпадение
    FUZZY,        // Нормализация + Levenshtein
    TOKENIZED,    // Токенизация (убирает ВСЕ переносы и пробелы)
    SEMANTIC,     // По сигнатуре
    REGEX,
    CONTAINS,
    LINE_RANGE
}

enum class AnchorScope {
    AUTO,        // Автоматическое определение
    FUNCTION,    // Внутри функции
    CLASS,       // Внутри класса
    BLOCK,       // Внутри блока {}
    FILE         // Весь файл
}

data class AnchorOptions(
    val anchorText: String,
    val matchMode: MatchMode = MatchMode.FUZZY,
    val scope: AnchorScope = AnchorScope.AUTO,
    val searchDepth: Int = 1  // -1 = без ограничений
)

data class MatchOptions(
    val mode: MatchMode = MatchMode.NORMALIZED,
    
    // FUZZY параметры
    val fuzzyThreshold: Double = 0.85,
    val ignoreComments: Boolean = false,
    val ignoreEmptyLines: Boolean = true,
    val caseSensitive: Boolean = true,
    
    // SEMANTIC параметры (поиск по сигнатуре)
    val matchFunctionName: Boolean = true,        // Имя функции/метода
    val matchClassName: Boolean = true,           // Имя класса
    val matchParameterTypes: Boolean = true,      // Типы параметров
    val matchParameterNames: Boolean = false,     // Имена параметров
    val matchReturnType: Boolean = true,          // Тип возвращаемого значения
    val matchModifiers: Boolean = false,          // Модификаторы (public, private, etc)

    // TOKENIZED параметры
    val tokenWindowSize: Int = 0,  // 0 = автоматически из pattern
    
    // ANCHOR
    val anchor: AnchorOptions? = null
)

sealed class PatchAction {
    data class Replace(
        val find: String, 
        val replace: String,
        val matchOptions: MatchOptions = MatchOptions()
    ) : PatchAction()
    
    data class InsertBefore(
        val marker: String, 
        val content: String,
        val matchOptions: MatchOptions = MatchOptions()
    ) : PatchAction()
    
    data class InsertAfter(
        val marker: String, 
        val content: String,
        val matchOptions: MatchOptions = MatchOptions()
    ) : PatchAction()
    
    data class Delete(
        val find: String,
        val matchOptions: MatchOptions = MatchOptions()
    ) : PatchAction()
    
    data class CreateFile(val content: String) : PatchAction()
    data class ReplaceFile(val content: String) : PatchAction()
    class DeleteFile() : PatchAction()
    data class MoveFile(val destination: String, val overwrite: Boolean = false) : PatchAction()
}

data class FilePatch(
    val file: String,
    val description: String,
    val action: PatchAction
)

enum class PatchStatus {
    SUCCESS, SKIPPED, FAILED, FILE_NOT_FOUND
}

data class FilePatchResult(
    val file: String,
    val description: String,
    val action: String,
    val status: PatchStatus,
    val message: String
)