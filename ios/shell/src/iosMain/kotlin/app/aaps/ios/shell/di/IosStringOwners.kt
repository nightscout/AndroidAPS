package app.aaps.ios.shell.di

import app.aaps.core.interfaces.InterfacesStringsValues
import app.aaps.core.interfaces.resources.TextRefValueRegistry
import app.aaps.core.keys.KeysStringsValues
import app.aaps.core.ui.CoreUiStringsValues
import app.aaps.implementation.ImplementationStringsValues
import app.aaps.plugins.aps.ApsStringsValues
import app.aaps.plugins.automation.AutomationStringsValues
import app.aaps.plugins.calibration.CalibrationStringsValues
import app.aaps.plugins.configuration.ConfigurationStringsValues
import app.aaps.plugins.constraints.ConstraintsStringsValues
import app.aaps.plugins.main.MainStringsValues
import app.aaps.plugins.sensitivity.SensitivityStringsValues
import app.aaps.plugins.smoothing.SmoothingStringsValues
import app.aaps.plugins.source.SourceStringsValues
import app.aaps.plugins.sync.SyncStringsValues
import app.aaps.pump.virtual.VirtualStringsValues
import app.aaps.ui.UiStringsValues

/**
 * Teaches the resolver where the text of each string name lives.
 *
 * The iOS counterpart of `MainApp.registerStringOwners()` and `DesktopStringOwners`. Android
 * registers `R.string` id maps; this registers the generated English text maps, because iOS has no
 * resource table to look an id up in.
 *
 * Longer than the desktop list because the iOS framework links more: every module the shell exports
 * has to be here, or its screens fall back to showing string names. A name is only unique inside one
 * module, so an owner registered against the wrong map would return the wrong text - which is why
 * this is written out by hand rather than discovered.
 *
 * Must run before anything asks for text. [app.aaps.ios.shell.IosAppStartup] calls it, before the
 * plugin list is built.
 */
internal object IosStringOwners {

    fun registerAll() {
        TextRefValueRegistry.register("keys") { KeysStringsValues.textOf(it) }
        TextRefValueRegistry.register("interfaces") { InterfacesStringsValues.textOf(it) }
        TextRefValueRegistry.register("coreUi") { CoreUiStringsValues.textOf(it) }
        TextRefValueRegistry.register("implementation") { ImplementationStringsValues.textOf(it) }
        TextRefValueRegistry.register("ui") { UiStringsValues.textOf(it) }
        TextRefValueRegistry.register("aps") { ApsStringsValues.textOf(it) }
        TextRefValueRegistry.register("automation") { AutomationStringsValues.textOf(it) }
        TextRefValueRegistry.register("calibration") { CalibrationStringsValues.textOf(it) }
        TextRefValueRegistry.register("configuration") { ConfigurationStringsValues.textOf(it) }
        TextRefValueRegistry.register("constraints") { ConstraintsStringsValues.textOf(it) }
        TextRefValueRegistry.register("main") { MainStringsValues.textOf(it) }
        TextRefValueRegistry.register("sensitivity") { SensitivityStringsValues.textOf(it) }
        TextRefValueRegistry.register("smoothing") { SmoothingStringsValues.textOf(it) }
        TextRefValueRegistry.register("source") { SourceStringsValues.textOf(it) }
        TextRefValueRegistry.register("sync") { SyncStringsValues.textOf(it) }
        TextRefValueRegistry.register("virtual") { VirtualStringsValues.textOf(it) }
    }
}
