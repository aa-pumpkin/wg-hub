package de.wghub.app.data

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.http.setCookie
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

val json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
}

/** A failed request. [code] is the API's error code, `network_error` when the server was not reached. */
class ApiException(
    val status: Int,
    val code: String,
    val fields: Map<String, String> = emptyMap(),
) : Exception(code) {
    val isOffline get() = status == 0
}

private const val SESSION_COOKIE = "wghub_session"

/**
 * Talks to the same API as the website. The session is the same cookie the website uses,
 * kept by the app and sent with every request.
 */
class Api(private val settings: Settings) {
    private val client = HttpClient {
        expectSuccess = false
        install(HttpTimeout) {
            connectTimeoutMillis = 5_000
            requestTimeoutMillis = 15_000
        }
    }

    /** Returns the response body as text, or throws [ApiException]. */
    suspend fun send(method: String, path: String, body: String? = null): String {
        val response: HttpResponse = try {
            client.request(settings.serverUrl.trimEnd('/') + "/api" + path) {
                this.method = HttpMethod.parse(method)
                settings.token?.let { header("Cookie", "$SESSION_COOKIE=$it") }
                if (body != null) {
                    contentType(ContentType.Application.Json)
                    setBody(body)
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            throw ApiException(0, "network_error")
        }

        response.setCookie().firstOrNull { it.name == SESSION_COOKIE }?.let { cookie ->
            settings.token = cookie.value.ifEmpty { null }
        }

        val text = response.bodyAsText()
        // The PC answers but the app behind it is down: the same as no connection for the user.
        if (response.status.value in 502..504) throw ApiException(0, "network_error")
        if (response.status.value !in 200..299) {
            val error = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
            val code = error?.get("error")?.jsonPrimitive?.content ?: "internal_error"
            val fields = (error?.get("fields") as? JsonObject)
                ?.mapValues { it.value.jsonPrimitive.content }
                .orEmpty()
            throw ApiException(response.status.value, code, fields)
        }
        return text
    }
}
