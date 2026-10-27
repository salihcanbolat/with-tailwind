package com.damla.backend

import com.damla.api.ErrorResponse
import com.damla.backend.api.api
import com.damla.backend.config.AppConfig
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.sse.SSE
import kotlinx.serialization.json.Json

fun main() {
    val config = AppConfig.fromEnv(System.getenv())
    val services = Services.create(config)
    embeddedServer(Netty, port = config.port) {
        damla(services)
        services.start(this)
        monitor.subscribe(ApplicationStopped) { services.close() }
    }.start(wait = true)
}

fun Application.damla(services: Services) {
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true })
    }
    install(SSE)
    // The desktop companion calls the API from its web view (docs/08). The API has no cookies or
    // credentials, so any origin may read it.
    install(CORS) {
        anyHost()
        allowMethod(HttpMethod.Post)
        allowHeader(HttpHeaders.ContentType)
    }
    install(StatusPages) {
        exception<BadRequestException> { call, cause ->
            val message = generateSequence<Throwable>(cause) { it.cause }.last().message ?: "bad request"
            call.respond(HttpStatusCode.BadRequest, ErrorResponse(listOf(message)))
        }
    }
    routing {
        get("/health") {
            call.respondText("ok")
        }
        api(services)
    }
}
