package app.aaps.plugins.configuration.setupwizard.elements

import android.widget.LinearLayout
import androidx.annotation.StringRes
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.interfaces.configuration.ConfigBuilder
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.protection.PasswordCheck
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.extensions.scanForActivity
import java.security.InvalidParameterException
import javax.inject.Inject

class SWPlugin @Inject constructor(
    aapsLogger: AAPSLogger, rh: ResourceHelper, rxBus: RxBus, preferences: Preferences, passwordCheck: PasswordCheck,
    private val activePlugin: ActivePlugin,
    private val configBuilder: ConfigBuilder
) : SWItem(aapsLogger, rh, rxBus, preferences, passwordCheck) {

    private var pType: PluginType? = null
    @StringRes private var pluginDescription = 0

    fun option(pType: PluginType, @StringRes pluginDescription: Int): SWPlugin {
        this.pType = pType
        this.pluginDescription = pluginDescription
        return this
    }

    override fun generateDialog(layout: LinearLayout) {
        val pType = this.pType ?: throw InvalidParameterException()
        val activity = layout.context.scanForActivity() ?: error("Activity not found")
        configBuilder.createViewsForPlugins(
            title = null,
            description = pluginDescription,
            pluginType = pType,
            plugins = activePlugin.getSpecificPluginsVisibleInList(pType),
            pluginViewHolders = null,
            activity = activity,
            parent = layout,
            showExpanded = true
        )
        super.generateDialog(layout)
    }
}