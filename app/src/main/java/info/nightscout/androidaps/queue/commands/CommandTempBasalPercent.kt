package info.nightscout.androidaps.queue.commands

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.data.Profile
import info.nightscout.androidaps.interfaces.ActivePluginProvider
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.queue.Callback
import javax.inject.Inject

class CommandTempBasalPercent(
    injector: HasAndroidInjector,
    private val percent: Int,
    private val durationInMinutes: Int,
    private val enforceNew: Boolean,
    private val profile: Profile,
    callback: Callback?
) : Command(injector, CommandType.TEMPBASAL, callback) {

    @Inject lateinit var activePlugin: ActivePluginProvider

    override fun execute() {
        val r = activePlugin.activePump.setTempBasalPercent(percent, durationInMinutes, profile, enforceNew)
        aapsLogger.debug(LTag.PUMPQUEUE, "Result percent: $percent durationInMinutes: $durationInMinutes success: ${r.success} enacted: ${r.enacted}")
        callback?.result(r)?.run()
    }

    override fun status(): String = "TEMP BASAL $percent% $durationInMinutes min"
}