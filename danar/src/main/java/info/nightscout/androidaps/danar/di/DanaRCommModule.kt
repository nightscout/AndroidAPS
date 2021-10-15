package info.nightscout.androidaps.danar.di

import dagger.Module
import dagger.android.ContributesAndroidInjector
import info.nightscout.androidaps.danaRKorean.comm.*
import info.nightscout.androidaps.danaRv2.comm.MsgCheckValue_v2
import info.nightscout.androidaps.danaRv2.comm.MsgHistoryEventsV2
import info.nightscout.androidaps.danaRv2.comm.MsgSetAPSTempBasalStart_v2
import info.nightscout.androidaps.danaRv2.comm.MsgSetHistoryEntry_v2
import info.nightscout.androidaps.danaRv2.comm.MsgStatusAPS_v2
import info.nightscout.androidaps.danar.comm.*

@Module
@Suppress("unused")
abstract class DanaRCommModule {

    @ContributesAndroidInjector abstract fun contributesMessageBase(): MessageBase
    @ContributesAndroidInjector abstract fun contributesMsgSetTime(): MsgSetTime
    @ContributesAndroidInjector abstract fun contributesMsgBolusProgress(): MsgBolusProgress
    @ContributesAndroidInjector abstract fun contributesMsgBolusStart(): MsgBolusStart
    @ContributesAndroidInjector abstract fun contributesMsgBolusStartWithSpeed(): MsgBolusStartWithSpeed
    @ContributesAndroidInjector abstract fun contributesMsgBolusStop(): MsgBolusStop
    @ContributesAndroidInjector abstract fun contributesMsgCheckValue(): MsgCheckValue
    @ContributesAndroidInjector abstract fun contributesMsgError(): MsgError
    @ContributesAndroidInjector abstract fun contributesMsgHistoryAll(): MsgHistoryAll
    @ContributesAndroidInjector abstract fun contributesMsgHistoryAllDone(): MsgHistoryAllDone
    @ContributesAndroidInjector abstract fun contributesMsgHistoryDone(): MsgHistoryDone
    @ContributesAndroidInjector abstract fun contributesMsgHistoryNewDone(): MsgHistoryNewDone
    @ContributesAndroidInjector abstract fun contributesMsgInitConnStatusBasic(): MsgInitConnStatusBasic
    @ContributesAndroidInjector abstract fun contributesMsgInitConnStatusBolus(): MsgInitConnStatusBolus
    @ContributesAndroidInjector abstract fun contributesMsgInitConnStatusOption(): MsgInitConnStatusOption
    @ContributesAndroidInjector abstract fun contributesMsgInitConnStatusTime(): MsgInitConnStatusTime
    @ContributesAndroidInjector abstract fun contributesMsgPCCommStart(): MsgPCCommStart
    @ContributesAndroidInjector abstract fun contributesMsgPCCommStop(): MsgPCCommStop
    @ContributesAndroidInjector abstract fun contributesMsgSetActivateBasalProfile(): MsgSetActivateBasalProfile
    @ContributesAndroidInjector abstract fun contributesMsgSetBasalProfile(): MsgSetBasalProfile
    @ContributesAndroidInjector abstract fun contributesMsgSetCarbsEntry(): MsgSetCarbsEntry
    @ContributesAndroidInjector abstract fun contributesMsgSetExtendedBolusStart(): MsgSetExtendedBolusStart
    @ContributesAndroidInjector abstract fun contributesMsgSetExtendedBolusStop(): MsgSetExtendedBolusStop
    @ContributesAndroidInjector abstract fun contributesMsgSetSingleBasalProfile(): MsgSetSingleBasalProfile
    @ContributesAndroidInjector abstract fun contributesMsgSetTempBasalStart(): MsgSetTempBasalStart
    @ContributesAndroidInjector abstract fun contributesMsgSetTempBasalStop(): MsgSetTempBasalStop
    @ContributesAndroidInjector abstract fun contributesMsgSetUserOptions(): MsgSetUserOptions
    @ContributesAndroidInjector abstract fun contributesMsgSettingActiveProfile(): MsgSettingActiveProfile
    @ContributesAndroidInjector abstract fun contributesMsgSettingBasal(): MsgSettingBasal
    @ContributesAndroidInjector abstract fun contributesMsgSettingBasalProfileAll(): MsgSettingBasalProfileAll
    @ContributesAndroidInjector abstract fun contributesMsgSettingGlucose(): MsgSettingGlucose
    @ContributesAndroidInjector abstract fun contributesMsgSettingMaxValues(): MsgSettingMaxValues
    @ContributesAndroidInjector abstract fun contributesMsgSettingMeal(): MsgSettingMeal
    @ContributesAndroidInjector abstract fun contributesMsgSettingProfileRatios(): MsgSettingProfileRatios
    @ContributesAndroidInjector abstract fun contributesMsgSettingProfileRatiosAll(): MsgSettingProfileRatiosAll
    @ContributesAndroidInjector abstract fun contributesMsgSettingPumpTime(): MsgSettingPumpTime
    @ContributesAndroidInjector abstract fun contributesMsgSettingShippingInfo(): MsgSettingShippingInfo
    @ContributesAndroidInjector abstract fun contributesMsgSettingUserOptions(): MsgSettingUserOptions
    @ContributesAndroidInjector abstract fun contributesMsgStatus(): MsgStatus
    @ContributesAndroidInjector abstract fun contributesMsgStatusBasic(): MsgStatusBasic
    @ContributesAndroidInjector abstract fun contributesMsgStatusBolusExtended(): MsgStatusBolusExtended
    @ContributesAndroidInjector abstract fun contributesMsgStatusProfile(): MsgStatusProfile
    @ContributesAndroidInjector abstract fun contributesMsgStatusTempBasal(): MsgStatusTempBasal
    @ContributesAndroidInjector abstract fun contributesMsgHistoryBolus(): MsgHistoryBolus
    @ContributesAndroidInjector abstract fun contributesMsgHistoryDailyInsulin(): MsgHistoryDailyInsulin
    @ContributesAndroidInjector abstract fun contributesMsgHistoryGlucose(): MsgHistoryGlucose
    @ContributesAndroidInjector abstract fun contributesMsgHistoryAlarm(): MsgHistoryAlarm
    @ContributesAndroidInjector abstract fun contributesMsgHistoryError(): MsgHistoryError
    @ContributesAndroidInjector abstract fun contributesMsgHistoryCarbo(): MsgHistoryCarbo
    @ContributesAndroidInjector abstract fun contributesMsgHistoryRefill(): MsgHistoryRefill
    @ContributesAndroidInjector abstract fun contributesMsgHistorySuspend(): MsgHistorySuspend
    @ContributesAndroidInjector abstract fun contributesMsgHistoryBasalHour(): MsgHistoryBasalHour
    @ContributesAndroidInjector abstract fun contributesMsgHistoryNew(): MsgHistoryNew

    @ContributesAndroidInjector abstract fun contributesMsgCheckValue_v2(): MsgCheckValue_v2
    @ContributesAndroidInjector abstract fun contributesMsgHistoryEventsV2(): MsgHistoryEventsV2
    @ContributesAndroidInjector abstract fun contributesMsgSetAPSTempBasalStart_v2(): MsgSetAPSTempBasalStart_v2
    @ContributesAndroidInjector abstract fun contributesMsgSetHistoryEntry_v2(): MsgSetHistoryEntry_v2
    @ContributesAndroidInjector abstract fun contributesMsgStatusAPS_v2(): MsgStatusAPS_v2

    @ContributesAndroidInjector abstract fun contributesMsgCheckValue_k(): MsgCheckValue_k
    @ContributesAndroidInjector abstract fun contributesMsgInitConnStatusBasic_k(): MsgInitConnStatusBasic_k
    @ContributesAndroidInjector abstract fun contributesMsgInitConnStatusBolus_k(): MsgInitConnStatusBolus_k
    @ContributesAndroidInjector abstract fun contributesMsgInitConnStatusTime_k(): MsgInitConnStatusTime_k
    @ContributesAndroidInjector abstract fun contributesMsgSettingBasalProfileAll_k(): MsgSettingBasalProfileAll_k
    @ContributesAndroidInjector abstract fun contributesMsgSettingBasal_k(): MsgSettingBasal_k
    @ContributesAndroidInjector abstract fun contributesMsgStatusBasic_k(): MsgStatusBasic_k
    @ContributesAndroidInjector abstract fun contributesMsgStatus_k(): MsgStatus_k
}