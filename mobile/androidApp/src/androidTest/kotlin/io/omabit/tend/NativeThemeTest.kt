package io.omabit.tend

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.view.WindowCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.test.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Opt-in only. Writes two settings entries on disposable fakes; restores their original values. */
@RunWith(AndroidJUnit4::class)
class NativeThemeTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()
    private val repo
        get() = (ui.activity.application as TendApplication).repository

    private val slots =
        listOf(
            Triple("omarchy-urbit-theme", "appearance", "current"),
            Triple("talon", "ui-prefs", "themes"),
        )

    private fun waitFor(condition: () -> Boolean) = ui.waitUntil(20000, condition)

    private fun request(url: String, cookie: String = "", body: String? = null): String {
        val r = URL(url).openConnection() as HttpURLConnection
        r.connectTimeout = 10000
        r.readTimeout = 15000
        r.setRequestProperty("Cookie", cookie)
        r.setRequestProperty("Content-Type", "application/json")
        if (body != null) {
            r.requestMethod = "POST"
            r.doOutput = true
        }
        try {
            if (body != null) r.outputStream.use { it.write(body.toByteArray()) }
            check(r.responseCode in 200..299) { "Fake-ship request failed: ${r.responseCode}" }
            return r.inputStream.bufferedReader().use { it.readText() }
        } finally {
            r.disconnect()
        }
    }

    private fun login(port: Int, loop: Int): ShipAccount {
        val code =
            Wire.parseToJsonElement(
                    request(
                        "http://10.0.2.2:$loop",
                        body = """{"source":{"dojo":"+code"},"sink":{"stdout":null}}""",
                    )
                )
                .jsonPrimitive
                .content
                .trim()
        runBlocking { repo.login("http://10.0.2.2:$port", code) }
        waitFor { repo.state.value.connection == Connection.ONLINE }
        return repo.state.value.active!!
    }

    private fun read(a: ShipAccount, slot: Int): JsonElement? {
        val (desk, bucket, key) = slots[slot]
        val body =
            Wire.parseToJsonElement(request("${a.url}/~/scry/settings/desk/$desk.json", a.cookie))
                .jsonObject
        return (body["desk"] ?: body).jsonObject[bucket]?.jsonObject?.get(key)
    }

    private fun put(a: ShipAccount, slot: Int, value: JsonElement?) {
        val (desk, bucket, key) = slots[slot]
        val channel = "${a.url}/~/channel/theme-native-${System.nanoTime()}"
        fun command(values: String) {
            val r = URL(channel).openConnection() as HttpURLConnection
            r.requestMethod = "PUT"
            r.doOutput = true
            r.connectTimeout = 10000
            r.readTimeout = 15000
            r.setRequestProperty("Cookie", a.cookie)
            r.setRequestProperty("Content-Type", "application/json")
            try {
                r.outputStream.use { it.write(values.toByteArray()) }
                check(r.responseCode == 204)
            } finally {
                r.disconnect()
            }
        }
        val action = buildJsonObject {
            put(
                if (value == null || value == JsonNull) "del-entry" else "put-entry",
                buildJsonObject {
                    put("desk", desk)
                    put("bucket-key", bucket)
                    put("entry-key", key)
                    if (value != null && value != JsonNull) put("value", value)
                },
            )
        }
        val r = URL(channel).openConnection() as HttpURLConnection
        r.setRequestProperty("Cookie", a.cookie)
        r.readTimeout = 15000
        r.connectTimeout = 10000
        try {
            command(
                buildJsonArray {
                        add(
                            buildJsonObject {
                                put("id", 1)
                                put("action", "poke")
                                put("ship", a.ship.removePrefix("~"))
                                put("app", "settings")
                                put("mark", "settings-event")
                                put("json", action)
                            }
                        )
                    }
                    .toString()
            )
            check(r.responseCode == 200)
            r.inputStream.bufferedReader().use { input ->
                while (true) {
                    val line = input.readLine() ?: error("Missing settings ack")
                    if (!line.startsWith("data:")) continue
                    val event =
                        Wire.parseToJsonElement(line.removePrefix("data:").trim()).jsonObject
                    if (event["response"]?.jsonPrimitive?.content == "poke") {
                        check(event["err"] == null || event["err"] == JsonNull) {
                            "Settings rejected test theme"
                        }
                        return
                    }
                }
            }
        } finally {
            r.disconnect()
            command("""[{"id":2,"action":"delete"}]""")
        }
    }

    private fun profile(mode: String) =
        JsonPrimitive(
            buildJsonObject {
                    put("schemaVersion", 1)
                    put("theme", "Native QA $mode")
                    put("updateId", if (mode == "dark") "f".repeat(32) else "0".repeat(32))
                    put(
                        "colors",
                        buildJsonObject {
                            put("mode", mode)
                            if (mode == "dark") {
                                put("background", "#141c19")
                                put("foreground", "#d2dfd5")
                                put("accent", "#94c8a6")
                                put("selection", "#344d3f")
                                put("muted", "#809a87")
                                put("dark_background", "#0f1512")
                                put("lighter_background", "#202b26")
                            } else {
                                put("background", "#faf5e9")
                                put("foreground", "#302d27")
                                put("accent", "#426950")
                                put("selection", "#d4e2c9")
                                put("muted", "#716956")
                                put("dark_background", "#f3eddf")
                                put("lighter_background", "#fffcf4")
                            }
                        },
                    )
                }
                .toString()
        )

    private fun screenshot(name: String) {
        ui.waitForIdle()
        Thread.sleep(350)
        val directory =
            File(ui.activity.getExternalFilesDir(null), "screenshots/themes").apply { mkdirs() }
        InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().let { bitmap ->
            File(directory, "$name.png").outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            bitmap.recycle()
        }
    }

    @Test
    fun realSettingsThemesPreserveDraftsAndSeparateAccounts() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("tendThemeFakes") == "true")
        waitFor { repo.state.value.initialized }
        runBlocking { repo.state.value.accounts.toList().forEach { repo.forget(it) } }
        val saved = mutableListOf<Pair<ShipAccount, List<JsonElement?>>>()
        try {
            val wex = login(8082, 12324)
            saved += wex to slots.indices.map { read(wex, it) }
            val rus = login(8081, 12323)
            saved += rus to slots.indices.map { read(rus, it) }
            runBlocking { repo.select(wex) }
            waitFor { repo.state.value.connection == Connection.ONLINE }
            put(wex, 0, profile("dark"))
            waitFor { repo.state.value.data.appearance.shipTheme?.name == "Native QA dark" }
            screenshot("01-dark-home")
            ui.onNodeWithContentDescription("New reminder").performClick()
            ui.onNodeWithText("What needs tending?").performTextInput("My theme-change draft")
            put(wex, 0, profile("light"))
            waitFor { repo.state.value.data.appearance.palette.mode == "light" }
            ui.onNodeWithText("My theme-change draft").assertExists()
            screenshot("02-light-draft")
            ui.activityRule.scenario.recreate()
            waitFor { repo.state.value.connection == Connection.ONLINE }
            ui.onNodeWithText("My theme-change draft").assertExists()
            ui.runOnIdle {
                assertTrue(
                    WindowCompat.getInsetsController(
                            ui.activity.window,
                            ui.activity.window.decorView,
                        )
                        .isAppearanceLightStatusBars
                )
            }
            ui.onNodeWithText("Set a due date").performScrollTo().performClick()
            ui.onNodeWithContentDescription("Choose calendar day").performClick()
            screenshot("03-light-calendar")
            ui.onNodeWithText("Back").performClick()
            ui.onNodeWithText("Cancel").performClick()
            ui.onNodeWithContentDescription("Close editor").performScrollTo().performClick()
            ui.onNodeWithContentDescription("Accounts", useUnmergedTree = true).performClick()
            ui.onNodeWithContentDescription("Follow ship theme").performScrollTo().performClick()
            waitFor { !repo.state.value.data.appearance.followShip }
            assertEquals(OmarchyPalette(), repo.state.value.data.appearance.palette)
            screenshot("04-local-opt-out")
            runBlocking { repo.select(rus) }
            waitFor { repo.state.value.connection == Connection.ONLINE }
            assertTrue(repo.state.value.data.appearance.followShip)
            val bridge =
                JsonPrimitive(
                    """{"activeId":"other","themes":[{"id":"omarchy-urbit-theme","name":"Bridge QA","dark":false,"background":"#faf5e9","primary":"#426950","surface":"#f3eddf","text":"#302d27"}]}"""
                )
            put(rus, 1, bridge)
            waitFor { repo.state.value.data.appearance.shipTheme?.source == "Talon bridge" }
            screenshot("05-peer-bridge")
            repo.setForeground(false)
            runBlocking { repo.select(wex) }
            assertFalse(repo.state.value.data.appearance.followShip)
            runBlocking { repo.followShipTheme(true) }
            assertEquals("light", repo.state.value.data.appearance.palette.mode)
            screenshot("06-cached-light-offline")
            repo.setForeground(true)
            waitFor { repo.state.value.connection == Connection.ONLINE }
            put(wex, 0, JsonPrimitive("""{"schemaVersion":99}"""))
            Thread.sleep(600)
            assertEquals("light", repo.state.value.data.appearance.palette.mode)
            assertEquals(Connection.ONLINE, repo.state.value.connection)
            put(wex, 1, bridge)
            put(wex, 0, null)
            waitFor { repo.state.value.data.appearance.shipTheme?.source == "Talon bridge" }
            put(wex, 1, null)
            waitFor { repo.state.value.data.appearance.shipTheme == null }
        } finally {
            for ((a, entries) in saved) for (i in slots.indices) put(a, i, entries[i])
            repo.setForeground(true)
            runBlocking { repo.state.value.accounts.toList().forEach { repo.forget(it) } }
        }
    }
}
