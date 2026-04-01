package app.aaps.implementation.queue.commands

import app.aaps.core.data.time.T
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.pump.BolusProgressData
import app.aaps.core.interfaces.pump.DetailedBolusInfo
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.queue.Callback
import app.aaps.core.interfaces.queue.Command
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.interfaces.Preferences
import dagger.android.HasAndroidInjector
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Provider

class CommandSMBBolus(
    injector: HasAndroidInjector,
    private val detailedBolusInfo: DetailedBolusInfo,
    override val callback: Callback?,
) : Command {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var persistenceLayer: PersistenceLayer
    @Inject lateinit var preferences: Preferences

    @Inject lateinit var pumpEnactResultProvider: Provider<PumpEnactResult>

    init {
        injector.androidInjector().inject(this)
    }

    override val commandType: Command.CommandType = Command.CommandType.SMB_BOLUS

    override fun execute() {
        val r: PumpEnactResult
        val lastBolusTime = runBlocking { persistenceLayer.getNewestBolus() }?.timestamp ?: 0L
        aapsLogger.debug(LTag.PUMPQUEUE, "Last bolus: $lastBolusTime ${dateUtil.dateAndTimeAndSecondsString(lastBolusTime)}")
        if (lastBolusTime != 0L && lastBolusTime + T.mins(preferences.get(IntKey.ApsMaxSmbFrequency).toLong()).msecs() > dateUtil.now()) {
            aapsLogger.debug(LTag.APS, "SMB requested but still in ${preferences.get(IntKey.ApsMaxSmbFrequency)} min interval")
            r = pumpEnactResultProvider.get().enacted(false).success(false).comment("SMB requested but still in ${preferences.get(IntKey.ApsMaxSmbFrequency)} min interval")
        } else if (detailedBolusInfo.deliverAtTheLatest != 0L && detailedBolusInfo.deliverAtTheLatest + T.mins(1).msecs() > System.currentTimeMillis()) {
            r = activePlugin.activePump.deliverTreatment(detailedBolusInfo)
        } else {
            r = pumpEnactResultProvider.get().enacted(false).success(false).comment("SMB request too old")
            aapsLogger.debug(LTag.PUMPQUEUE, "SMB bolus canceled. deliverAt: " + dateUtil.dateAndTimeString(detailedBolusInfo.deliverAtTheLatest))
        }
        aapsLogger.debug(LTag.PUMPQUEUE, "Result success: ${r.success} enacted: ${r.enacted}")
        callback?.result(r)?.run()
        BolusProgressData.bolusEnded = true
    }

    override fun status(): String = rh.gs(app.aaps.core.ui.R.string.smb_bolus_u, detailedBolusInfo.insulin)

    override fun log(): String = "SMB BOLUS ${rh.gs(app.aaps.core.ui.R.string.format_insulin_units, detailedBolusInfo.insulin)}"
    override fun cancel() {
        aapsLogger.debug(LTag.PUMPQUEUE, "Result cancel")
        callback?.result(pumpEnactResultProvider.get().success(false).comment(app.aaps.core.ui.R.string.connectiontimedout))?.run()
    }
}