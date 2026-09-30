package io.omabit.tend

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.io.readByteArray
import kotlinx.serialization.json.*

fun normalizeEndpoint(raw: String, allowLocal: Boolean = false): String {
    val text = raw.trim().trimEnd('/')
    val u =
        runCatching { Url(if ("://" in text) text else "https://$text") }
            .getOrElse { error("Enter a valid ship URL") }
    require(
        u.host.isNotBlank() &&
            u.user == null &&
            u.password == null &&
            u.parameters.isEmpty() &&
            u.fragment.isBlank()
    ) {
        "Use a ship base URL without credentials, query or fragment"
    }
    require(u.encodedPath in listOf("", "/")) { "Use the ship's base URL, without /apps/tend" }
    require(
        u.protocol == URLProtocol.HTTPS ||
            (allowLocal &&
                u.protocol == URLProtocol.HTTP &&
                u.host in setOf("localhost", "127.0.0.1", "10.0.2.2"))
    ) {
        "Ship connections require HTTPS"
    }
    return u.toString().trimEnd('/')
}

class Eyre(private val http: HttpClient, private val allowLocal: Boolean = false) {
    companion object {
        const val MAX_BYTES = 32L * 1024 * 1024
    }

    private suspend fun body(response: HttpResponse, limit: Long = MAX_BYTES): String {
        if (response.status.value in listOf(401, 403))
            throw AuthenticationException(
                "Your ship session expired. Sign in again with the current +code."
            )
        if (response.status.value !in 200..299)
            error(
                "Ship request failed (HTTP ${response.status.value}). Check the URL and that %tend is running."
            )
        require((response.contentLength() ?: 0) <= limit) {
            "Ship response exceeds the safety limit"
        }
        val bytes = response.bodyAsChannel().readRemaining(limit + 1).readByteArray()
        require(bytes.size <= limit) { "Ship response exceeds the safety limit" }
        return bytes.decodeToString()
    }

    suspend fun login(rawUrl: String, code: String): Pair<ShipAccount, TendState> {
        val url = normalizeEndpoint(rawUrl, allowLocal)
        require(code.isNotBlank()) { "Enter the +code from your ship" }
        val response =
            http.submitForm(
                "$url/~/login",
                parameters { append("password", code.trim().removePrefix("+").removePrefix("~")) },
            )
        body(response)
        val cookies =
            response.headers
                .getAll(HttpHeaders.SetCookie)
                .orEmpty()
                .map { it.substringBefore(';') }
                .filter { it.startsWith("urbauth-") }
        require(cookies.isNotEmpty()) { "The ship did not issue a session. Check your +code." }
        val temporary = ShipAccount("", "", url, cookies.joinToString("; "))
        val identity =
            scry(temporary, "whoami").jsonPrimitive.content.let { "~" + it.removePrefix("~") }
        require(Regex("~[a-z]+(-{1,2}[a-z]+)*").matches(identity)) {
            "The ship returned an invalid identity"
        }
        val account = temporary.copy(id = "$identity@$url", ship = identity)
        val state = reduce(TendState(), scry(account, "state").jsonObject)
        return account to state
    }

    suspend fun scry(account: ShipAccount, path: String): JsonElement =
        Wire.parseToJsonElement(
            body(
                http.get("${account.url}/~/scry/tend/$path.json") {
                    header(HttpHeaders.Cookie, account.cookie)
                }
            )
        )

    suspend fun settingsDesk(account: ShipAccount, desk: String): JsonElement {
        require(desk in setOf("omarchy-urbit-theme", "talon"))
        val response =
            http.get("${account.url}/~/scry/settings/desk/$desk.json") {
                header(HttpHeaders.Cookie, account.cookie)
            }
        if (response.status.value == 404) return JsonObject(emptyMap())
        return Wire.parseToJsonElement(body(response, 1024 * 1024))
    }

    suspend fun receipt(account: ShipAccount, operationId: String): JsonObject? {
        val response =
            http.get("${account.url}/~/scry/tend/receipt/$operationId.json") {
                header(HttpHeaders.Cookie, account.cookie)
            }
        if (response.status.value == 404) return null
        return Wire.parseToJsonElement(body(response)).jsonObject
    }

    suspend fun commands(account: ShipAccount, channel: String, values: JsonArray) {
        body(
            http.put("${account.url}/~/channel/$channel") {
                header(HttpHeaders.Cookie, account.cookie)
                contentType(ContentType.Application.Json)
                setBody(values.toString())
            }
        )
    }

    suspend fun subscribe(
        account: ShipAccount,
        channel: String,
        app: String = "tend",
        path: String = "/all",
        onReady: suspend () -> Unit = {},
        onFact: suspend (JsonObject) -> Unit,
    ) {
        var commandId = 1
        commands(
            account,
            channel,
            buildJsonArray {
                add(
                    buildJsonObject {
                        put("id", 1)
                        put("action", "subscribe")
                        put("ship", account.ship.removePrefix("~"))
                        put("app", app)
                        put("path", path)
                    }
                )
            },
        )
        try {
            http
                .prepareGet("${account.url}/~/channel/$channel") {
                    header(HttpHeaders.Cookie, account.cookie)
                    accept(ContentType.Text.EventStream)
                }
                .execute { response ->
                    if (response.status.value !in 200..299) {
                        body(response)
                        error("Cannot open ship event stream")
                    }
                    val input = response.bodyAsChannel()
                    var eventId: Long? = null
                    val data = StringBuilder()
                    while (currentCoroutineContext().isActive) {
                        val line =
                            input.readUTF8Line(MAX_BYTES.toInt())
                                ?: error("Ship event stream closed")
                        if (line.isEmpty()) {
                            if (data.isNotEmpty()) {
                                val message = Wire.parseToJsonElement(data.toString()).jsonObject
                                if (message["err"] != null && message["err"] != JsonNull)
                                    error("Ship subscription refused")
                                if (message["response"]?.jsonPrimitive?.content == "quit")
                                    error("Ship subscription closed")
                                if (message["response"]?.jsonPrimitive?.content == "subscribe")
                                    onReady()
                                message["json"]?.let { if (it is JsonObject) onFact(it) }
                                eventId?.let { id ->
                                    commands(
                                        account,
                                        channel,
                                        buildJsonArray {
                                            add(
                                                buildJsonObject {
                                                    put("id", ++commandId)
                                                    put("action", "ack")
                                                    put("event-id", id)
                                                }
                                            )
                                        },
                                    )
                                }
                            }
                            data.clear()
                            eventId = null
                        } else if (line.startsWith("id:"))
                            eventId = line.substring(3).trim().toLongOrNull()
                        else if (line.startsWith("data:")) {
                            if (data.isNotEmpty()) data.append('\n')
                            data.append(line.substring(5).trimStart())
                            require(data.length <= MAX_BYTES) { "Ship event exceeds safety limit" }
                        }
                    }
                }
        } finally {
            withContext(NonCancellable) {
                withTimeoutOrNull(2000) {
                    runCatching {
                        commands(
                            account,
                            channel,
                            buildJsonArray {
                                add(
                                    buildJsonObject {
                                        put("id", ++commandId)
                                        put("action", "delete")
                                    }
                                )
                            },
                        )
                    }
                }
            }
        }
    }

    suspend fun poke(account: ShipAccount, action: JsonObject, channel: String) {
        require(action.toString().encodeToByteArray().size <= 1024 * 1024)
        commands(
            account,
            channel,
            buildJsonArray {
                add(
                    buildJsonObject {
                        put("id", 1)
                        put("action", "poke")
                        put("ship", account.ship.removePrefix("~"))
                        put("app", "tend")
                        put("mark", "tend-action-1")
                        put("json", action)
                    }
                )
            },
        )
        // An HTTP 204 only accepted the channel request. Observe Gall's poke nack,
        // but still require the durable receipt before reporting canonical success.
        http
            .prepareGet("${account.url}/~/channel/$channel") {
                header(HttpHeaders.Cookie, account.cookie)
                accept(ContentType.Text.EventStream)
            }
            .execute { response ->
                if (response.status.value !in 200..299) body(response)
                val input = response.bodyAsChannel()
                val data = StringBuilder()
                while (currentCoroutineContext().isActive) {
                    val line =
                        input.readUTF8Line(MAX_BYTES.toInt())
                            ?: error("Ship closed the acknowledgement stream")
                    if (line.isEmpty() && data.isNotEmpty()) {
                        val message = Wire.parseToJsonElement(data.toString()).jsonObject
                        data.clear()
                        if (
                            message["response"]?.jsonPrimitive?.content == "poke" &&
                                message["id"]?.jsonPrimitive?.intOrNull == 1
                        ) {
                            if (message["err"] != null && message["err"] != JsonNull)
                                throw PokeRejected(
                                    "Your ship rejected the request before committing it. Refresh and try again."
                                )
                            return@execute
                        }
                    } else if (line.startsWith("data:")) {
                        if (data.isNotEmpty()) data.append('\n')
                        data.append(line.substring(5).trimStart())
                        require(data.length <= MAX_BYTES) {
                            "Ship acknowledgement exceeds safety limit"
                        }
                    }
                }
            }
    }

    suspend fun closeChannel(account: ShipAccount, channel: String) {
        runCatching {
            commands(
                account,
                channel,
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("id", 2)
                            put("action", "delete")
                        }
                    )
                },
            )
        }
    }
}

class PokeRejected(message: String) : Exception(message)
