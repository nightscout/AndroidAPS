package info.nightscout.androidaps.interfaces

interface ConfigBuilder {
    fun initialize()
    fun storeSettings(from: String)
    fun performPluginSwitch(changedPlugin: PluginBase, enabled: Boolean, type: PluginType)
}