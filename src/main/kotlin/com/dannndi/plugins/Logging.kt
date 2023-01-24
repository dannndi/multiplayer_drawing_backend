package com.dannndi.plugins

import io.ktor.server.application.*
import io.ktor.server.plugins.callloging.*
import io.ktor.server.request.*
import org.slf4j.event.Level

fun Application.configureLogging(){
    install(CallLogging){
        level = Level.INFO
        format { call ->
            val status = call.response.status()
            val httpMethod = call.request.httpMethod.value
            val path = call.request.path()
            "Status: $status, HTTP method: $httpMethod, Path : $path, "
        }
    }
}