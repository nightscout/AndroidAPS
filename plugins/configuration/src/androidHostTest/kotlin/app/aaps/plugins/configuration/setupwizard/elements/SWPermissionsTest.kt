package app.aaps.plugins.configuration.setupwizard.elements

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.plugin.PermissionGroup
import app.aaps.core.interfaces.protection.PasswordCheck
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.ui.R
import app.aaps.plugins.configuration.setupwizard.SWDefinition
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Pins the permission step of the setup wizard: one row per permission group, and whether the row
 * offers an action. A row that silently stops offering "Grant" would leave the user with a plugin
 * that cannot work and no way to see why, so this is covered before the file is moved.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class SWPermissionsTest {

    @get:Rule
    val compose = createComposeRule()

    private val definition: SWDefinition = mock<SWDefinition>()

    private lateinit var grantText: String
    private lateinit var changeText: String

    private val bluetooth = PermissionGroup(
        permissions = listOf("android.permission.BLUETOOTH_CONNECT"),
        rationaleTitle = TextRef.Literal("Bluetooth"),
        rationaleDescription = TextRef.Literal("Needed to talk to the pump")
    )

    private val storage = PermissionGroup(
        permissions = listOf("android.permission.READ_EXTERNAL_STORAGE"),
        rationaleTitle = TextRef.Literal("Storage"),
        rationaleDescription = TextRef.Literal("Needed to export settings"),
        alwaysShowAction = true
    )

    private fun newItem(): SWPermissions =
        SWPermissions(
            aapsLogger = mock<AAPSLogger>(),
            rh = mock<TextResolver>(),
            rxBus = mock<RxBus>(),
            preferences = mock<Preferences>(),
            passwordCheck = mock<PasswordCheck>()
        )

    @Before
    fun setUp() {
        val app = RuntimeEnvironment.getApplication()
        grantText = app.getString(R.string.grant)
        changeText = app.getString(R.string.change)
    }

    /** Stubs the getter. Assigning the property on a mock does nothing, so the getter must be stubbed. */
    private fun withItems(vararg items: Pair<PermissionGroup, Boolean>) {
        val supplier: () -> List<Pair<PermissionGroup, Boolean>> = { items.toList() }
        whenever(definition.permissionItems).thenReturn(supplier)
    }

    @Test
    fun rendersTitleAndDescriptionForEveryGroup() {
        withItems(bluetooth to false, storage to true)
        val item = newItem().with(definition)

        compose.setContent { MaterialTheme { item.Compose() } }

        compose.onNodeWithText("Bluetooth").assertIsDisplayed()
        compose.onNodeWithText("Needed to talk to the pump").assertIsDisplayed()
        compose.onNodeWithText("Storage").assertIsDisplayed()
        compose.onNodeWithText("Needed to export settings").assertIsDisplayed()
    }

    @Test
    fun notGranted_offersGrant() {
        withItems(bluetooth to false)
        val item = newItem().with(definition)

        compose.setContent { MaterialTheme { item.Compose() } }

        compose.onNodeWithText(grantText).assertIsDisplayed()
    }

    @Test
    fun granted_offersNoAction() {
        withItems(bluetooth to true)
        val item = newItem().with(definition)

        compose.setContent { MaterialTheme { item.Compose() } }

        compose.onNodeWithText(grantText).assertDoesNotExist()
        compose.onNodeWithText(changeText).assertDoesNotExist()
    }

    @Test
    fun grantedButAlwaysShowAction_offersChange() {
        withItems(storage to true)
        val item = newItem().with(definition)

        compose.setContent { MaterialTheme { item.Compose() } }

        compose.onNodeWithText(changeText).assertIsDisplayed()
    }

    @Test
    fun clickingGrant_requestsThatGroup() {
        withItems(bluetooth to false)
        val requested = mutableListOf<PermissionGroup>()
        val handler: (PermissionGroup) -> Unit = { requested += it }
        whenever(definition.onRequestPermission).thenReturn(handler)
        val item = newItem().with(definition)

        compose.setContent { MaterialTheme { item.Compose() } }
        compose.onNodeWithText(grantText).performClick()

        assert(requested == listOf(bluetooth)) { "expected a request for the bluetooth group, got $requested" }
    }

    @Test
    fun noDefinition_rendersNothing() {
        val item = newItem()

        compose.setContent { MaterialTheme { item.Compose() } }

        compose.onNodeWithText(grantText).assertDoesNotExist()
    }
}
