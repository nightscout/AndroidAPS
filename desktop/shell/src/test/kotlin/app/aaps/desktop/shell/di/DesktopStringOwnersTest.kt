package app.aaps.desktop.shell.di

import app.aaps.core.interfaces.InterfacesStrings
import app.aaps.core.interfaces.resources.TextRefValueRegistry
import app.aaps.core.keys.KeysStrings
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.ui.CoreUiStrings
import app.aaps.implementation.ImplementationStrings
import app.aaps.implementation.resources.GeneratedTextResolver
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
import kotlin.test.assertNotEquals

/**
 * That every module the desktop shell registers really resolves to its text.
 *
 * The build makes sure each module is in `GeneratedStringOwners`: the module and the registry both
 * read the same entry in `StringOwnerModules.ALL`. What the build cannot see is whether the
 * registration reaches the text at run time, and the only symptom of that failing is a screen showing
 * string **names** instead of words. That reads like unfinished UI rather than like a bug, which is
 * how it would survive review.
 *
 * Each case takes its name from that module's own generated object instead of repeating the owner
 * as a literal, so it asks with the owner exactly as the module generated it.
 */
class DesktopStringOwnersTest {

    private val resolver = GeneratedTextResolver()

    @BeforeTest fun register() {
        TextRefValueRegistry.clear()
        GeneratedStringOwners.registerAll()
    }

    @AfterTest fun cleanUp() {
        TextRefValueRegistry.clear()
    }

    @Test fun `resolves a string to real english text and not to its name`() {
        assertEquals("General", resolver.gs(CoreUiStrings.configbuilder_general))
    }

    @Test fun `every registered owner resolves one of its own strings`() {
        // One case per registration, so a single misspelled owner fails rather than only the first.
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
            assertNotEquals(
                named.name,
                resolver.gs(named),
                "owner '${named.owner}' resolves to the string name, so it is not registered correctly"
            )
        }
    }

    @Test fun `substitutes an argument into a real format string`() {
        // Proves the generated text keeps its placeholder through XML parsing and Kotlin escaping.
        // "Run alarm in %1$d min" is what this one says in strings.xml.
        assertEquals("Run alarm in 20 min", resolver.gs(InterfacesStrings.alarminxmin, 20))
    }

    @Test fun `an owner the shell does not depend on falls back to the name`() {
        // The other half of the contract: an unregistered module must not resolve and must not throw.
        assertEquals("greeting", resolver.gs(TextRef.Named("no_such_module", "greeting")))
    }
}
