package dev.assay

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import dev.assay.ui.AssayRoot
import dev.assay.ui.AssayTheme
import dev.assay.ui.Tab
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The five screens in light and dark at normal and 200 % font size. Record with
 * `gradle :app:recordRoborazziDebug`, check with `gradle :app:verifyRoborazziDebug`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class ScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val dark = mutableStateOf(false)
    private val scale = mutableStateOf(1f)
    private val tab = mutableStateOf(Tab.HOME)

    @Test
    fun fiveScreensLightAndDarkAtNormalAndDoubleFontSize() {
        compose.setContent {
            key(dark.value, scale.value, tab.value) {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, scale.value)) {
                    AssayTheme(darkTheme = dark.value) {
                        AssayRoot(TestFixtures.state, TestFixtures.NoopActions, initialTab = tab.value)
                    }
                }
            }
        }
        val options = RoborazziOptions(
            compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.005f),
        )
        for (isDark in listOf(false, true)) {
            for (fontScale in listOf(1f, 2f)) {
                for (entry in Tab.entries) {
                    dark.value = isDark
                    scale.value = fontScale
                    tab.value = entry
                    compose.waitForIdle()
                    val name = "${entry.name.lowercase()}_${if (isDark) "dark" else "light"}_${fontScale.toInt()}x"
                    compose.onRoot().captureRoboImage("src/test/screenshots/$name.png", roborazziOptions = options)
                }
            }
        }
    }
}
