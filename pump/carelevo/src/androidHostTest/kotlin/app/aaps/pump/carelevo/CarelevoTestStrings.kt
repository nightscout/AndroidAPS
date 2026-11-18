package app.aaps.pump.carelevo

import android.content.Context
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.resources.TextRefIdRegistry

/**
 * Lets a test read the driver's [CarelevoStrings] as real text, as the app does at start.
 *
 * The driver names its strings, and a name is turned into a resource id through [TextRefIdRegistry]. In the app the
 * shell registers every module. A test has no shell, so without this a screen shows the string names.
 */
internal fun registerCarelevoStrings() {
    TextRefIdRegistry.register("carelevo") { name -> CarelevoStringIds.idOf(name) }
}

/**
 * A [ResourceHelper] that reads the real strings of [context], for tests that check the text a class builds.
 * Only the calls the driver makes are answered.
 */
internal class ContextResourceHelper(private val context: Context) : ResourceHelper {

    init {
        registerCarelevoStrings()
    }

    override fun gs(id: Int): String = context.getString(id)
    override fun gs(id: Int, vararg args: Any?): String = context.getString(id, *args)
    override fun gq(id: Int, quantity: Int, vararg args: Any?): String = context.resources.getQuantityString(id, quantity, *args)
    override fun gsNotLocalised(id: Int, vararg args: Any?): String = context.getString(id, *args)
}
