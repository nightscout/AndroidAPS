package info.nightscout.androidaps.queue.commands

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.data.Profile
import info.nightscout.androidaps.interfaces.ActivePluginProvider
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.queue.Callback
import javax.inject.Inject

class CommandTempBasalAbsolute(
    injector: HasAndroidInjector,
    private val absoluteRate: Double,
    private val durationInMinutes: Int,
    private val enforceNew: Boolean,
    private val profile: Profile,
    callback: Callback?
) : Command(injector, CommandType.TEMPBASAL, callback) {

    @Inject lateinit var activePlugin: ActivePluginProvider

    override fun execute() {
        val r = activePlugin.activePump.setTempBasalAbsolute(absoluteRate, durationInMinutes, profile, enforceNew)
        aapsLogger.debug(LTag.PUMPQUEUE, "Result rate: $absoluteRate durationInMinutes: $durationInMinutes success: ${r.success} enacted: ${r.enacted}")
        callback?.result(r)?.run()
    }

    override fun status(): String = "TEMP BASAL $absoluteRate U/h $durationInMinutes min"
}