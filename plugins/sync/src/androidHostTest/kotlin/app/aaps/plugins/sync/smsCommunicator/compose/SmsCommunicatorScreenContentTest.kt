package app.aaps.plugins.sync.smsCommunicator.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Compose UI smoke test for the state-hoisted [SmsCommunicatorScreenContent], rendered headlessly on
 * the JVM via Robolectric. Each SMS row is a single styled text node, so the seeded literals (phone
 * number + body) are asserted with a substring match.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class SmsCommunicatorScreenContentTest {

    @get:Rule
    val compose = createComposeRule()

    private fun setContent(state: SmsCommunicatorUiState) {
        compose.setContent {
            MaterialTheme {
                SmsCommunicatorScreenContent(uiState = state)
            }
        }
    }

    @Test
    fun showsMessagePhoneNumberAndText() {
        setContent(
            SmsCommunicatorUiState(
                messages = listOf(
                    SmsItem(
                        time = "12:00",
                        phoneNumber = "+15551234567",
                        text = "BOLUS 1.5",
                        isReceived = true,
                        isSent = false,
                        isProcessed = true,
                        isIgnored = false
                    )
                )
            )
        )
        compose.onNodeWithText("+15551234567", substring = true).assertIsDisplayed()
        compose.onNodeWithText("BOLUS 1.5", substring = true).assertIsDisplayed()
    }

    @Test
    fun showsMultipleMessages() {
        setContent(
            SmsCommunicatorUiState(
                messages = listOf(
                    SmsItem(
                        time = "12:00",
                        phoneNumber = "+15551234567",
                        text = "FIRST MESSAGE",
                        isReceived = true,
                        isSent = false,
                        isProcessed = true,
                        isIgnored = false
                    ),
                    SmsItem(
                        time = "12:05",
                        phoneNumber = "+19998887777",
                        text = "SECOND MESSAGE",
                        isReceived = false,
                        isSent = true,
                        isProcessed = true,
                        isIgnored = false
                    )
                )
            )
        )
        compose.onNodeWithText("FIRST MESSAGE", substring = true).assertIsDisplayed()
        compose.onNodeWithText("SECOND MESSAGE", substring = true).assertIsDisplayed()
    }

    @Test
    fun emptyMessages_rendersNothing() {
        setContent(SmsCommunicatorUiState(messages = emptyList()))
        compose.onNodeWithText("BOLUS 1.5", substring = true).assertDoesNotExist()
    }
}
