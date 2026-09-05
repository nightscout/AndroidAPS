package app.aaps.ui.compose.siteRotationDialog

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.TE
import app.aaps.core.interfaces.resources.TextRefIdRegistry
import app.aaps.ui.R
import app.aaps.ui.UiStringIds
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Robolectric composable test for [InlineEditorContent]: renders + fires the arrow-select callback. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class InlineEditorContentTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var arrowLabel: String

    @Before
    fun setUp() {
        // MainApp does this in production; a Robolectric test has no MainApp, so a TextRef.Named
        // would have no id to resolve to and the screen would render blank text.
        TextRefIdRegistry.register("ui") { name -> UiStringIds.idOf(name) }
        val ctx: Context = RuntimeEnvironment.getApplication()
        arrowLabel = ctx.getString(R.string.select_arrow)
    }

    @Test
    fun rendersAndFiresArrowClick() {
        var arrowClicked = false
        compose.setContent {
            MaterialTheme {
                InlineEditorContent(
                    te = TE(timestamp = 1000L, type = TE.Type.NOTE, glucoseUnit = GlucoseUnit.MGDL),
                    dateString = "2023-01-01 12:00",
                    locationString = "Left arm",
                    onArrowClick = { arrowClicked = true },
                    onNoteChange = {}
                )
            }
        }
        compose.onNodeWithContentDescription(arrowLabel).assertIsDisplayed()
        compose.onNodeWithContentDescription(arrowLabel).performClick()
        assertThat(arrowClicked).isTrue()
    }
}
