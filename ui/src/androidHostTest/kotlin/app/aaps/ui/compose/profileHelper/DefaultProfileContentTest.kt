package app.aaps.ui.compose.profileHelper

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import app.aaps.core.interfaces.resources.TextRefIdRegistry
import app.aaps.ui.UiStringIds
import app.aaps.ui.R
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Robolectric composable test for [DefaultProfileContent]: renders the parameter fields (all shown). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class DefaultProfileContentTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var title: String

    @Before
    fun setUp() {
        // MainApp does this in production; a Robolectric test has no MainApp, so a TextRef.Named
        // would have no id to resolve to and the screen would render blank text.
        TextRefIdRegistry.register("ui") { name -> UiStringIds.idOf(name) }
        val ctx: Context = RuntimeEnvironment.getApplication()
        title = ctx.getString(R.string.profile_parameters)
    }

    @Test
    fun rendersParametersTitleWithAllFields() {
        compose.setContent {
            MaterialTheme {
                DefaultProfileContent(
                    age = 40, onAgeChange = {},
                    weight = 70.0, onWeightChange = {},
                    tdd = 30.0, onTddChange = {},
                    pct = 100.0, onPctChange = {},
                    showPct = true, showWeight = true, showTdd = true
                )
            }
        }
        compose.onNodeWithText(title).assertIsDisplayed()
    }
}
