package info.nightscout.androidaps.queue.commands

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.interfaces.ActivePluginProvider
import info.nightscout.androidaps.interfaces.ProfileFunction
import info.nightscout.androidaps.plugins.pump.insight.LocalInsightPlugin
import info.nightscout.androidaps.plugins.pump.omnipod.OmnipodPumpPlugin
import info.nightscout.androidaps.queue.Callback
import javax.inject.Inject

class CommandStartPump(
    injector: HasAndroidInjector,
    callback: Callback?
) : Command(injector, CommandType.START_PUMP, callback) {

    @Inject lateinit var activePlugin: ActivePluginProvider
    @Inject lateinit var profileFunction: ProfileFunction

    override fun execute() {
        val pump = activePlugin.activePump
        if (pump is LocalInsightPlugin) {
            val result = pump.startPump()
            callback?.result(result)?.run()
        } else if (pump is OmnipodPumpPlugin) {
            // When using CommandQueue.setProfile, it refuses to set the profile is the same as the current profile
            // However we need to set the current profile to resume delivery in case the Pod is suspended
            if (profileFunction.getProfile() != null) {
                pump.setNewBasalProfile(profileFunction.getProfile())
            }
        }
    }

    override fun status(): String = "START PUMP"
}