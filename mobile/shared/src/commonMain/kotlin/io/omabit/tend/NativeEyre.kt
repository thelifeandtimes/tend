package io.omabit.tend

import io.ktor.client.HttpClient
import io.ktor.client.request.*
import io.ktor.client.request.forms.submitForm
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.io.readByteArray
import kotlinx.serialization.json.*

/** Explicit native preview transport. No Gall probing, action marks or fallback.
 * The existing account/session storage and application identity stay unchanged.
 * UI adoption is gated on native build/distribution qualification.
 */
class NativeEyre(http: HttpClient, private val allowLocal: Boolean = false) {
    private val client = http.config { followRedirects = false }
    private val api = "/apps/tend/api/v1/"

    private suspend fun read(response: HttpResponse): JsonElement {
        if (response.status.value in setOf(401, 403))
            throw AuthenticationException("Sign in to your ship again")
        require(response.status.value in 200..299) { "Native ship request failed (${response.status.value})" }
        require((response.contentLength() ?: 0) <= Eyre.MAX_BYTES) { "Native response exceeds safety limit" }
        val bytes = response.bodyAsChannel().readRemaining(Eyre.MAX_BYTES + 1).readByteArray()
        require(bytes.size <= Eyre.MAX_BYTES) { "Native response exceeds safety limit" }
        return Wire.parseToJsonElement(bytes.decodeToString())
    }

    suspend fun login(rawUrl: String, code: String): Pair<ShipAccount, JsonObject> {
        val url = normalizeEndpoint(rawUrl, allowLocal)
        require(code.isNotBlank()) { "Enter your ship's +code" }
        val response = client.submitForm("$url/~/login", parameters { append("password", code.trim().removePrefix("+").removePrefix("~")) })
        require(response.status.value in 200..299) { "Ship login failed" }
        val cookie = response.headers.getAll(HttpHeaders.SetCookie).orEmpty()
            .map { it.substringBefore(';') }.filter { it.startsWith("urbauth-") }.joinToString("; ")
        require(cookie.isNotEmpty()) { "Ship did not issue a session" }
        val temporary = ShipAccount("", "", url, cookie)
        val identity = read(client.get(url + api + "whoami") { header(HttpHeaders.Cookie, cookie) }).jsonPrimitive.content
        require(Regex("~[a-z]+(-{1,2}[a-z]+)*").matches(identity)) { "Invalid native ship identity" }
        val account = temporary.copy(id = "$identity@$url", ship = identity)
        return account to snapshot(account)
    }

    suspend fun snapshot(account: ShipAccount): JsonObject {
        val value = read(client.get(account.url + api + "state") { header(HttpHeaders.Cookie, account.cookie) }).jsonObject
        if (value["snapshot"]?.jsonObject?.get("protocol-version")?.jsonPrimitive?.intOrNull != 3)
            throw ProtocolException("Native Tend protocol 3 required")
        return value
    }

    /** Reading a missing receipt never submits a replacement operation. */
    suspend fun receipt(account: ShipAccount, operationId: String): JsonObject? {
        require(operationId.isNotBlank() && operationId.encodeToByteArray().size <= 256)
        val response = client.get(account.url + api + "receipt/" + operationId.encodeURLPathPart()) { header(HttpHeaders.Cookie, account.cookie) }
        if (response.status == HttpStatusCode.NotFound) return null
        val value = read(response)
        return if (value == JsonNull) null else validateReceipt(value, operationId)
    }

    suspend fun command(account: ShipAccount, command: JsonObject): JsonObject {
        val operationId = if (command["kind"]?.jsonPrimitive?.content == "edit")
            command["action"]?.jsonObject?.values?.singleOrNull()?.jsonObject?.get("operation-id")?.jsonPrimitive?.content
        else command["operation-id"]?.jsonPrimitive?.content
        require(!operationId.isNullOrBlank() && operationId.encodeToByteArray().size <= 256) { "Keep the original operation ID" }
        require(command.toString().encodeToByteArray().size <= 1024 * 1024) { "Command exceeds safety limit" }
        val value = try {
            read(client.post(account.url + api + "command") {
                header(HttpHeaders.Cookie, account.cookie)
                contentType(ContentType.Application.Json)
                setBody(command.toString())
            })
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            throw NativeOperationUncertain(operationId, error)
        }
        return validateReceipt(value, operationId)
    }

    /** Polling is the preview watch contract. Online means a validated snapshot. */
    suspend fun watch(account: ShipAccount, onSnapshot: suspend (JsonObject) -> Unit) {
        var previous: JsonObject? = null
        while (currentCoroutineContext().isActive) {
            val next = snapshot(account)
            if (next != previous) onSnapshot(next)
            previous = next
            delay(2000)
        }
    }

    private fun validateReceipt(value: JsonElement, operationId: String): JsonObject {
        val receipt = value as? JsonObject
        if ((receipt?.get("protocol") as? JsonPrimitive)?.content != "tend-native-1" ||
            (receipt?.get("operation-id") as? JsonPrimitive)?.content != operationId ||
            (receipt?.get("outcome") as? JsonPrimitive)?.content !in setOf("committed", "rejected"))
            throw NativeOperationUncertain(operationId)
        return requireNotNull(receipt)
    }
}

class NativeOperationUncertain(val operationId: String, cause: Throwable? = null) :
    Exception("Native operation $operationId is uncertain; read its receipt before resubmission", cause)
