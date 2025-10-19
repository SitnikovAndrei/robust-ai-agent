package com.robustpatcher

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import com.robustpatcher.plugins.*

fun main() {
    embeddedServer(
        Netty, 
        port = 9081,
        host = "0.0.0.0",
        module = Application::module
    ).start(wait = true)
}

fun Application.module() {
    configureSerialization()
    configureCORS()
    configureRouting()
    configureStatusPages()
    configureLogging()
}