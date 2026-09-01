package app.aaps.desktop.shell.di

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
import app.aaps.ui.UiStringsValues

/**
 * Teaches the resolver where the text of each string name lives.
 *
 * The desktop counterpart of `MainApp.registerStringOwners()`. Android registers `R.string` id maps
 * there; this registers the generated English text maps, because a desktop JVM has no resource table
 * to look an id up in.
 *
 * **Every module this shell depends on has to be here.** A name is unique inside one module, not
 * across the project, so a module left out does not fall back to something reasonable - every string
 * it owns renders as its own name. That is how it was found: the plugin list showed
 * `objectives_shortname` and friends instead of plugin names, because only the five core modules
 * were registered and none of the plugins were.
 *
 * `DesktopStringOwnersTest` asserts one real string per registration, so a module added to the build
 * and forgotten here fails a test rather than showing up as odd text on a screen.
 */
internal object DesktopStringOwners {

    fun registerAll() {
        // Core
        TextRefValueRegistry.register("keys") { KeysStringsValues.textOf(it) }
        TextRefValueRegistry.register("interfaces") { InterfacesStringsValues.textOf(it) }
        TextRefValueRegistry.register("coreUi") { CoreUiStringsValues.textOf(it) }
        TextRefValueRegistry.register("implementation") { ImplementationStringsValues.textOf(it) }
        TextRefValueRegistry.register("ui") { UiStringsValues.textOf(it) }
        // Plugins, in the order their modules appear in the build file
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
    }
}
