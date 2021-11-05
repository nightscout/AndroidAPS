package info.nightscout.androidaps.interfaces

import android.content.Context

interface ConfigBuilder {
    fun initialize()
    fun startGlunovoService(context: Context)
    fun storeSettings(from: String)
    fun performPluginSwitch(changedPlugin: PluginBase, enabled: Boolean, type: PluginType)
}