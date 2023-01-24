package com.dannndi.plugins

import com.dannndi.feature.drawing.drawingWebsocket
import io.ktor.server.routing.*
import io.ktor.server.response.*
import io.ktor.server.application.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*

fun Application.configureRouting() {
    routing {
        get("/") {
            call.respondText("Hello World!")
        }
        drawingWebsocket()
    }
}