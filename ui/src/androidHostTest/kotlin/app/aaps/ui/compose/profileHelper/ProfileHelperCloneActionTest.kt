package app.aaps.ui.compose.profileHelper

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.aaps.core.interfaces.resources.TextRefIdRegistry
import app.aaps.core.ui.R as CoreUiR
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

/**
 * Robolectric composable test for the bottom action and the comparison tab of
 * [ProfileHelperContent].
 *
 * Two things here decide what a user can do: the bottom button changes both its label and whether it
 * is clickable depending on whether a generated profile is available to clone, and the third tab is
 * only reachable once both sides of the comparison hold enough data. Both are easy to break while
 * moving the file and neither shows up as a compile error.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class ProfileHelperCloneActionTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var ctx: Context
    private lateinit var cloneLabel: String
    private lateinit var okLabel: String
    private lateinit var compareTab: String

    @Before
    fun setUp() {
        // MainApp does this in production; a Robolectric test has no MainApp, so a TextRef.Named
        // would have no id to resolve to and the screen would render blank text.
        TextRefIdRegistry.register("ui") { name -> UiStringIds.idOf(name) }
        ctx = RuntimeEnvironment.getApplication()
        cloneLabel = ctx.getString(R.string.clone_label)
        okLabel = ctx.getString(CoreUiR.string.ok)
        compareTab = ctx.getString(R.string.comparation)
    }

    private fun setContent(
        selectedTab: Int = 0,
        isCompareTabValid: Boolean,
        showCloneAction: Boolean,
        onTabSelected: (Int) -> Unit = {},
        onCloneClick: () -> Unit = {}
    ) {
        compose.setContent {
            MaterialTheme {
                ProfileHelperContent(
                    selectedTab = selectedTab,
                    onTabSelected = onTabSelected,
                    profileTypes = listOf(ProfileType.MOTOL_DEFAULT, ProfileType.CURRENT),
                    onProfileTypeChange = { _, _ -> },
                    isCompareTabValid = isCompareTabValid,
                    showCloneAction = showCloneAction,
                    onCloneClick = onCloneClick,
                    onBackClick = {},
                    focusManager = LocalFocusManager.current,
                    comparisonContent = { Text("COMPARISON") },
                    profileTabContent = { index -> Text("TAB CONTENT $index") }
                )
            }
        }
    }

    @Test
    fun bottomButtonOffersCloneWhenAProfileCanBeCloned() {
        setContent(isCompareTabValid = true, showCloneAction = true)

        compose.onNodeWithText(cloneLabel).assertIsDisplayed()
        compose.onNodeWithText(cloneLabel).assertIsEnabled()
    }

    @Test
    fun bottomButtonFallsBackToOkAndIsDisabledWhenNothingCanBeCloned() {
        setContent(isCompareTabValid = true, showCloneAction = false)

        compose.onNodeWithText(cloneLabel).assertDoesNotExist()
        compose.onNodeWithText(okLabel).assertIsDisplayed()
        compose.onNodeWithText(okLabel).assertIsNotEnabled()
    }

    @Test
    fun cloneButtonFiresItsCallback() {
        var cloned = false
        setContent(isCompareTabValid = true, showCloneAction = true, onCloneClick = { cloned = true })

        compose.onNodeWithText(cloneLabel).performClick()

        assertThat(cloned).isTrue()
    }

    @Test
    fun comparisonTabIsDisabledUntilBothSidesHaveData() {
        setContent(isCompareTabValid = false, showCloneAction = false)

        compose.onNodeWithText(compareTab).assertIsNotEnabled()
    }

    @Test
    fun comparisonTabIsEnabledOnceBothSidesHaveData() {
        setContent(isCompareTabValid = true, showCloneAction = false)

        compose.onNodeWithText(compareTab).assertIsEnabled()
    }

    @Test
    fun aDisabledComparisonTabCannotBeSelected() {
        var selected = -1
        setContent(isCompareTabValid = false, showCloneAction = false, onTabSelected = { selected = it })

        compose.onNodeWithText(compareTab).performClick()

        assertThat(selected).isEqualTo(-1)
    }

    @Test
    fun theComparisonTabShowsTheComparisonContent() {
        setContent(selectedTab = 2, isCompareTabValid = true, showCloneAction = true)

        compose.onNodeWithText("COMPARISON").assertIsDisplayed()
        compose.onNodeWithText("TAB CONTENT 2").assertDoesNotExist()
    }

    @Test
    fun aProfileTabShowsThatTabsContent() {
        setContent(selectedTab = 1, isCompareTabValid = true, showCloneAction = true)

        compose.onNodeWithText("TAB CONTENT 1").assertIsDisplayed()
        compose.onNodeWithText("COMPARISON").assertDoesNotExist()
    }
}
