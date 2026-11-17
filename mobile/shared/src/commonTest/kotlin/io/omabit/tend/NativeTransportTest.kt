package io.omabit.tend

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlin.test.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*

class NativeTransportTest {
    private val account = ShipAccount("~zod@https://ship.test", "~zod", "https://ship.test", "urbauth-test=session")
    private val command = buildJsonObject { put("kind", "grant"); put("operation-id", "keep-me") }

    @Test fun nativeWriteUsesOneRequestAndRequiresMatchingReceipt() = runTest {
        var requests = 0
        val client = HttpClient(MockEngine { request ->
            requests++
            assertEquals("/apps/tend/api/v1/command", request.url.encodedPath)
            assertEquals(HttpMethod.Post, request.method)
            assertEquals(account.cookie, request.headers[HttpHeaders.Cookie])
            respond("""{"protocol":"tend-native-1","operation-id":"keep-me","outcome":"committed","reason":"ok"}""", headers = headersOf(HttpHeaders.ContentType, "application/json"))
        })
        try {
            assertEquals("committed", NativeEyre(client).command(account, command)["outcome"]?.jsonPrimitive?.content)
            assertEquals(1, requests)
        } finally { client.close() }
    }

    @Test fun plainAcceptanceIsUncertainAndNeverRetried() = runTest {
        var requests = 0
        val client = HttpClient(MockEngine { requests++; respond("{}") })
        try {
            val error = assertFailsWith<NativeOperationUncertain> { NativeEyre(client).command(account, command) }
            assertEquals("keep-me", error.operationId)
            assertEquals(1, requests)
        } finally { client.close() }
    }

    @Test fun nativeSnapshotRejectsOldProtocolWithoutGallFallback() = runTest {
        var requests = 0
        val client = HttpClient(MockEngine { request ->
            requests++
            assertEquals("/apps/tend/api/v1/state", request.url.encodedPath)
            respond("""{"snapshot":{"protocol-version":2,"lists":[]}}""")
        })
        try {
            assertFailsWith<ProtocolException> { NativeEyre(client).snapshot(account) }
            assertEquals(1, requests)
        } finally { client.close() }
    }
}
