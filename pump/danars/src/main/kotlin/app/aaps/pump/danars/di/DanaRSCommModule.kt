package app.aaps.pump.danars.di

import app.aaps.pump.danars.comm.DanaRSPacket
import app.aaps.pump.danars.comm.DanaRSPacketAPSBasalSetTemporaryBasal
import app.aaps.pump.danars.comm.DanaRSPacketAPSHistoryEvents
import app.aaps.pump.danars.comm.DanaRSPacketAPSSetEventHistory
import app.aaps.pump.danars.comm.DanaRSPacketBasalGetBasalRate
import app.aaps.pump.danars.comm.DanaRSPacketBasalGetProfileNumber
import app.aaps.pump.danars.comm.DanaRSPacketBasalSetCancelTemporaryBasal
import app.aaps.pump.danars.comm.DanaRSPacketBasalSetProfileBasalRate
import app.aaps.pump.danars.comm.DanaRSPacketBasalSetProfileNumber
import app.aaps.pump.danars.comm.DanaRSPacketBasalSetSuspendOff
import app.aaps.pump.danars.comm.DanaRSPacketBasalSetSuspendOn
import app.aaps.pump.danars.comm.DanaRSPacketBasalSetTemporaryBasal
import app.aaps.pump.danars.comm.DanaRSPacketBolusGet24CIRCFArray
import app.aaps.pump.danars.comm.DanaRSPacketBolusGetBolusOption
import app.aaps.pump.danars.comm.DanaRSPacketBolusGetCIRCFArray
import app.aaps.pump.danars.comm.DanaRSPacketBolusGetCalculationInformation
import app.aaps.pump.danars.comm.DanaRSPacketBolusGetStepBolusInformation
import app.aaps.pump.danars.comm.DanaRSPacketBolusSet24CIRCFArray
import app.aaps.pump.danars.comm.DanaRSPacketBolusSetBolusOption
import app.aaps.pump.danars.comm.DanaRSPacketBolusSetExtendedBolus
import app.aaps.pump.danars.comm.DanaRSPacketBolusSetExtendedBolusCancel
import app.aaps.pump.danars.comm.DanaRSPacketBolusSetStepBolusStart
import app.aaps.pump.danars.comm.DanaRSPacketBolusSetStepBolusStop
import app.aaps.pump.danars.comm.DanaRSPacketEtcKeepConnection
import app.aaps.pump.danars.comm.DanaRSPacketEtcSetHistorySave
import app.aaps.pump.danars.comm.DanaRSPacketGeneralGetPumpCheck
import app.aaps.pump.danars.comm.DanaRSPacketGeneralGetShippingInformation
import app.aaps.pump.danars.comm.DanaRSPacketGeneralGetShippingVersion
import app.aaps.pump.danars.comm.DanaRSPacketGeneralGetUserTimeChangeFlag
import app.aaps.pump.danars.comm.DanaRSPacketGeneralInitialScreenInformation
import app.aaps.pump.danars.comm.DanaRSPacketGeneralSetHistoryUploadMode
import app.aaps.pump.danars.comm.DanaRSPacketGeneralSetUserTimeChangeFlagClear
import app.aaps.pump.danars.comm.DanaRSPacketHistory
import app.aaps.pump.danars.comm.DanaRSPacketHistoryAlarm
import app.aaps.pump.danars.comm.DanaRSPacketHistoryAllHistory
import app.aaps.pump.danars.comm.DanaRSPacketHistoryBasal
import app.aaps.pump.danars.comm.DanaRSPacketHistoryBloodGlucose
import app.aaps.pump.danars.comm.DanaRSPacketHistoryBolus
import app.aaps.pump.danars.comm.DanaRSPacketHistoryCarbohydrate
import app.aaps.pump.danars.comm.DanaRSPacketHistoryDaily
import app.aaps.pump.danars.comm.DanaRSPacketHistoryPrime
import app.aaps.pump.danars.comm.DanaRSPacketHistoryRefill
import app.aaps.pump.danars.comm.DanaRSPacketHistorySuspend
import app.aaps.pump.danars.comm.DanaRSPacketHistoryTemporary
import app.aaps.pump.danars.comm.DanaRSPacketNotifyAlarm
import app.aaps.pump.danars.comm.DanaRSPacketNotifyDeliveryComplete
import app.aaps.pump.danars.comm.DanaRSPacketNotifyDeliveryRateDisplay
import app.aaps.pump.danars.comm.DanaRSPacketNotifyMissedBolusAlarm
import app.aaps.pump.danars.comm.DanaRSPacketOptionGetPumpTime
import app.aaps.pump.danars.comm.DanaRSPacketOptionGetPumpUTCAndTimeZone
import app.aaps.pump.danars.comm.DanaRSPacketOptionGetUserOption
import app.aaps.pump.danars.comm.DanaRSPacketOptionSetPumpTime
import app.aaps.pump.danars.comm.DanaRSPacketOptionSetPumpUTCAndTimeZone
import app.aaps.pump.danars.comm.DanaRSPacketOptionSetUserOption
import app.aaps.pump.danars.comm.DanaRSPacketReviewBolusAvg
import app.aaps.pump.danars.comm.DanaRSPacketReviewGetPumpDecRatio
import dagger.Module
import dagger.android.ContributesAndroidInjector

@Module
@Suppress("unused")
abstract class DanaRSCommModule {

    @ContributesAndroidInjector abstract fun contributesDanaRSPacket(): DanaRSPacket
    @ContributesAndroidInjector abstract fun contributesDanaRSPacketBasalSetCancelTemporaryBasal(): DanaRSPacketBasalSetCancelTemporaryBasal
    @ContributesAndroidInjector abstract fun contributesDanaRSPacketBasalGetBasalRate(): DanaRSPacketBasalGetBasalRate
    @ContributesAndroidInjector abstract fun contributesDanaRSPacketBasalGetProfileNumber(): DanaRSPacketBasalGetProfileNumber
    @ContributesAndroidInjector abstract fun contributesDanaRSPacketBasalSetProfileBasalRate(): DanaRSPacketBasalSetProfileBasalRate
    @ContributesAndroidInjector abstract fun contributesDanaRSPacketBasalSetProfileNumber(): DanaRSPacketBasalSetProfileNumber
    @ContributesAndroidInjector abstract fun contributesDanaRSPacketBasalSetSuspendOff(): DanaRSPacketBasalSetSuspendOff
    @ContributesAndroidInjector abstract fun contributesDanaRSPacketBasalSetSuspendOn(): DanaRSPacketBasalSetSuspendOn
    @ContributesAndroidInjector abstract fun contributesDanaRSPacketBasalSetTemporaryBasal(): DanaRSPacketBasalSetTemporaryBasal
    @ContributesAndroidInjector abstract fun contributesDanaRSPacketBolusGetBolusOption(): DanaRSPacketBolusGetBolusOption
    @ContributesAndroidInjector abstract fun contributesDanaRSPacketBolusGetCalculationInformation(): DanaRSPacketBolusGetCalculationInformation
    @ContributesAndroidInjector abstract fun contributesDanaRSPacketBolusGetCIRCFArray(): DanaRSPacketBolusGetCIRCFArray
    @ContributesAndroidInjector abstract fun contributesDanaRSPacketBolusGetStepBolusInformation(): DanaRSPacketBolusGetStepBolusInformation
    @ContributesAndroidInjector abstract fun contributesDanaRSPacketBolusSetBolusOption(): DanaRSPacketBolusSetBolusOption
    @ContributesAndroidInjector abstract fun contributesDanaRSPacketBolusGet24CIRCFArray(): DanaRSPacketBolusGet24CIRCFArray
    @ContributesAndroidInjector abstract fun contributesDanaRSPacketBolusSet24CIRCFArray(): DanaRSPacketBolusSet24CIRCFArray
    @ContributesAndroidInjector abstract fun contributesDanaRSPacketBolusSetExtendedBolus(): DanaRSPacketBolusSetExtendedBolus
    @ContributesAndroidInjector abstract fun contributesDanaRSPacketBolusSetExtendedBolusCancel(): DanaRSPacketBolusSetExtendedBolusCancel
    @ContributesAndroidInjector abstract fun contributesDanaRSPacketBolusSetStepBolusStart(): DanaRSPacketBolusSetStepBolusStart
    @ContributesAndroidInjector abstract fun contributesDanaRSPacketBolusSetStepBolusStop(): DanaRSPacketBolusSetStepBolusStop
    @ContributesAndroidInjector abstract fun contributesDanaRSPacketEtcKeepConnection(): DanaRSPacketEtcKeepConnection
    @ContributesAndroidInjector abstract fun contributesDanaRSPacketEtcSetHistorySave(): DanaRSPacketEtcSetHistorySave
    @ContributesAndroidInjector abstract fun contributesDanaRSPacketGeneralInitialScreenInformation(): DanaRSPacketGeneralInitialScreenInformation
    @ContributesAndroidInjector abstract fun contributesDanaRSPacketNotifyDeliveryRateDisplay(): DanaRSPacketNotifyDeliveryRateDisplay
    @ContributesAndroidInjector abstract fun contributesDanaRSPacketNotifyAlarm(): DanaRSPacketNotifyAlarm
    @ContributesAndroidInjector abstract fun contributesDanaRSPacketNotifyDeliveryComplete(): DanaRSPacketNotifyDeliveryComplete
    @ContributesAndroidInjector abstract fun contributesDanaRSPacketNotifyMissedBolusAlarm(): DanaRSPacketNotifyMissedBolusAlarm
    @ContributesAndroidInjector abstract fun contributesDanaRSPacketOptionGetPumpTime(): DanaRSPacketOptionGetPumpTime
    @ContributesAndroidInjector abstract fun contributesDanaRSPacketOptionGetUserOption(): DanaRSPacketOptionGetUserOption
    @ContributesAndroidInjector abstract fun contributesDanaRSPacketOptionSetPumpTime(): DanaRSPacketOptionSetPumpTime
    @ContributesAndroidInjector abstract fun contributesDanaRSPacketOptionSetUserOption(): DanaRSPacketOptionSetUserOption
    @ContributesAndroidInjector abstract fun contributesDanaRSPacketHistory(): DanaRSPacketHistory
    @ContributesAndroidInjector abstract fun contributesDanaRSPacketHistoryAlarm(): DanaRSPacketHistoryAlarm
    @ContributesAndroidInjector abstract fun contributesDanaRSPacketHistoryAllHistory(): DanaRSPacketHistoryAllHistory
    @ContributesAndroidInjector abstract fun contributesDanaRSPacketHistoryBasal(): DanaRSPacketHistoryBasal
    @ContributesAndroidInjector abstract fun contributesDanaRSPacketHistoryBloodGlucose(): DanaRSPacketHistoryBloodGlucose
    @ContributesAndroidInjector abstract fun contributesDanaRSPacketHistoryBolus(): DanaRSPacketHistoryBolus
    @ContributesAndroidInjector abstract fun contributesDanaRSPacketReviewBolusAvg(): DanaRSPacketReviewBolusAvg
    @ContributesAndroidInjector abstract fun contributesDanaRSPacketHistoryCarbohydrate(): DanaRSPacketHistoryCarbohydrate
    @ContributesAndroidInjector abstract fun contributesDanaRSPacketHistoryDaily(): DanaRSPacketHistoryDaily
    @ContributesAndroidInjector abstract fun contributesDanaRSPacketGeneralGetPumpCheck(): DanaRSPacketGeneralGetPumpCheck
    @ContributesAndroidInjector abstract fun contributesDanaRSPacketGeneralGetShippingInformation(): DanaRSPacketGeneralGetShippingInformation
    @ContributesAndroidInjector abstract fun contributesDanaRSPacketGeneralGetUserTimeChangeFlag(): DanaRSPacketGeneralGetUserTimeChangeFlag
    @ContributesAndroidInjector abstract fun contributesDanaRSPacketHistoryPrime(): DanaRSPacketHistoryPrime
    @ContributesAndroidInjector abstract fun contributesDanaRSPacketHistoryRefill(): DanaRSPacketHistoryRefill
    @ContributesAndroidInjector abstract fun contributesDanaRSPacketGeneralSetHistoryUploadMode(): DanaRSPacketGeneralSetHistoryUploadMode
    @ContributesAndroidInjector abstract fun contributesDanaRSPacketGeneralSetUserTimeChangeFlagClear(): DanaRSPacketGeneralSetUserTimeChangeFlagClear
    @ContributesAndroidInjector abstract fun contributesDanaRSPacketHistorySuspend(): DanaRSPacketHistorySuspend
    @ContributesAndroidInjector abstract fun contributesDanaRSPacketHistoryTemporary(): DanaRSPacketHistoryTemporary
    @ContributesAndroidInjector abstract fun contributesDanaRSPacketAPSBasalSetTemporaryBasal(): DanaRSPacketAPSBasalSetTemporaryBasal
    @ContributesAndroidInjector abstract fun contributesDanaRSPacketAPSHistoryEvents(): DanaRSPacketAPSHistoryEvents
    @ContributesAndroidInjector abstract fun contributesDanaRSPacketAPSSetEventHistory(): DanaRSPacketAPSSetEventHistory
    @ContributesAndroidInjector abstract fun contributesDanaRSPacketGeneralGetShippingVersion(): DanaRSPacketGeneralGetShippingVersion
    @ContributesAndroidInjector abstract fun contributesDanaRSPacketReviewGetPumpDecRatio(): DanaRSPacketReviewGetPumpDecRatio
    @ContributesAndroidInjector abstract fun contributesDanaRSPacketOptionGetPumpUTCAndTimeZone(): DanaRSPacketOptionGetPumpUTCAndTimeZone
    @ContributesAndroidInjector abstract fun contributesDanaRSPacketOptionSetPumpUTCAndTimeZone(): DanaRSPacketOptionSetPumpUTCAndTimeZone
}