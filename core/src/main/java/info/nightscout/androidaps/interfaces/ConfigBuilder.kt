package info.nightscout.androidaps.interfaces

import info.nightscout.interfaces.PluginType

interface ConfigBuilder {
    fun initialize()
    fun storeSettings(from: String)
    fun performPluginSwitch(changedPlugin: PluginBase, enabled: Boolean, type: PluginType)
}