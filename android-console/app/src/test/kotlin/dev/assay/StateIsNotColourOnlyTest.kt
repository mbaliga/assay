package dev.assay

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import dev.assay.ui.AssayTheme
import dev.assay.ui.LifecycleChip
import dev.assay.ui.SeverityChip
import dev.assay.ui.StatusChip
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Every state chip must carry a text label and a spoken description (shape + label + luminance). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class StateIsNotColourOnlyTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun runStatusChipsShowALabelAndSpeakIt() {
        compose.setContent {
            AssayTheme(darkTheme = true) { Column { RunDisplay.entries.forEach { StatusChip(it) } } }
        }
        RunDisplay.entries.forEach { display ->
            compose.onNodeWithText(display.label, useUnmergedTree = true).assertIsDisplayed()
            compose.onNodeWithContentDescription("Run status: ${display.label}").assertIsDisplayed()
        }
    }

    @Test
    fun severityChipsShowTheirLabel() {
        compose.setContent {
            AssayTheme(darkTheme = false) { Column { listOf("SEV1", "SEV2", "SEV3", "SEV4").forEach { SeverityChip(it) } } }
        }
        listOf("SEV1", "SEV2", "SEV3", "SEV4").forEach {
            compose.onNodeWithText(it, useUnmergedTree = true).assertIsDisplayed()
            compose.onNodeWithContentDescription("Severity $it").assertIsDisplayed()
        }
    }

    @Test
    fun candidateLifecycleChipsShowTheirLabel() {
        val states = listOf("proposed", "proof_passed", "approved", "rejected", "stale", "applied", "detected")
        compose.setContent {
            AssayTheme(darkTheme = true) { Column { states.forEach { LifecycleChip(it) } } }
        }
        states.forEach {
            compose.onNodeWithText(it.replace('_', ' '), useUnmergedTree = true).assertIsDisplayed()
            compose.onNodeWithContentDescription("Candidate ${it.replace('_', ' ')}").assertIsDisplayed()
        }
    }
}
