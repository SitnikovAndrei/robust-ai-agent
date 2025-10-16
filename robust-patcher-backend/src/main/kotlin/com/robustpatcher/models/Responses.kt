package com.robustpatcher.models

import kotlinx.serialization.Serializable

@Serializable
data class PatchResponse(
    val success: Boolean,
    val metadata: MetadataResponse,
    val results: List<FileResultResponse>,
    val stats: StatsResponse,
    val error: String? = null
)

@Serializable
data class MetadataResponse(
    val name: String,
    val description: String,
    val author: String,
    val version: String
)

@Serializable
data class FileResultResponse(
    val file: String,
    val description: String,
    val action: String,
    val status: String,
    val message: String
)

@Serializable
data class StatsResponse(
    val success: Int,
    val skipped: Int,
    val failed: Int
)