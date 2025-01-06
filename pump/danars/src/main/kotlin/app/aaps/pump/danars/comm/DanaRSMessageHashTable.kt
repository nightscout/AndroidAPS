package app.aaps.pump.danars.comm

import dagger.android.HasAndroidInjector
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DanaRSMessageHashTable @Inject constructor(
    val injector: HasAndroidInjector
) {

    var messages: HashMap<Int, DanaRSPacket> = HashMap()

    fun put(message: DanaRSPacket) {
        messages[message.command] = message
    }

    fun findMessage(command: Int): DanaRSPacket {
        return messages[command] ?: DanaRSPacket(injector)
    }

    init {
        put(DanaRSPacketBasalSetCancelTemporaryBasal(injector))
        put(DanaRSPacketBasalGetBasalRate(injector))
        put(DanaRSPacketBasalGetProfileNumber(injector))
        put(DanaRSPacketBasalSetProfileBasalRate(injector, 0, arrayOf()))
        put(DanaRSPacketBasalSetProfileNumber(injector))
        put(DanaRSPacketBasalSetSuspendOff(injector))
        put(DanaRSPacketBasalSetSuspendOn(injector))
        put(DanaRSPacketBasalSetTemporaryBasal(injector))
        put(DanaRSPacketBolusGetBolusOption(injector))
        put(DanaRSPacketBolusGetCalculationInformation(injector))
        put(DanaRSPacketBolusGetCIRCFArray(injector))
        put(DanaRSPacketBolusGetStepBolusInformation(injector))
        put(DanaRSPacketBolusSetBolusOption(injector))
        put(DanaRSPacketBolusSet24CIRCFArray(injector, null))
        put(DanaRSPacketBolusGet24CIRCFArray(injector))
        put(DanaRSPacketBolusSetExtendedBolus(injector))
        put(DanaRSPacketBolusSetExtendedBolusCancel(injector))
        put(DanaRSPacketBolusSetStepBolusStart(injector))
        put(DanaRSPacketBolusSetStepBolusStop(injector))
        put(DanaRSPacketEtcKeepConnection(injector))
        put(DanaRSPacketEtcSetHistorySave(injector))
        put(DanaRSPacketGeneralInitialScreenInformation(injector))
        put(DanaRSPacketNotifyAlarm(injector))
        put(DanaRSPacketNotifyDeliveryComplete(injector))
        put(DanaRSPacketNotifyDeliveryRateDisplay(injector))
        put(DanaRSPacketNotifyMissedBolusAlarm(injector))
        put(DanaRSPacketOptionGetPumpTime(injector))
        put(DanaRSPacketOptionGetPumpUTCAndTimeZone(injector))
        put(DanaRSPacketOptionGetUserOption(injector))
        put(DanaRSPacketOptionSetPumpTime(injector))
        put(DanaRSPacketOptionSetPumpUTCAndTimeZone(injector))
        put(DanaRSPacketOptionSetUserOption(injector))
        //put(new DanaRS_Packet_History_(injector));
        put(DanaRSPacketHistoryAlarm(injector))
        put(DanaRSPacketHistoryAllHistory(injector))
        put(DanaRSPacketHistoryBasal(injector))
        put(DanaRSPacketHistoryBloodGlucose(injector))
        put(DanaRSPacketHistoryBolus(injector))
        put(DanaRSPacketReviewBolusAvg(injector))
        put(DanaRSPacketHistoryCarbohydrate(injector))
        put(DanaRSPacketHistoryDaily(injector))
        put(DanaRSPacketHistoryPrime(injector))
        put(DanaRSPacketHistoryRefill(injector))
        put(DanaRSPacketHistorySuspend(injector))
        put(DanaRSPacketHistoryTemporary(injector))
        put(DanaRSPacketGeneralGetPumpCheck(injector))
        put(DanaRSPacketGeneralGetShippingInformation(injector))
        put(DanaRSPacketGeneralGetUserTimeChangeFlag(injector))
        put(DanaRSPacketGeneralSetHistoryUploadMode(injector))
        put(DanaRSPacketGeneralSetUserTimeChangeFlagClear(injector))
        // APS
        put(DanaRSPacketAPSBasalSetTemporaryBasal(injector, 0))
        put(DanaRSPacketAPSHistoryEvents(injector, 0))
        put(DanaRSPacketAPSSetEventHistory(injector, 0, 0, 0, 0))
        // v3
        put(DanaRSPacketGeneralGetShippingVersion(injector))
        put(DanaRSPacketReviewGetPumpDecRatio(injector))
    }
}