package app.aaps.pump.aaruy

import app.aaps.core.interfaces.resources.TextRefIdRegistry
import app.aaps.core.ui.CoreUiStringIds

/**
 * Lets a Compose test read the driver's [AaruyStrings] and the [app.aaps.core.ui.CoreUiStrings] its screens use as real
 * text, as the app does at start. In the app the shell registers every module. A test has no shell, so without this a
 * screen shows the string names.
 */
internal fun registerAaruyStrings() {
    TextRefIdRegistry.register("aaruy") { name -> AaruyStringIds.idOf(name) }
    TextRefIdRegistry.register("coreUi") { name -> CoreUiStringIds.idOf(name) }
}
