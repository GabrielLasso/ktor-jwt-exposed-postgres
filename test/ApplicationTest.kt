package com.example

import io.ktor.application.*
import io.ktor.response.*
import io.ktor.request.*
import io.ktor.routing.*
import io.ktor.http.*
import io.ktor.auth.*
import com.fasterxml.jackson.databind.*
import io.ktor.jackson.*
import io.ktor.features.*
import kotlin.test.*
import io.ktor.server.testing.*

class ApplicationTest {
    @Test
    fun testToken() {
        withTestApplication({module(testing = true)}){
            handleRequest(HttpMethod.Get, "/user") {
                addHeader("Authorization", "Bearer ${JwtConfig.makeToken(42)}")
            }.apply {
                assertEquals(HttpStatusCode.OK, response.status())
                assertEquals("42", response.content)
            }
        }
    }
}
