package com.robustpatcher.models

import kotlinx.serialization.Serializable

@Serializable
data class ApplyPatchRequest(
    val patchContent: String,
    val dryRun: Boolean = true,
    val baseDir: String = "."
)