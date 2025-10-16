package com.robustpatcher.services

import com.robustpatcher.models.*
import java.io.File

class PatcherService {
    
    fun applyPatch(
        patchContent: String,
        baseDir: String,
        dryRun: Boolean
    ): PatchResponse {
        val baseDirFile = File(baseDir).canonicalFile
        
        // Проверка безопасности
        if (!baseDirFile.exists()) {
            throw IllegalArgumentException("Base directory does not exist: $baseDir")
        }
        
        val parser = PatchParser()
        val (metadata, patches) = parser.parse(patchContent)
        
        val executor = PatchExecutor(baseDirFile)
        val results = patches.map { patch ->
            executor.execute(patch, dryRun)
        }
        
        val stats = calculateStats(results)
        
        return PatchResponse(
            success = true,
            metadata = MetadataResponse(
                name = metadata.name,
                description = metadata.description,
                author = metadata.author,
                version = metadata.version
            ),
            results = results.map { result ->
                FileResultResponse(
                    file = result.file,
                    description = result.description,
                    action = result.action,
                    status = result.status.name.lowercase(),
                    message = result.message
                )
            },
            stats = StatsResponse(
                success = stats.success,
                skipped = stats.skipped,
                failed = stats.failed
            )
        )
    }
    
    fun validatePatch(patchContent: String): Map<String, Any> {
        return try {
            val parser = PatchParser()
            val (metadata, patches) = parser.parse(patchContent)
            
            mapOf(
                "valid" to true,
                "metadata" to mapOf(
                    "name" to metadata.name,
                    "description" to metadata.description,
                    "author" to metadata.author,
                    "version" to metadata.version
                ),
                "patchCount" to patches.size
            )
        } catch (e: Exception) {
            mapOf(
                "valid" to false,
                "error" to (e.message ?: "Unknown error")
            )
        }
    }
    
    private fun calculateStats(results: List<FilePatchResult>): StatsResponse {
        var success = 0
        var skipped = 0
        var failed = 0
        
        results.forEach { result ->
            when (result.status) {
                PatchStatus.SUCCESS -> success++
                PatchStatus.SKIPPED -> skipped++
                PatchStatus.FAILED, PatchStatus.FILE_NOT_FOUND -> failed++
            }
        }
        
        return StatsResponse(success, skipped, failed)
    }
}