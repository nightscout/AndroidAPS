package app.aaps.ui.compose.permissionsSheet

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.aaps.core.interfaces.plugin.PermissionGroup
import app.aaps.core.interfaces.resources.TextRefIdRegistry
import app.aaps.core.keys.interfaces.TextRef
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

/** Robolectric composable test for [PermissionsSheetContent]: title renders + grant button fires onRequestPermission. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class PermissionsSheetContentTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var titleLabel: String
    private lateinit var grantLabel: String

    @Before
    fun setUp() {
        // MainApp does this in production; a Robolectric test has no MainApp, so a TextRef.Named
        // would have no id to resolve to and the screen would render blank text.
        TextRefIdRegistry.register("ui") { name -> UiStringIds.idOf(name) }
        val ctx: Context = RuntimeEnvironment.getApplication()
        titleLabel = ctx.getString(R.string.permission_sheet_title)
        grantLabel = ctx.getString(R.string.permission_grant)
    }

    @Test
    fun rendersTitleAndFiresGrant() {
        val group = PermissionGroup(
            permissions = listOf("android.permission.POST_NOTIFICATIONS"),
            rationaleTitle = TextRef.AndroidRes(R.string.permission_change),
            rationaleDescription = TextRef.AndroidRes(R.string.permission_sheet_subtitle)
        )
        var requested: PermissionGroup? = null
        compose.setContent {
            MaterialTheme {
                PermissionsSheetContent(
                    items = listOf(PermissionItem(group = group, granted = false)),
                    snackbarHostState = remember { SnackbarHostState() },
                    onRequestPermission = { requested = it }
                )
            }
        }
        compose.onNodeWithText(titleLabel).assertIsDisplayed()
        compose.onNodeWithText(grantLabel).performClick()
        assertThat(requested).isEqualTo(group)
    }
}
