package app.aaps.ui.compose.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class PageIndicatorDotsTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun rendersDots_multiplePages() {
        compose.setContent {
            MaterialTheme {
                PageIndicatorDots(
                    pageCount = 4,
                    currentPage = 1,
                    modifier = Modifier.testTag("dots")
                )
            }
        }

        compose.onNodeWithTag("dots").assertIsDisplayed()
    }

    @Test
    fun rendersDots_singlePage() {
        compose.setContent {
            MaterialTheme {
                PageIndicatorDots(
                    pageCount = 1,
                    currentPage = 0,
                    modifier = Modifier.testTag("dots")
                )
            }
        }

        compose.onNodeWithTag("dots").assertIsDisplayed()
    }
}
