package io.omabit.tend

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.net.HttpURLConnection
import java.net.URL
import kotlin.test.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Explicitly opted-in emulator smoke test. Never includes ship credentials in the test APK. */
@RunWith(AndroidJUnit4::class)
class NativeLiveFlowTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()

    @Test
    fun realShipLoginCreateCompleteAndCleanUp() {
        val args = InstrumentationRegistry.getArguments()
        val endpoint = args.getString("tendLiveUrl")
        val loopback = args.getString("tendLiveLoopback")
        assumeTrue(
            "Opt in with a disposable fake ship's URLs",
            endpoint != null && loopback != null,
        )
        // These arguments deliberately permit only the emulator's host-loopback bridge.
        require(URL(endpoint).host == "10.0.2.2" && URL(loopback).host == "10.0.2.2")
        val repo = (ui.activity.application as TendApplication).repository
        ui.waitUntil(20_000) { repo.state.value.initialized }
        runBlocking { repo.state.value.accounts.toList().forEach { repo.forget(it) } }
        val request = URL(loopback).openConnection() as HttpURLConnection
        request.requestMethod = "POST"
        request.connectTimeout = 10_000
        request.readTimeout = 10_000
        request.doOutput = true
        request.setRequestProperty("Content-Type", "application/json")
        val code =
            try {
                request.outputStream.use {
                    it.write("""{"source":{"dojo":"+code"},"sink":{"stdout":null}}""".toByteArray())
                }
                check(request.responseCode == 200) {
                    "Cannot read the authorized fake-ship login code"
                }
                Wire.parseToJsonElement(request.inputStream.bufferedReader().use { it.readText() })
                    .jsonPrimitive
                    .content
                    .trim()
            } finally {
                request.disconnect()
            }
        var listId: Long? = null
        try {
            ui.onNodeWithText("Ship URL").performTextReplacement(endpoint!!)
            ui.onNodeWithText("Ship +code").performTextReplacement(code)
            ui.onNodeWithText("Connect to Tend").performScrollTo().performClick()
            ui.waitUntil(30_000) { repo.state.value.connection == Connection.ONLINE }
            listId = runBlocking {
                repo
                    .createList("Native Android QA ${System.currentTimeMillis()}")
                    .getValue("list-upserted")
                    .jsonObject
                    .getValue("list")
                    .jsonObject
                    .getValue("id")
                    .jsonPrimitive
                    .long
            }
            val id = listId
            ui.waitUntil(20_000) { repo.canEdit(id) }
            val reminderId = runBlocking {
                repo.addReminder(
                    id,
                    "Verified from the installed APK",
                    repo.state.value.active!!.ship,
                    null,
                )
            }
            val reminder = repo.state.value.data.list(id)!!.reminders.single { it.id == reminderId }
            runBlocking { repo.complete(id, reminder) }
            assertTrue(
                repo.state.value.data.list(id)!!.reminders.single { it.id == reminderId }.completed
            )
            assertTrue(repo.state.value.data.pending.isEmpty())
        } finally {
            listId?.let { runBlocking { repo.mutate("delete-list", it) } }
            runBlocking { repo.state.value.accounts.toList().forEach { repo.forget(it) } }
        }
    }
}
