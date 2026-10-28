package app.aaps.plugins.source.compose

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToKeyAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToKey
import app.aaps.core.interfaces.resources.TextRefIdRegistry
import app.aaps.core.keys.interfaces.PreferenceItem
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.plugins.dexcomoneplus.OnePlusCgmDriver
import app.aaps.plugins.dexcomoneplus.OnePlusCgmDriverReal
import app.aaps.plugins.dexcomoneplus.OnePlusCgmDriverStub
import app.aaps.plugins.dexcomoneplus.OnePlusGlucoseWatcher
import app.aaps.plugins.dexcomoneplus.OnePlusWarmupState
import app.aaps.plugins.dexcomoneplus.identity.OnePlusSensorPrefs
import app.aaps.plugins.dexcomoneplus.identity.OnePlusSensorStore
import app.aaps.plugins.dexcomoneplus.scan.OnePlusBleScanner
import app.aaps.plugins.dexcomoneplus.scan.OnePlusBleScannerStub
import app.aaps.plugins.source.DexcomOnePlusHost
import app.aaps.plugins.source.DexcomOnePlusPlugin
import app.aaps.plugins.source.SourceStringIds
import app.aaps.plugins.source.SourceStringsValues
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.Config as RobolectricConfig

/**
 * The sensor start screen, rendered.
 *
 * Two things this proves that no unit test can. The strings really resolve - every one of them is
 * a `TextRef.Named`, so a name that no longer matched would draw as the raw name here. And the
 * pre-soak slot is **absent** when no second slot is offered, rather than present and broken.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RobolectricConfig(sdk = [35])
class DexcomOnePlusStartScreenTest {

    @get:Rule val compose = createComposeRule()

    /** The English text of a string, which is what the screen must draw. */
    private fun text(name: String): String =
        requireNotNull(SourceStringsValues.textOf(name, null)) { "no English text for $name" }

    private fun render(twoSlots: Boolean) {
        // MainApp does this in production. Without it every TextRef.Named draws as its raw name and
        // the assertions below read "the node is not displayed" without saying why.
        TextRefIdRegistry.register("source") { name -> SourceStringIds.idOf(name) }
        compose.setContent {
            DexcomOnePlusStartScreen(
                host = FakeHost,
                permissions = GrantedPermissions,
                onBack = {},
                onEnsureDriver = { OnePlusCgmDriverStub.instance },
                onActivatePlugin = {},
                onStarted = {},
                onBeginStaging = { true },
                onIsStagingSensor = { false },
                onCancelStaging = {},
                onOpenPromote = {},
                onStagingDriver = if (twoSlots) ({ error("not reached in this test") }) else null,
                onSensorSessionStarted = { _, _ -> },
            )
        }
    }

    @Test
    fun `a phone with two slots offers the pre-soak choice`() {
        render(twoSlots = true)
        compose.onNodeWithText(text("dexcom_oneplus_slot_staging_short")).assertIsDisplayed()
        compose.onNodeWithText(text("dexcom_oneplus_slot_production_short")).assertIsDisplayed()
    }

    @Test
    fun `a phone with one slot does not show the pre-soak choice at all`() {
        render(twoSlots = false)
        compose.onNodeWithText(text("dexcom_oneplus_slot_staging_short")).assertDoesNotExist()
        compose.onNodeWithText(text("dexcom_oneplus_slot_production_short")).assertDoesNotExist()
    }

    @Test
    fun `the converted strings are drawn as their text and not as their names`() {
        render(twoSlots = true)
        compose.onNodeWithText(text("dexcom_oneplus_start_title")).assertIsDisplayed()
        compose.onNodeWithText(text("dexcom_oneplus_section_pairing_code")).assertIsDisplayed()
        compose.onNodeWithText(text("dexcom_oneplus_applicator_gs1_label")).assertIsDisplayed()
        // The bottom bar is pinned, so this one is on screen whatever the list is doing.
        compose.onNodeWithText(text("dexcom_oneplus_connect_follow")).assertIsDisplayed()
    }

    @Test
    fun `the scan part of the screen reads correctly once it is scrolled to`() {
        render(twoSlots = true)
        // The screen is one long list and the test window is a phone sized one, so the scan rows
        // are not composed until the list is asked for them.
        compose.onNode(hasScrollToKeyAction()).performScrollToKey("scanEmpty")
        compose.onNodeWithText(text("dexcom_oneplus_section_sensors_nearby")).assertExists()
        compose.onNodeWithText(text("dexcom_oneplus_scan_pick_hint")).assertExists()
        compose.onNodeWithText(text("dexcom_oneplus_scan_empty")).assertExists()
        // dexcom_oneplus_scan_idle is one of the six lines that take an argument. Nothing has been
        // heard yet so it must read zero - and a wrong argument count would leave a placeholder.
        val idle = SourceStringsValues.textOf("dexcom_oneplus_scan_idle", null)!!.replace("%1\$d", "0")
        compose.onNodeWithText(idle, useUnmergedTree = true).assertExists()
    }

    /** A phone where the Bluetooth permission is already granted. */
    private object GrantedPermissions : OnePlusBlePermissionGate {
        override fun granted(): Boolean = true
        override fun request() = Unit
    }

    /** Everything the screen asks of the phone, answered without Bluetooth or a file. */
    private object FakeHost : DexcomOnePlusHost {

        override fun attachPlatform(driver: OnePlusCgmDriver) = Unit
        override fun slotStore(namespace: String?): OnePlusSensorStore = OnePlusSensorStore(MemoryPrefs())
        override val stagingNamespace: String = "staging"
        override fun productionDriver(): OnePlusCgmDriver = OnePlusCgmDriverStub.instance
        override fun stagingDriver(): OnePlusCgmDriver = OnePlusCgmDriverStub.instance
        override fun attachProductionDriver(watcher: OnePlusGlucoseWatcher): OnePlusCgmDriver =
            OnePlusCgmDriverStub.instance

        override fun promoteStagingInstance(): OnePlusCgmDriverReal? = null
        override fun setSessionServiceWanted(wanted: Boolean) = Unit
        override fun showWarmup(state: OnePlusWarmupState) = Unit
        override fun cancelWarmupNotification() = Unit
        override fun alert(message: String) = Unit
        override fun screenScanner(): OnePlusBleScanner = OnePlusBleScannerStub()
        override fun screenItems(plugin: DexcomOnePlusPlugin): List<PreferenceItem> = emptyList()
    }

    /** An empty slot store in memory, so the screen starts with no sensor remembered. */
    private class MemoryPrefs : OnePlusSensorPrefs {

        private val values = mutableMapOf<String, Any>()

        override fun getString(key: String): String? = values[key] as? String
        override fun getLong(key: String, defaultValue: Long): Long = values[key] as? Long ?: defaultValue
        override fun getInt(key: String, defaultValue: Int): Int = values[key] as? Int ?: defaultValue
        override fun getBoolean(key: String, defaultValue: Boolean): Boolean =
            values[key] as? Boolean ?: defaultValue

        override fun contains(key: String): Boolean = values.containsKey(key)

        override fun edit(block: OnePlusSensorPrefs.Editor.() -> Unit) {
            object : OnePlusSensorPrefs.Editor {
                override fun putString(key: String, value: String?) {
                    if (value == null) values.remove(key) else values[key] = value
                }

                override fun putLong(key: String, value: Long) {
                    values[key] = value
                }

                override fun putInt(key: String, value: Int) {
                    values[key] = value
                }

                override fun putBoolean(key: String, value: Boolean) {
                    values[key] = value
                }

                override fun remove(key: String) {
                    values.remove(key)
                }

                override fun clear() {
                    values.clear()
                }
            }.block()
        }
    }
}
