package app.aaps.core.graph.profile

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

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class ProfileViewerContentTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun profileInlineRow_rendersLabelWithColon_andValue() {
        compose.setContent {
            MaterialTheme {
                ProfileInlineRow(label = "Insulin", value = "Humalog")
            }
        }

        compose.onNodeWithText("Insulin:").assertIsDisplayed()
        compose.onNodeWithText("Humalog").assertIsDisplayed()
    }

    @Test
    fun profileRow_rendersLabelAndSingleValue() {
        compose.setContent {
            MaterialTheme {
                ProfileRow(label = "Units", value = "mg/dL")
            }
        }

        compose.onNodeWithText("Units").assertIsDisplayed()
        compose.onNodeWithText("mg/dL").assertIsDisplayed()
    }

    @Test
    fun profileRow_rendersEachValueLine() {
        compose.setContent {
            MaterialTheme {
                ProfileRow(label = "IC", value = "08:00 10.0\n12:00 8.5\n18:00 9.0")
            }
        }

        compose.onNodeWithText("IC").assertIsDisplayed()
        compose.onNodeWithText("08:00 10.0").assertIsDisplayed()
        compose.onNodeWithText("12:00 8.5").assertIsDisplayed()
        compose.onNodeWithText("18:00 9.0").assertIsDisplayed()
    }

    @Test
    fun profileCompareTable_rendersTimeAndValuesWithUnits() {
        val rows = listOf(
            ProfileCompareRow(time = "00:00", value1 = "0.5", value2 = "0.6"),
            ProfileCompareRow(time = "06:00", value1 = "0.7", value2 = "0.8")
        )
        compose.setContent {
            MaterialTheme {
                ProfileCompareTable(rows = rows, units = "U/hr")
            }
        }

        compose.onNodeWithText("00:00").assertIsDisplayed()
        compose.onNodeWithText("0.5 U/hr").assertIsDisplayed()
        compose.onNodeWithText("0.8 U/hr").assertIsDisplayed()
    }
}
