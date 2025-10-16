package com.robustpatcher.models

data class PatchMetadata(
    val name: String,
    val description: String,
    val author: String,
    val version: String
)

sealed class PatchAction {
    data class Replace(val find: String, val replace: String) : PatchAction()
    data class InsertBefore(val marker: String, val content: String) : PatchAction()
    data class InsertAfter(val marker: String, val content: String) : PatchAction()
    data class Delete(val find: String) : PatchAction()
    data class CreateFile(val content: String) : PatchAction()
    data class ReplaceFile(val content: String) : PatchAction()
    data class DeleteFile(val confirm: Boolean = false) : PatchAction()
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