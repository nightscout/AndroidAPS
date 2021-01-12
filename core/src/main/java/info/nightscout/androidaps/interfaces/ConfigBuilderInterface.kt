package info.nightscout.androidaps.interfaces

interface ConfigBuilderInterface {
    fun storeSettings(from: String)
    fun performPluginSwitch(changedPlugin: PluginBase, enabled: Boolean, type: PluginType)
}