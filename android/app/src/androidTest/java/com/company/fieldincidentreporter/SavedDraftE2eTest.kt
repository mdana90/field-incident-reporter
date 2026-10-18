package com.company.fieldincidentreporter

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.company.fieldincidentreporter.data.ReportInput
import com.company.fieldincidentreporter.data.offline.DraftReport
import com.company.fieldincidentreporter.data.offline.DraftReports
import com.company.fieldincidentreporter.data.offline.DraftState
import com.company.fieldincidentreporter.di.E2eDraftEntryPoint
import com.company.fieldincidentreporter.model.Severity
import dagger.hilt.android.EntryPointAccessors
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Runs against the isolated local API on port 3000 through adb reverse. */
@RunWith(AndroidJUnit4::class)
class SavedDraftE2eTest {
    @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val drafts: DraftReports get() = EntryPointAccessors.fromApplication(context, E2eDraftEntryPoint::class.java).drafts()
    private val ids = mutableListOf<String>()
    private val baseUrl = "http://127.0.0.1:3000"

    @Before fun ready() { control("normal") }

    @After fun cleanup() {
        control("normal")
        ids.forEach { id -> runBlocking { drafts.delete(id) } }
    }

    @Test fun savedDraftSurvivesRecreationAndOnlySendsOnTap() {
        val draft = seedDraft(DraftState.DRAFT)
        assertFalse(serverIncidents().contains(draft.title))
        composeRule.activityRule.scenario.recreate()
        waitForText(draft.title)
        composeRule.onNodeWithText("Saved on device · tap to send").assertExists()

        composeRule.onNodeWithText(draft.title).performClick()
        waitForText("Send report")
        composeRule.onNodeWithContentDescription("Selected incident photo").assertExists()
        assertFalse(serverIncidents().contains(draft.title))
        composeRule.onNodeWithText("Send report").performClick()

        waitForText("Incident details")
        waitForText(draft.title)
        assertEquals(null, draftRow(draft.id))
        assertFalse(draft.folder.exists())
        assertTrue(serverIncidents().contains(draft.title))
    }

    @Test fun failedSendRemainsLockedUntilManualRetry() {
        val draft = seedDraft(DraftState.DRAFT)
        composeRule.activityRule.scenario.recreate()
        waitForText(draft.title)
        control("create_503")

        composeRule.onNodeWithText(draft.title).performClick()
        waitForText("Send report")
        composeRule.onNodeWithText("Send report").performClick()
        waitForText("Submission not confirmed · tap to retry")
        assertEquals(DraftState.UNCONFIRMED, draftRow(draft.id)?.state)
        assertTrue(draft.photo.exists())
        assertFalse(serverIncidents().contains(draft.title))

        composeRule.onNodeWithText("Submission not confirmed · tap to retry").performClick()
        waitForText("Retry send")
        composeRule.onNodeWithText("Save draft").assertDoesNotExist()
        composeRule.onNodeWithText("Remove").assertIsNotEnabled()
        control("normal")
        composeRule.onNodeWithText("Retry send").performClick()

        waitForText("Incident details")
        assertEquals(null, draftRow(draft.id))
        assertTrue(serverIncidents().contains(draft.title))
    }

    @Test fun retryAfterLostReplyOpensTheSingleAcceptedIncident() {
        val draft = seedDraft(DraftState.DRAFT)
        composeRule.activityRule.scenario.recreate()
        waitForText(draft.title)
        control("create_lost_reply")

        composeRule.onNodeWithText(draft.title).performClick()
        waitForText("Send report")
        composeRule.onNodeWithText("Send report").performClick()
        waitForText("Submission not confirmed · tap to retry")
        assertEquals(DraftState.UNCONFIRMED, draftRow(draft.id)?.state)
        assertEquals(1, incidentCount(draft.title))

        composeRule.onNodeWithText("Submission not confirmed · tap to retry").performClick()
        waitForText("Retry send")
        control("normal")
        composeRule.onNodeWithText("Retry send").performClick()

        waitForText("Incident details")
        assertEquals(null, draftRow(draft.id))
        assertFalse(draft.folder.exists())
        assertEquals(1, incidentCount(draft.title))
    }

    @Test fun draftsRemainVisibleDuringServerErrorAndDeleteRequiresConfirmation() {
        val draft = seedDraft(DraftState.UNCONFIRMED)
        control("list_503")
        composeRule.activityRule.scenario.recreate()
        waitForText(draft.title)
        composeRule.onNodeWithText("Server error (503). Try again.").assertExists()
        composeRule.onNodeWithText("Submission not confirmed · tap to retry").assertExists()

        composeRule.onNodeWithText("Delete").performClick()
        composeRule.onNodeWithText("The server may already have received this report.", substring = true).assertExists()
        composeRule.onNodeWithText("Cancel").performClick()
        assertTrue(draft.photo.exists())
        assertEquals(DraftState.UNCONFIRMED, draftRow(draft.id)?.state)

        composeRule.onNodeWithText("Delete").performClick()
        composeRule.onAllNodesWithText("Delete")[1].performClick()
        composeRule.waitUntil(15_000) { draftRow(draft.id) == null && !draft.folder.exists() }
        assertFalse(draft.folder.exists())
    }

    @Test fun missingPhotoOnUnconfirmedDraftShowsActionableError() {
        val draft = seedDraft(DraftState.UNCONFIRMED)
        assertTrue(draft.photo.delete())
        composeRule.activityRule.scenario.recreate()
        waitForText(draft.title)

        composeRule.onNodeWithText(draft.title).performClick()
        waitForText("Retry send")
        composeRule.onNodeWithText("Retry send").performClick()
        waitForText("A photo in this draft is missing or invalid. Delete the draft and report again.")

        assertEquals(DraftState.UNCONFIRMED, draftRow(draft.id)?.state)
        assertEquals(0, incidentCount(draft.title))
    }

    @Test fun definiteRejectionKeepsDraftEditableForCorrection() {
        val draft = seedDraft(DraftState.DRAFT)
        composeRule.activityRule.scenario.recreate()
        waitForText(draft.title)
        control("create_400")

        composeRule.onNodeWithText(draft.title).performClick()
        waitForText("Send report")
        composeRule.onNodeWithText("Send report").performClick()
        waitForText("Please check the report details and photos.")
        assertEquals(DraftState.DRAFT, draftRow(draft.id)?.state)

        composeRule.onNodeWithText(draft.title).performTextReplacement("Corrected ${draft.title}")
        composeRule.onNodeWithText("Save draft").performClick()
        waitForText("Corrected ${draft.title}")
        assertEquals("Corrected ${draft.title}", draftRow(draft.id)?.input?.title)
        assertFalse(serverIncidents().contains(draft.title))
    }

    @Test fun backDiscardsUnsavedChangesWithoutLosingSavedDraft() {
        val draft = seedDraft(DraftState.DRAFT)
        composeRule.activityRule.scenario.recreate()
        waitForText(draft.title)
        composeRule.onNodeWithText(draft.title).performClick()
        waitForText("Send report")

        composeRule.onNodeWithText(draft.title).performTextReplacement("Unsaved ${draft.title}")
        composeRule.onNodeWithText("‹ Back").performClick()
        composeRule.onNodeWithText("Discard changes?").assertExists()
        composeRule.onNodeWithText("Keep editing").performClick()
        composeRule.onNodeWithText("‹ Back").performClick()
        composeRule.onNodeWithText("Discard changes").performClick()

        waitForText(draft.title)
        assertEquals(draft.title, draftRow(draft.id)?.input?.title)
        assertTrue(draft.photo.exists())
    }

    private fun seedDraft(state: DraftState): Seed {
        val id = UUID.randomUUID().toString()
        ids += id
        val title = "E2E draft ${id.take(8)}"
        val folder = File(context.filesDir, "drafts/$id").apply { mkdirs() }
        val photo = File(folder, "photo.jpg")
        Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.RED)
            photo.outputStream().use { compress(Bitmap.CompressFormat.JPEG, 90, it) }
            recycle()
        }
        runBlocking {
            drafts.save(ReportInput(title, "E2E description", Severity.HIGH, "E2E location", listOf(photo), clientRequestId = id), state)
        }
        return Seed(id, title, folder, photo)
    }

    private fun draftRow(id: String): DraftReport? = runBlocking { drafts.get(id) }

    private fun control(mode: String) { get("$baseUrl/__e2e/control?mode=$mode") }
    private fun serverIncidents(): String = get("$baseUrl/incidents")
    private fun incidentCount(title: String): Int = JSONArray(serverIncidents()).let { incidents ->
        (0 until incidents.length()).count { incidents.getJSONObject(it).getString("title") == title }
    }

    private fun get(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 5_000
        connection.readTimeout = 5_000
        return try { connection.inputStream.bufferedReader().use { it.readText() } }
        finally { connection.disconnect() }
    }

    private fun waitForText(text: String) {
        composeRule.waitUntil(15_000) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private data class Seed(val id: String, val title: String, val folder: File, val photo: File)
}
