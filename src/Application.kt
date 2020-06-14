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
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.auth.jwt.JWTPrincipal
import io.ktor.auth.jwt.jwt
import io.ktor.jackson.*
import io.ktor.features.*
import kotlinx.coroutines.launch
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.lang.reflect.Parameter
import java.util.*

fun main(args: Array<String>) {
    val db = HikariDataSource(HikariConfig().apply {
        jdbcUrl = "jdbc:postgresql://localhost:5432/testdb"
        username = "postgres"
        password = "example"
        driverClassName = "org.postgresql.Driver"
    })
    Database.connect(db)
    transaction {
        SchemaUtils.createMissingTablesAndColumns(UsersTable)
    }
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

data class User(val id: Int, val name: String)

object UsersTable : Table() {
    val id: Column<Int> = integer("id")
    val name: Column<String> = varchar("name", 45)

    fun getUser(id: Int): User? {
        return this.select { UsersTable.id eq id }.firstOrNull()?.toUser()
    }

    fun upsertUser(user: User) {
        val isNew = this.select { UsersTable.id eq user.id }.empty()
        if (isNew) {
            this.insert {
                it[id] = user.id
                it[name] = user.name
            }
        } else {
            this.update({ id eq user.id }) {
                it[name] = user.name
            }
        }
    }

    private fun ResultRow.toUser(): User {
        return User(
            id = this[id],
            name = this[name]
        )
    }
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
                val id = call.principal<JWTPrincipal>()?.payload?.getClaim("id")?.asInt() ?: 0
                val user = transaction {
                    UsersTable.getUser(id)
                }

                if (user == null) {
                    call.respond(HttpStatusCode.NotFound)
                    return@get
                }

                call.respond(user)
            }
            put("/user") {
                val id = call.principal<JWTPrincipal>()?.payload?.getClaim("id")?.asInt() ?: 0
                val name = call.receive<Parameters>()["name"] ?: ""
                val user = User(id, name)
                transaction {
                    UsersTable.upsertUser(user)
                }

                if (user == null) {
                    call.respond(HttpStatusCode.NotFound)
                    return@put
                }

                call.respond(user)
            }
        }
    }
}

