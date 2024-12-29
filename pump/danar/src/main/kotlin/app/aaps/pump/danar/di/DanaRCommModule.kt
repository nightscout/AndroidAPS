package app.aaps.pump.danar.di

import app.aaps.pump.danar.comm.MessageBase
import app.aaps.pump.danar.comm.MsgBolusProgress
import app.aaps.pump.danar.comm.MsgBolusStart
import app.aaps.pump.danar.comm.MsgBolusStartWithSpeed
import app.aaps.pump.danar.comm.MsgBolusStop
import app.aaps.pump.danar.comm.MsgCheckValue
import app.aaps.pump.danar.comm.MsgError
import app.aaps.pump.danar.comm.MsgHistoryAlarm
import app.aaps.pump.danar.comm.MsgHistoryAll
import app.aaps.pump.danar.comm.MsgHistoryAllDone
import app.aaps.pump.danar.comm.MsgHistoryBasalHour
import app.aaps.pump.danar.comm.MsgHistoryBolus
import app.aaps.pump.danar.comm.MsgHistoryCarbo
import app.aaps.pump.danar.comm.MsgHistoryDailyInsulin
import app.aaps.pump.danar.comm.MsgHistoryDone
import app.aaps.pump.danar.comm.MsgHistoryError
import app.aaps.pump.danar.comm.MsgHistoryGlucose
import app.aaps.pump.danar.comm.MsgHistoryNew
import app.aaps.pump.danar.comm.MsgHistoryNewDone
import app.aaps.pump.danar.comm.MsgHistoryRefill
import app.aaps.pump.danar.comm.MsgHistorySuspend
import app.aaps.pump.danar.comm.MsgInitConnStatusBasic
import app.aaps.pump.danar.comm.MsgInitConnStatusBolus
import app.aaps.pump.danar.comm.MsgInitConnStatusOption
import app.aaps.pump.danar.comm.MsgInitConnStatusTime
import app.aaps.pump.danar.comm.MsgPCCommStart
import app.aaps.pump.danar.comm.MsgPCCommStop
import app.aaps.pump.danar.comm.MsgSetActivateBasalProfile
import app.aaps.pump.danar.comm.MsgSetBasalProfile
import app.aaps.pump.danar.comm.MsgSetCarbsEntry
import app.aaps.pump.danar.comm.MsgSetExtendedBolusStart
import app.aaps.pump.danar.comm.MsgSetExtendedBolusStop
import app.aaps.pump.danar.comm.MsgSetSingleBasalProfile
import app.aaps.pump.danar.comm.MsgSetTempBasalStart
import app.aaps.pump.danar.comm.MsgSetTempBasalStop
import app.aaps.pump.danar.comm.MsgSetTime
import app.aaps.pump.danar.comm.MsgSetUserOptions
import app.aaps.pump.danar.comm.MsgSettingActiveProfile
import app.aaps.pump.danar.comm.MsgSettingBasal
import app.aaps.pump.danar.comm.MsgSettingBasalProfileAll
import app.aaps.pump.danar.comm.MsgSettingGlucose
import app.aaps.pump.danar.comm.MsgSettingMaxValues
import app.aaps.pump.danar.comm.MsgSettingMeal
import app.aaps.pump.danar.comm.MsgSettingProfileRatios
import app.aaps.pump.danar.comm.MsgSettingProfileRatiosAll
import app.aaps.pump.danar.comm.MsgSettingPumpTime
import app.aaps.pump.danar.comm.MsgSettingShippingInfo
import app.aaps.pump.danar.comm.MsgSettingUserOptions
import app.aaps.pump.danar.comm.MsgStatus
import app.aaps.pump.danar.comm.MsgStatusBasic
import app.aaps.pump.danar.comm.MsgStatusBolusExtended
import app.aaps.pump.danar.comm.MsgStatusProfile
import app.aaps.pump.danar.comm.MsgStatusTempBasal
import app.aaps.pump.danarkorean.comm.MsgCheckValueK
import app.aaps.pump.danarkorean.comm.MsgInitConnStatusBasicK
import app.aaps.pump.danarkorean.comm.MsgInitConnStatusBolusK
import app.aaps.pump.danarkorean.comm.MsgInitConnStatusTimeK
import app.aaps.pump.danarkorean.comm.MsgSettingBasalProfileAllK
import app.aaps.pump.danarkorean.comm.MsgSettingBasal_k
import app.aaps.pump.danarkorean.comm.MsgStatusBasic_k
import app.aaps.pump.danarkorean.comm.MsgStatus_k
import app.aaps.pump.danarv2.comm.MsgCheckValueV2
import app.aaps.pump.danarv2.comm.MsgHistoryEventsV2
import app.aaps.pump.danarv2.comm.MsgSetAPSTempBasalStartV2
import app.aaps.pump.danarv2.comm.MsgSetHistoryEntryV2
import app.aaps.pump.danarv2.comm.MsgStatusAPSV2
import dagger.Module
import dagger.android.ContributesAndroidInjector

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

    @ContributesAndroidInjector abstract fun contributesMsgCheckValueV2(): MsgCheckValueV2
    @ContributesAndroidInjector abstract fun contributesMsgHistoryEventsV2(): MsgHistoryEventsV2
    @ContributesAndroidInjector abstract fun contributesMsgSetAPSTempBasalStartV2(): MsgSetAPSTempBasalStartV2
    @ContributesAndroidInjector abstract fun contributesMsgSetHistoryEntryV2(): MsgSetHistoryEntryV2
    @ContributesAndroidInjector abstract fun contributesMsgStatusAPSV2(): MsgStatusAPSV2

    @ContributesAndroidInjector abstract fun contributesMsgCheckValueK(): MsgCheckValueK
    @ContributesAndroidInjector abstract fun contributesMsgInitConnStatusBasicK(): MsgInitConnStatusBasicK
    @ContributesAndroidInjector abstract fun contributesMsgInitConnStatusBolusK(): MsgInitConnStatusBolusK
    @ContributesAndroidInjector abstract fun contributesMsgInitConnStatusTimeK(): MsgInitConnStatusTimeK
    @ContributesAndroidInjector abstract fun contributesMsgSettingBasalProfileAllK(): MsgSettingBasalProfileAllK
    @ContributesAndroidInjector abstract fun contributesMsgSettingBasalK(): MsgSettingBasal_k
    @ContributesAndroidInjector abstract fun contributesMsgStatusBasicK(): MsgStatusBasic_k
    @ContributesAndroidInjector abstract fun contributesMsgStatusK(): MsgStatus_k
}