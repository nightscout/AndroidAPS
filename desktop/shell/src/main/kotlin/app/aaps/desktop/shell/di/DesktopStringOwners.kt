package app.aaps.desktop.shell.di

import app.aaps.core.interfaces.InterfacesStringsValues
import app.aaps.core.interfaces.resources.TextRefValueRegistry
import app.aaps.core.keys.KeysStringsValues
import app.aaps.core.ui.CoreUiStringsValues
import app.aaps.implementation.ImplementationStringsValues
import app.aaps.ui.UiStringsValues

/**
 * Teaches the resolver where the text of each string name lives.
 *
 * The desktop counterpart of `MainApp.registerStringOwners()`. Android registers `R.string` id maps
 * there; this registers the generated English text maps, because a desktop JVM has no resource table
 * to look an id up in.
 *
 * **Only the modules this shell depends on.** A name is unique inside one module, not across the
 * project, so registering an owner whose module is not on the classpath is not possible and
 * registering the wrong map would return the wrong text. When the desktop app grows to host the
 * plugin screens, their owners are added here at the same time as the dependency.
 */
internal object DesktopStringOwners {

    fun registerAll() {
        TextRefValueRegistry.register("keys") { KeysStringsValues.textOf(it) }
        TextRefValueRegistry.register("interfaces") { InterfacesStringsValues.textOf(it) }
        TextRefValueRegistry.register("coreUi") { CoreUiStringsValues.textOf(it) }
        TextRefValueRegistry.register("implementation") { ImplementationStringsValues.textOf(it) }
        TextRefValueRegistry.register("ui") { UiStringsValues.textOf(it) }
    }
}
