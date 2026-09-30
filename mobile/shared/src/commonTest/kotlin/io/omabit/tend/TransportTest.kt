package io.omabit.tend

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.http.content.OutgoingContent
import kotlin.test.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*

class TransportTest {
    private val account =
        ShipAccount("~zod@https://ship.test", "~zod", "https://ship.test", "urbauth-~zod=session")

    @Test
    fun settingsSubscribeAcknowledgesBeforeInitialScryAndUsesIndependentDesk() = runTest {
        val order = mutableListOf<String>()
        val commands = mutableListOf<JsonObject>()
        val client =
            HttpClient(
                MockEngine { request ->
                    assertEquals(account.cookie, request.headers[HttpHeaders.Cookie])
                    when {
                        request.method == HttpMethod.Put -> {
                            val command =
                                Wire.parseToJsonElement(
                                        (request.body as OutgoingContent.ByteArrayContent)
                                            .bytes()
                                            .decodeToString()
                                    )
                                    .jsonArray
                                    .single()
                                    .jsonObject
                            commands += command
                            respond("", HttpStatusCode.NoContent)
                        }
                        request.url.encodedPath.contains("/scry/") -> {
                            order += "scry"
                            respond("""{"desk":{}}""")
                        }
                        else ->
                            respond(
                                "id: 1\ndata: {\"id\":1,\"response\":\"subscribe\",\"ok\":\"ok\"}\n\nid: 2\ndata: {\"id\":1,\"response\":\"diff\",\"json\":{\"put-entry\":{}}}\n\n",
                                headers = headersOf(HttpHeaders.ContentType, "text/event-stream"),
                            )
                    }
                }
            )
        try {
            val api = Eyre(client)
            assertFails {
                api.subscribe(
                    account,
                    "theme-channel",
                    app = "settings",
                    path = "/desk/omarchy-urbit-theme",
                    onReady = {
                        order += "ready"
                        api.settingsDesk(account, "omarchy-urbit-theme")
                    },
                ) {
                    order += "invalidated"
                }
            } // Finite fixture stream ends, triggering reconnection in the repository.
            assertEquals(listOf("ready", "scry", "invalidated"), order)
            assertEquals("settings", commands.first()["app"]?.jsonPrimitive?.content)
            assertEquals(
                "/desk/omarchy-urbit-theme",
                commands.first()["path"]?.jsonPrimitive?.content,
            )
            assertEquals(
                listOf("subscribe", "ack", "ack", "delete"),
                commands.map { it["action"]!!.jsonPrimitive.content },
            )
            assertEquals(listOf(1, 2, 3, 4), commands.map { it["id"]!!.jsonPrimitive.int })
        } finally {
            client.close()
        }
    }

    @Test
    fun missingSettingsIsNotAFailedTendConnection() = runTest {
        val client = HttpClient(MockEngine { respond("", HttpStatusCode.NotFound) })
        try {
            assertNull(profileTheme(Eyre(client).settingsDesk(account, "omarchy-urbit-theme")))
        } finally {
            client.close()
        }
    }

    @Test
    fun loginStripsLeadingPlusAndValidatesShipAndProtocol() = runTest {
        val requests = mutableListOf<String>()
        val client =
            HttpClient(
                MockEngine { request ->
                    requests += request.url.encodedPath
                    when (request.url.encodedPath) {
                        "/~/login" -> {
                            assertEquals(
                                "password=sampel-palnet",
                                (request.body as OutgoingContent.ByteArrayContent)
                                    .bytes()
                                    .decodeToString(),
                            )
                            respond(
                                "",
                                HttpStatusCode.NoContent,
                                headersOf(
                                    HttpHeaders.SetCookie,
                                    "urbauth-~zod=session; HttpOnly; Path=/",
                                ),
                            )
                        }
                        "/~/scry/tend/whoami.json" -> {
                            assertEquals(account.cookie, request.headers[HttpHeaders.Cookie])
                            respond("\"~zod\"")
                        }
                        else -> respond("""{"snapshot":{"protocol-version":2,"lists":[]}}""")
                    }
                }
            ) {
                followRedirects = false
            }
        try {
            for (code in listOf(" +sampel-palnet ", " ~sampel-palnet ", "sampel-palnet")) {
                val (saved, state) = Eyre(client).login("ship.test", code)
                assertEquals(account, saved)
                assertEquals(2, state.protocol)
            }
            assertEquals(9, requests.size)
        } finally {
            client.close()
        }
    }

    @Test
    fun noCookieIsNotSuccessfulAuthentication() = runTest {
        val client = HttpClient(MockEngine { respond("", HttpStatusCode.OK) })
        try {
            assertFails { Eyre(client).login("ship.test", "wrong") }
        } finally {
            client.close()
        }
    }

    @Test
    fun expiredSessionIsExplicit() = runTest {
        val client = HttpClient(MockEngine { respond("", HttpStatusCode.Forbidden) })
        try {
            assertFailsWith<AuthenticationException> { Eyre(client).scry(account, "state") }
        } finally {
            client.close()
        }
    }

    @Test
    fun unsupportedProtocolNeverBecomesSavedSession() = runTest {
        val client =
            HttpClient(
                MockEngine { request ->
                    when (request.url.encodedPath) {
                        "/~/login" ->
                            respond(
                                "",
                                HttpStatusCode.NoContent,
                                headersOf(HttpHeaders.SetCookie, "urbauth-~zod=session"),
                            )
                        "/~/scry/tend/whoami.json" -> respond("\"~zod\"")
                        else -> respond("""{"snapshot":{"protocol-version":3,"lists":[]}}""")
                    }
                }
            )
        try {
            assertFailsWith<ProtocolException> { Eyre(client).login("ship.test", "code") }
        } finally {
            client.close()
        }
    }

    @Test
    fun missingReceiptIsStillUnconfirmed() = runTest {
        val client = HttpClient(MockEngine { respond("", HttpStatusCode.NotFound) })
        try {
            assertNull(Eyre(client).receipt(account, "mobile-test"))
        } finally {
            client.close()
        }
    }

    @Test
    fun pokeNackIsNotMistakenForSuccess() = runTest {
        val client =
            HttpClient(
                MockEngine { request ->
                    if (request.method == HttpMethod.Put) respond("", HttpStatusCode.NoContent)
                    else
                        respond(
                            "id: 1\ndata: {\"id\":1,\"response\":\"poke\",\"err\":\"bad-action\"}\n\n",
                            headers = headersOf(HttpHeaders.ContentType, "text/event-stream"),
                        )
                }
            )
        try {
            assertFailsWith<PokeRejected> {
                Eyre(client)
                    .poke(
                        account,
                        buildJsonObject {
                            put("create-list", buildJsonObject { put("title", "A list") })
                        },
                        "channel",
                    )
            }
        } finally {
            client.close()
        }
    }

    @Test
    fun pokeAckAllowsReceiptConfirmation() = runTest {
        val client =
            HttpClient(
                MockEngine { request ->
                    assertEquals(account.cookie, request.headers[HttpHeaders.Cookie])
                    if (request.method == HttpMethod.Put) respond("", HttpStatusCode.NoContent)
                    else
                        respond(
                            ": heartbeat\n\nid: 1\ndata: {\"id\":1,\"response\":\"poke\",\"ok\":\"ok\"}\n\n",
                            headers = headersOf(HttpHeaders.ContentType, "text/event-stream"),
                        )
                }
            )
        try {
            Eyre(client)
                .poke(
                    account,
                    buildJsonObject {
                        put("create-list", buildJsonObject { put("title", "A list") })
                    },
                    "channel",
                )
        } finally {
            client.close()
        }
    }

    @Test
    fun oversizedResponseIsRejectedBeforeParsing() = runTest {
        val client =
            HttpClient(
                MockEngine {
                    respond(
                        "{}",
                        headers =
                            headersOf(HttpHeaders.ContentLength, (Eyre.MAX_BYTES + 1).toString()),
                    )
                }
            )
        try {
            assertFails { Eyre(client).scry(account, "state") }
        } finally {
            client.close()
        }
    }

    @Test
    fun staleReceiptCannotRollbackPreferences() {
        val preferences = buildJsonObject {
            put("revision", 9)
            put("default-list", 8)
        }
        val reduced =
            reduce(
                TendState(preferences = preferences),
                Wire.parseToJsonElement(
                        """{"preferences-updated":{"operation-id":"old","preferences":{"revision":2,"default-list":1}}}"""
                    )
                    .jsonObject,
            )
        assertEquals(preferences, reduced.preferences)
    }
}
