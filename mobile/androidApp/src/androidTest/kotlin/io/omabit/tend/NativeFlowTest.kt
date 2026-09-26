package io.omabit.tend

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.*

/** Exercises the packaged app against tools/fixture_ship.py, not a mocked repository. */
@RunWith(AndroidJUnit4::class)
class NativeFlowTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()
    private val repo get() = (ui.activity.application as TendApplication).repository
    private fun waitFor(condition: () -> Boolean) = ui.waitUntil(20_000, condition)
    private fun screenshot(name: String) {
        ui.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = File(ui.activity.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
        instrumentation.uiAutomation.takeScreenshot().let { bitmap ->
            File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
    private fun login(code: String) {
        ui.onNodeWithText("Ship URL").performTextReplacement("http://10.0.2.2:18787")
        ui.onNodeWithText("Ship +code").performTextReplacement(code)
        ui.onNodeWithText("Connect to Tend").performScrollTo().performClick()
        waitFor { repo.state.value.connection == Connection.ONLINE && repo.state.value.active?.ship == "~${code.removePrefix("+")}" }
    }
    private fun allView() {
        ui.onNodeWithContentDescription("Open navigation").performClick()
        ui.onNodeWithText("All reminders").performClick()
    }

    @Test fun loginDraftScheduleRecurrenceMultiShipAndOffline() {
        val title = "Native QA reminder ${System.currentTimeMillis()}"
        waitFor { repo.state.value.initialized }
        // Explicitly forget only this disposable app's fixture accounts on reruns.
        runBlocking { repo.state.value.accounts.toList().forEach { repo.forget(it) } }
        screenshot("01-login")
        login("+zod")
        allView()
        ui.onNodeWithText("A second thing").assertIsDisplayed()
        ui.onNodeWithText("A small step underneath").assertIsDisplayed()
        screenshot("02-all-nesting")
        ui.onNodeWithContentDescription("New reminder").performClick()
        ui.onNodeWithText("What needs tending?").performTextInput(title)
        ui.onNodeWithText("~zod (me)").performScrollTo().performClick()
        ui.onNodeWithText("~bus").performClick()
        ui.onNodeWithText("Set a due date").performScrollTo().performClick()
        ui.onNodeWithText("Does not repeat").performScrollTo().performClick()
        ui.onNodeWithText("Daily").performClick()
        screenshot("03-recurrence-editor")
        ui.onNodeWithText("Set date").performClick()
        // Receiving a fresh snapshot must not reset any field of a user's draft.
        repo.reconnect()
        waitFor { repo.state.value.connection == Connection.ONLINE }
        ui.onNodeWithText(title).assertExists()
        ui.onNodeWithText("~bus").assertExists()
        ui.onNodeWithContentDescription("Clear due date").assertExists()
        screenshot("04-new-reminder")
        ui.onNodeWithText("Add reminder").performScrollTo().performClick()
        waitFor { !repo.state.value.busy && repo.state.value.data.lists.flatMap { it.reminders }.any { it.title == title && it.schedule?.recurrence != null } }
        val created = repo.state.value.data.list(1)!!.reminders.last { it.title == title }
        assertEquals("~bus", created.assignee)
        assertEquals("daily", created.schedule!!.recurrence!!.frequency)
        assertTrue(repo.state.value.data.pending.isEmpty())
        ui.onNodeWithText("Find a reminder").performTextInput(title.substringAfterLast(" "))
        ui.onNodeWithContentDescription("Complete $title").performClick()
        waitFor { (repo.state.value.data.list(1)!!.reminders.first { it.id == created.id }.schedule?.occurrence ?: 0) > 0 }
        assertFalse(repo.state.value.data.list(1)!!.reminders.first { it.id == created.id }.completed)
        screenshot("05-next-occurrence")
        ui.onNodeWithText(title).performClick()
        ui.onNodeWithText("Notes").performScrollTo().performTextInput("Saved from the native Android editor.")
        ui.onNodeWithText("Save").performScrollTo().performClick()
        waitFor { !repo.state.value.busy && repo.state.value.data.list(1)!!.reminders.first { it.id == created.id }.notes.isNotBlank() }
        ui.onNodeWithContentDescription("Accounts").performClick()
        ui.onNodeWithText("Connect another ship").performScrollTo().performClick()
        login("nec")
        assertTrue(repo.state.value.data.lists.none { l -> l.reminders.any { it.title == title } })
        ui.onNodeWithContentDescription("Accounts").performClick()
        screenshot("06-separate-accounts")
        ui.onAllNodesWithText("~zod")[0].performClick()
        waitFor { repo.state.value.active?.ship == "~zod" && repo.state.value.connection == Connection.ONLINE }
        assertTrue(repo.state.value.data.list(1)!!.reminders.any { it.id == created.id && it.notes.isNotBlank() })
        ui.activityRule.scenario.recreate()
        waitFor { repo.state.value.connection == Connection.ONLINE }
        allView()
        runBlocking { repo.setForeground(false) }
        waitFor { repo.state.value.connection == Connection.OFFLINE }
        assertFalse(repo.canEdit(1))
        ui.onNodeWithText("Offline · cached reminders · read-only").assertExists()
        screenshot("07-offline-cache")
        repo.setForeground(true)
    }
}
