package com.robustpatcher.routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import com.robustpatcher.models.*
import com.robustpatcher.services.PatcherService

fun Route.patchRoutes(
patcherService: PatcherService = PatcherService()
) {
    
    route("/api/patch") {
        post("/apply") {
            try {
                val request = call.receive<ApplyPatchRequest>()
                
                // Валидация
                if (request.patchContent.isBlank()) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        PatchResponse(
                            success = false,
                            metadata = MetadataResponse("", "", "", ""),
                            results = emptyList(),
                            stats = StatsResponse(0, 0, 0),
                            error = "Patch content cannot be empty"
                        )
                    )
                    return@post
                }
                
                val response = patcherService.applyPatch(
                    patchContent = request.patchContent,
                    baseDir = request.baseDir,
                    dryRun = request.dryRun
                )
                
                call.respond(HttpStatusCode.OK, response)
                
            } catch (e: Exception) {
                call.application.environment.log.error("Error applying patch", e)
                call.respond(
                    HttpStatusCode.BadRequest,
                    PatchResponse(
                        success = false,
                        metadata = MetadataResponse("", "", "", ""),
                        results = emptyList(),
                        stats = StatsResponse(0, 0, 0),
                        error = e.message ?: "Unknown error"
                    )
                )
            }
        }
        
        post("/validate") {
            try {
                val request = call.receive<ApplyPatchRequest>()
                
                val response = patcherService.validatePatch(request.patchContent)
                
                call.respond(HttpStatusCode.OK, response)
                
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to (e.message ?: "Validation failed"))
                )
            }
        }
    }
}