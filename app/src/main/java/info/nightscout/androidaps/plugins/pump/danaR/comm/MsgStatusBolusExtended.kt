package info.nightscout.androidaps.plugins.pump.danaR.comm

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.db.ExtendedBolus
import info.nightscout.androidaps.db.Source
import info.nightscout.androidaps.interfaces.ActivePluginProvider
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.pump.danaR.DanaRPump
import info.nightscout.androidaps.utils.DateUtil
import kotlin.math.ceil

class MsgStatusBolusExtended(
    private val injector: HasAndroidInjector,
    private val aapsLogger: AAPSLogger,
    private val danaRPump: DanaRPump,
    private val activePlugin: ActivePluginProvider,
    private val dateUtil: DateUtil
) : MessageBase() {

    init {
        SetCommand(0x0207)
        aapsLogger.debug(LTag.PUMPCOMM, "New message")
    }

    override fun handleMessage(bytes: ByteArray) {
        val isExtendedInProgress = intFromBuff(bytes, 0, 1) == 1
        val extendedBolusHalfHours = intFromBuff(bytes, 1, 1)
        val extendedBolusMinutes = extendedBolusHalfHours * 30
        val extendedBolusAmount = intFromBuff(bytes, 2, 2) / 100.0
        val extendedBolusSoFarInSecs = intFromBuff(bytes, 4, 3)
        // This is available only on korean, but not needed now
//        int extendedBolusDeliveryPulse = intFromBuff(bytes, 7, 2);
//        int isEasyUIUserSleep = intFromBuff(bytes, 9, 1);
        val extendedBolusSoFarInMinutes = extendedBolusSoFarInSecs / 60
        val extendedBolusAbsoluteRate = if (isExtendedInProgress) extendedBolusAmount / extendedBolusMinutes * 60 else 0.0
        val extendedBolusStart = if (isExtendedInProgress) getDateFromSecAgo(extendedBolusSoFarInSecs) else 0
        val extendedBolusRemainingMinutes = extendedBolusMinutes - extendedBolusSoFarInMinutes
        danaRPump.isExtendedInProgress = isExtendedInProgress
        danaRPump.extendedBolusMinutes = extendedBolusMinutes
        danaRPump.extendedBolusAmount = extendedBolusAmount
        danaRPump.extendedBolusSoFarInMinutes = extendedBolusSoFarInMinutes
        danaRPump.extendedBolusAbsoluteRate = extendedBolusAbsoluteRate
        danaRPump.extendedBolusStart = extendedBolusStart
        danaRPump.extendedBolusRemainingMinutes = extendedBolusRemainingMinutes
        updateExtendedBolusInDB()
        aapsLogger.debug(LTag.PUMPCOMM, "Is extended bolus running: $isExtendedInProgress")
        aapsLogger.debug(LTag.PUMPCOMM, "Extended bolus min: $extendedBolusMinutes")
        aapsLogger.debug(LTag.PUMPCOMM, "Extended bolus amount: $extendedBolusAmount")
        aapsLogger.debug(LTag.PUMPCOMM, "Extended bolus so far in minutes: $extendedBolusSoFarInMinutes")
        aapsLogger.debug(LTag.PUMPCOMM, "Extended bolus absolute rate: $extendedBolusAbsoluteRate")
        aapsLogger.debug(LTag.PUMPCOMM, "Extended bolus start: " + dateUtil.dateAndTimeString(extendedBolusStart))
        aapsLogger.debug(LTag.PUMPCOMM, "Extended bolus remaining minutes: $extendedBolusRemainingMinutes")
    }

    private fun getDateFromSecAgo(tempBasalAgoSecs: Int): Long {
        return (ceil(System.currentTimeMillis() / 1000.0) - tempBasalAgoSecs).toLong() * 1000
    }

    private fun updateExtendedBolusInDB() {
        val now = System.currentTimeMillis()
        val extendedBolus = activePlugin.activeTreatments.getExtendedBolusFromHistory(System.currentTimeMillis())
        if (extendedBolus != null) {
            if (danaRPump.isExtendedInProgress) {
                if (extendedBolus.absoluteRate() != danaRPump.extendedBolusAbsoluteRate) { // Close current extended
                    val exStop = ExtendedBolus(injector, danaRPump.extendedBolusStart - 1000)
                    exStop.source = Source.USER
                    activePlugin.activeTreatments.addToHistoryExtendedBolus(exStop)
                    // Create new
                    val newExtended = ExtendedBolus(injector)
                        .date(danaRPump.extendedBolusStart)
                        .insulin(danaRPump.extendedBolusAmount)
                        .durationInMinutes(danaRPump.extendedBolusMinutes)
                        .source(Source.USER)
                    activePlugin.activeTreatments.addToHistoryExtendedBolus(newExtended)
                }
            } else {
                // Close current temp basal
                val exStop = ExtendedBolus(injector, now)
                    .source(Source.USER)
                activePlugin.activeTreatments.addToHistoryExtendedBolus(exStop)
            }
        } else {
            if (danaRPump.isExtendedInProgress) { // Create new
                val newExtended = ExtendedBolus(injector)
                    .date(danaRPump.extendedBolusStart)
                    .insulin(danaRPump.extendedBolusAmount)
                    .durationInMinutes(danaRPump.extendedBolusMinutes)
                    .source(Source.USER)
                activePlugin.activeTreatments.addToHistoryExtendedBolus(newExtended)
            }
        }
    }
}