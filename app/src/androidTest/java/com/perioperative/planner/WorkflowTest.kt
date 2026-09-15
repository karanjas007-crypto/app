package com.perioperative.planner

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class WorkflowTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun capture(name: String) {
        ui.waitForIdle()
        val folder = File(context.getExternalFilesDir(null), "verification").apply { mkdirs() }
        File(folder, "$name.png").outputStream().use {
            ui.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
    @Test fun caseToHandoverAndEncryptedSave() {
        ui.onNodeWithText("Cases").performClick()
        ui.onNodeWithText("Add demo").performClick()
        ui.onNodeWithTag("label").performScrollTo().performTextClearance()
        ui.onNodeWithTag("label").performTextInput("Fictional workflow verification")
        closeSoftKeyboard()
        capture("01-case")

        ui.onNodeWithTag("nav_1").performClick()
        ui.onNodeWithTag("airway.opening").assertTextContains("2")
        capture("02-assessment")
        ui.onAllNodesWithText("Add to plan").onFirst().performScrollTo().performClick()

        ui.onNodeWithTag("nav_2").performClick()
        capture("03-plan")
        ui.onNodeWithTag("plan.reason").performScrollTo().performTextInput("Fictional editable plan reasoning")
        closeSoftKeyboard()
        ui.onNodeWithTag("nav_3").performClick()
        ui.onNodeWithText("Operating-room checklist").assertIsDisplayed()
        capture("04-preparation")

        ui.onNodeWithTag("nav_4").performClick()
        ui.onNodeWithText("PACU").performClick()
        capture("05-recovery")
        ui.onNodeWithText("Preview handover").performScrollTo().performClick()
        ui.onNodeWithText("Handover preview").assertIsDisplayed()
        ui.onNodeWithText("Close").performClick()

        ui.waitUntil(15_000) {
            ui.onAllNodesWithText("Saved on device").fetchSemanticsNodes().isNotEmpty()
        }
        val book = Storage(context).read()
        val saved = book.cases.first { it.id == book.selected }
        assertEquals("Fictional workflow verification", saved.v("label"))
        assertEquals("Fictional editable plan reasoning", saved.v("plan.reason"))
        assertEquals("PACU", saved.v("recovery.destination"))
        assertTrue(saved.actions.isNotEmpty())
        assertTrue(saved.actions.first().trigger.isNotBlank())
        val storedBytes = File(context.filesDir, "cases-v1.enc").readBytes()
        assertFalse(String(storedBytes, Charsets.UTF_8).contains(saved.v("label")))

        val report = Report.text(saved)
        assertTrue(report.contains(saved.actions.first().trigger))
        assertTrue(report.contains("Fictional editable plan reasoning"))
        val pdf = File(context.getExternalFilesDir(null), "verification/handover.pdf")
        pdf.outputStream().use { Report.pdf(report, it) }
        ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            PdfRenderer(fd).use { renderer -> assertTrue(renderer.pageCount >= 1) }
        }
        ui.activityRule.scenario.recreate()
        ui.onNodeWithTag("nav_0").performClick()
        ui.onNodeWithTag("label").assertTextContains("Fictional workflow verification")
    }
}
