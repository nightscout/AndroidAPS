package info.nightscout.pump.danars.di

import dagger.Module
import dagger.android.ContributesAndroidInjector
import info.nightscout.pump.danars.comm.DanaRSPacket
import info.nightscout.pump.danars.comm.DanaRSPacketAPSBasalSetTemporaryBasal
import info.nightscout.pump.danars.comm.DanaRSPacketAPSHistoryEvents
import info.nightscout.pump.danars.comm.DanaRSPacketAPSSetEventHistory
import info.nightscout.pump.danars.comm.DanaRSPacketBasalGetBasalRate
import info.nightscout.pump.danars.comm.DanaRSPacketBasalGetProfileNumber
import info.nightscout.pump.danars.comm.DanaRSPacketBasalSetCancelTemporaryBasal
import info.nightscout.pump.danars.comm.DanaRSPacketBasalSetProfileBasalRate
import info.nightscout.pump.danars.comm.DanaRSPacketBasalSetProfileNumber
import info.nightscout.pump.danars.comm.DanaRSPacketBasalSetSuspendOff
import info.nightscout.pump.danars.comm.DanaRSPacketBasalSetSuspendOn
import info.nightscout.pump.danars.comm.DanaRSPacketBasalSetTemporaryBasal
import info.nightscout.pump.danars.comm.DanaRSPacketBolusGet24CIRCFArray
import info.nightscout.pump.danars.comm.DanaRSPacketBolusGetBolusOption
import info.nightscout.pump.danars.comm.DanaRSPacketBolusGetCIRCFArray
import info.nightscout.pump.danars.comm.DanaRSPacketBolusGetCalculationInformation
import info.nightscout.pump.danars.comm.DanaRSPacketBolusGetStepBolusInformation
import info.nightscout.pump.danars.comm.DanaRSPacketBolusSet24CIRCFArray
import info.nightscout.pump.danars.comm.DanaRSPacketBolusSetBolusOption
import info.nightscout.pump.danars.comm.DanaRSPacketBolusSetExtendedBolus
import info.nightscout.pump.danars.comm.DanaRSPacketBolusSetExtendedBolusCancel
import info.nightscout.pump.danars.comm.DanaRSPacketBolusSetStepBolusStart
import info.nightscout.pump.danars.comm.DanaRSPacketBolusSetStepBolusStop
import info.nightscout.pump.danars.comm.DanaRSPacketEtcKeepConnection
import info.nightscout.pump.danars.comm.DanaRSPacketEtcSetHistorySave
import info.nightscout.pump.danars.comm.DanaRSPacketGeneralGetPumpCheck
import info.nightscout.pump.danars.comm.DanaRSPacketGeneralGetShippingInformation
import info.nightscout.pump.danars.comm.DanaRSPacketGeneralGetShippingVersion
import info.nightscout.pump.danars.comm.DanaRSPacketGeneralGetUserTimeChangeFlag
import info.nightscout.pump.danars.comm.DanaRSPacketGeneralInitialScreenInformation
import info.nightscout.pump.danars.comm.DanaRSPacketGeneralSetHistoryUploadMode
import info.nightscout.pump.danars.comm.DanaRSPacketGeneralSetUserTimeChangeFlagClear
import info.nightscout.pump.danars.comm.DanaRSPacketHistory
import info.nightscout.pump.danars.comm.DanaRSPacketHistoryAlarm
import info.nightscout.pump.danars.comm.DanaRSPacketHistoryAllHistory
import info.nightscout.pump.danars.comm.DanaRSPacketHistoryBasal
import info.nightscout.pump.danars.comm.DanaRSPacketHistoryBloodGlucose
import info.nightscout.pump.danars.comm.DanaRSPacketHistoryBolus
import info.nightscout.pump.danars.comm.DanaRSPacketHistoryCarbohydrate
import info.nightscout.pump.danars.comm.DanaRSPacketHistoryDaily
import info.nightscout.pump.danars.comm.DanaRSPacketHistoryPrime
import info.nightscout.pump.danars.comm.DanaRSPacketHistoryRefill
import info.nightscout.pump.danars.comm.DanaRSPacketHistorySuspend
import info.nightscout.pump.danars.comm.DanaRSPacketHistoryTemporary
import info.nightscout.pump.danars.comm.DanaRSPacketNotifyAlarm
import info.nightscout.pump.danars.comm.DanaRSPacketNotifyDeliveryComplete
import info.nightscout.pump.danars.comm.DanaRSPacketNotifyDeliveryRateDisplay
import info.nightscout.pump.danars.comm.DanaRSPacketNotifyMissedBolusAlarm
import info.nightscout.pump.danars.comm.DanaRSPacketOptionGetPumpTime
import info.nightscout.pump.danars.comm.DanaRSPacketOptionGetPumpUTCAndTimeZone
import info.nightscout.pump.danars.comm.DanaRSPacketOptionGetUserOption
import info.nightscout.pump.danars.comm.DanaRSPacketOptionSetPumpTime
import info.nightscout.pump.danars.comm.DanaRSPacketOptionSetPumpUTCAndTimeZone
import info.nightscout.pump.danars.comm.DanaRSPacketOptionSetUserOption
import info.nightscout.pump.danars.comm.DanaRSPacketReviewBolusAvg
import info.nightscout.pump.danars.comm.DanaRSPacketReviewGetPumpDecRatio

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