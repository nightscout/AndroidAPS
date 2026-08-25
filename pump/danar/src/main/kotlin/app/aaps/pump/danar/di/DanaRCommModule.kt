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
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
@Suppress("unused")
abstract class DanaRCommModule {

}