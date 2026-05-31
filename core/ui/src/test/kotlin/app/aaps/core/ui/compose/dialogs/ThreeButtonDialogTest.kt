package app.aaps.core.ui.compose.dialogs

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.aaps.core.ui.R
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class ThreeButtonDialogTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var cancelLabel: String

    @Before
    fun setUp() {
        cancelLabel = RuntimeEnvironment.getApplication().getString(R.string.cancel)
    }

    private fun show(
        onPrimary: () -> Unit = {},
        onSecondary: () -> Unit = {},
        onDismiss: () -> Unit = {}
    ) {
        compose.setContent {
            MaterialTheme {
                ThreeButtonDialog(
                    title = "End scene",
                    message = "End Warmup?",
                    primaryLabel = "End",
                    onPrimary = onPrimary,
                    secondaryLabel = "Skip to Cooldown",
                    onSecondary = onSecondary,
                    onDismiss = onDismiss
                )
            }
        }
    }

    @Test
    fun rendersAllThreeActions() {
        show()
        compose.onNodeWithText("End").assertIsDisplayed()
        compose.onNodeWithText("Skip to Cooldown").assertIsDisplayed()
        compose.onNodeWithText(cancelLabel).assertIsDisplayed()
    }

    @Test
    fun primary_firesOnPrimaryOnly() {
        var primary = 0; var secondary = 0; var dismiss = 0
        show(onPrimary = { primary++ }, onSecondary = { secondary++ }, onDismiss = { dismiss++ })
        compose.onNodeWithText("End").performClick()
        assertThat(primary).isEqualTo(1)
        assertThat(secondary).isEqualTo(0)
        assertThat(dismiss).isEqualTo(0)
    }

    @Test
    fun secondary_firesOnSecondaryOnly() {
        var primary = 0; var secondary = 0; var dismiss = 0
        show(onPrimary = { primary++ }, onSecondary = { secondary++ }, onDismiss = { dismiss++ })
        compose.onNodeWithText("Skip to Cooldown").performClick()
        assertThat(secondary).isEqualTo(1)
        assertThat(primary).isEqualTo(0)
        assertThat(dismiss).isEqualTo(0)
    }

    @Test
    fun cancel_firesOnDismissOnly() {
        var primary = 0; var secondary = 0; var dismiss = 0
        show(onPrimary = { primary++ }, onSecondary = { secondary++ }, onDismiss = { dismiss++ })
        compose.onNodeWithText(cancelLabel).performClick()
        assertThat(dismiss).isEqualTo(1)
        assertThat(primary).isEqualTo(0)
        assertThat(secondary).isEqualTo(0)
    }
}
