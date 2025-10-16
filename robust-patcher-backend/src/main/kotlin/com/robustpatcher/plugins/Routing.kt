package com.robustpatcher.plugins

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import com.robustpatcher.routes.patchRoutes

fun Application.configureRouting() {
    routing {
        get("/") {
            call.respondText("Robust Patcher Backend v1.0.0")
        }
        
        get("/api/health") {
            call.respondText("OK")
        }
        
        patchRoutes()
    }
}