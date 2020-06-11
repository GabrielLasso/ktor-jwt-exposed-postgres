package com.example

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.application.*
import io.ktor.response.*
import io.ktor.request.*
import io.ktor.routing.*
import io.ktor.http.*
import io.ktor.auth.*
import com.fasterxml.jackson.databind.*
import io.ktor.auth.jwt.JWTPrincipal
import io.ktor.auth.jwt.jwt
import io.ktor.jackson.*
import io.ktor.features.*
import org.jetbrains.exposed.sql.Database
import java.util.*

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

object JwtConfig {
    private const val secret = "secret"
    private val algorithm = Algorithm.HMAC256(secret)
    val verifier = JWT.require(algorithm).build()

    private fun expiresAt() = Date(System.currentTimeMillis() + 24 * 3600 * 1000) // 1 day

    fun makeToken(id: Int): String = JWT.create()
        .withSubject("Authentication")
        .withClaim("id", id)
        .withExpiresAt(expiresAt())
        .sign(algorithm)
}

@Suppress("unused") // Referenced in application.conf
@kotlin.jvm.JvmOverloads
fun Application.module(testing: Boolean = false) {
    install(Authentication) {
        jwt("jwt") {
            verifier(JwtConfig.verifier)
            validate {
                JWTPrincipal(it.payload)
            }
        }
    }

    install(ContentNegotiation) {
        jackson {
            enable(SerializationFeature.INDENT_OUTPUT)
        }
    }

    routing {
        get("/token/{id}") {
            val id = call.parameters["id"]
            val token = JwtConfig.makeToken(id?.toInt() ?: 0)

            call.respond(mapOf("token" to token))
        }

        authenticate("jwt") {
            get("/user") {
                call.respondText(call.principal<JWTPrincipal>()?.let {
                    it.payload.getClaim("id").asInt().toString()
                } ?: "")
            }
        }
    }
}

