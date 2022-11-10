package info.nightscout.interfaces

import info.nightscout.interfaces.plugin.PluginBase
import info.nightscout.interfaces.plugin.PluginType

interface ConfigBuilder {
    fun initialize()
    fun storeSettings(from: String)
    fun performPluginSwitch(changedPlugin: PluginBase, enabled: Boolean, type: PluginType)
}