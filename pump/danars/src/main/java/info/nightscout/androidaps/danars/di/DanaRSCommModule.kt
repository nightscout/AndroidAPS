package info.nightscout.androidaps.danars.di

import dagger.Module
import dagger.android.ContributesAndroidInjector
import info.nightscout.androidaps.danars.comm.*

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