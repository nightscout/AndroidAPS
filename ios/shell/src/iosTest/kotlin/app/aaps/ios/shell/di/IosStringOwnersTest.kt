package app.aaps.ios.shell.di

import app.aaps.core.interfaces.InterfacesStrings
import app.aaps.core.interfaces.resources.TextRefValueRegistry
import app.aaps.core.keys.KeysStrings
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.ui.CoreUiStrings
import app.aaps.implementation.ImplementationStrings
import app.aaps.plugins.aps.ApsStrings
import app.aaps.plugins.automation.AutomationStrings
import app.aaps.plugins.calibration.CalibrationStrings
import app.aaps.plugins.configuration.ConfigurationStrings
import app.aaps.plugins.constraints.ConstraintsStrings
import app.aaps.plugins.main.MainStrings
import app.aaps.plugins.sensitivity.SensitivityStrings
import app.aaps.plugins.smoothing.SmoothingStrings
import app.aaps.plugins.source.SourceStrings
import app.aaps.plugins.sync.SyncStrings
import app.aaps.pump.virtual.VirtualStrings
import app.aaps.ui.UiStrings
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * That every module the iOS framework links can have its text found.
 *
 * The failure this guards against is quiet rather than loud: an owner missing from
 * `GeneratedStringOwners` does not crash, it makes that module's screens render string **names** -
 * `pref_title_units` instead of "Units" - and only on the screens that module owns. Nobody notices
 * until they open the right screen, which is how the whole iOS app looked before this was wired.
 *
 * So this checks the registration by owner rather than by spot-checking one label.
 */
class IosStringOwnersTest {

    @BeforeTest
    fun setUp() {
        TextRefValueRegistry.clear()
    }

    @AfterTest
    fun tearDown() {
        TextRefValueRegistry.clear()
    }

    private fun textOf(owner: String, name: String) = TextRefValueRegistry.textOf(TextRef.Named(owner, name))

    /** Without this the next test would pass on a registry some other test had already filled. */
    @Test
    fun `nothing resolves before the owners are registered`() {
        assertNull(textOf("coreUi", "settings"))
    }

    @Test
    fun `registering gives real English text rather than the name`() {
        GeneratedStringOwners.registerAll()

        assertEquals("Settings", textOf("coreUi", "settings"))
        assertEquals("Units", textOf("keys", "pref_title_units"))
        assertEquals("Cancel", textOf("coreUi", "cancel"))
    }

    /**
     * Every module the framework links, checked by asking each for a name it really owns.
     *
     * The build already makes sure each module is in the generated registry. This checks the other
     * half: that the registration really reaches that module's text. Each name comes from the module's
     * own generated object, so its owner is spelled the way the module spells it, and a missing
     * registration gives null here instead of string names on that module's screens.
     */
    @Test
    fun `every linked module resolves one of its own strings`() {
        GeneratedStringOwners.registerAll()

        listOf(
            InterfacesStrings.advisoralarm,
            KeysStrings.absorption_cutoff_summary,
            CoreUiStrings.configbuilder_general,
            ImplementationStrings.backup_to_google_drive,
            UiStrings.a11y_add_new_insulin,
            ApsStrings.array_of_elements,
            AutomationStrings.automation,
            CalibrationStrings.no_calibration_name,
            ConfigurationStrings.units,
            ConstraintsStrings.dst_plugin_name,
            MainStrings.basal_shortname,
            SensitivityStrings.sensitivity_oref1,
            SmoothingStrings.no_smoothing_name,
            SourceStrings.ns_client_bg,
            SyncStrings.a11y_otp_qr_code,
            VirtualStrings.virtual_pump_type
        ).forEach { ref ->
            val named = ref as TextRef.Named
            assertNotNull(TextRefValueRegistry.textOf(named), "owner '${named.owner}' has no text, so it is not registered")
        }
    }

    @Test
    fun `an unknown owner has no text`() {
        GeneratedStringOwners.registerAll()

        assertNull(textOf("no_such_owner", "settings"))
    }
}
