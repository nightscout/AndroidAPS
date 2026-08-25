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
import app.aaps.core.interfaces.di.FeatureMemberInjectors
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ClassKey
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoMap
import dev.zacsweers.metro.MembersInjector
import dev.zacsweers.metro.Provides

/**
 * Member injectors for the DanaR messages - the `@ContributesAndroidInjector` replacement.
 *
 * Third of these, after Diaconn and Medtrum, and the same shape: `MessageBase` is built with `new` by
 * the execution service, so it fills its own fields from this map. The execution services themselves are
 * still `dagger.android`, in `DanaRServicesModule` - Android constructs those, which is a different
 * problem from a message the app news up.
 */
@ContributesTo(AppScope::class)
@BindingContainer
object DanaRMemberInjectors {

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(MessageBase::class)
    fun bindMessageBase(injector: MembersInjector<MessageBase>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(MsgBolusProgress::class)
    fun bindMsgBolusProgress(injector: MembersInjector<MsgBolusProgress>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(MsgBolusStart::class)
    fun bindMsgBolusStart(injector: MembersInjector<MsgBolusStart>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(MsgBolusStartWithSpeed::class)
    fun bindMsgBolusStartWithSpeed(injector: MembersInjector<MsgBolusStartWithSpeed>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(MsgBolusStop::class)
    fun bindMsgBolusStop(injector: MembersInjector<MsgBolusStop>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(MsgCheckValue::class)
    fun bindMsgCheckValue(injector: MembersInjector<MsgCheckValue>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(MsgCheckValueK::class)
    fun bindMsgCheckValueK(injector: MembersInjector<MsgCheckValueK>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(MsgCheckValueV2::class)
    fun bindMsgCheckValueV2(injector: MembersInjector<MsgCheckValueV2>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(MsgError::class)
    fun bindMsgError(injector: MembersInjector<MsgError>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(MsgHistoryAlarm::class)
    fun bindMsgHistoryAlarm(injector: MembersInjector<MsgHistoryAlarm>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(MsgHistoryAll::class)
    fun bindMsgHistoryAll(injector: MembersInjector<MsgHistoryAll>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(MsgHistoryAllDone::class)
    fun bindMsgHistoryAllDone(injector: MembersInjector<MsgHistoryAllDone>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(MsgHistoryBasalHour::class)
    fun bindMsgHistoryBasalHour(injector: MembersInjector<MsgHistoryBasalHour>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(MsgHistoryBolus::class)
    fun bindMsgHistoryBolus(injector: MembersInjector<MsgHistoryBolus>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(MsgHistoryCarbo::class)
    fun bindMsgHistoryCarbo(injector: MembersInjector<MsgHistoryCarbo>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(MsgHistoryDailyInsulin::class)
    fun bindMsgHistoryDailyInsulin(injector: MembersInjector<MsgHistoryDailyInsulin>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(MsgHistoryDone::class)
    fun bindMsgHistoryDone(injector: MembersInjector<MsgHistoryDone>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(MsgHistoryError::class)
    fun bindMsgHistoryError(injector: MembersInjector<MsgHistoryError>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(MsgHistoryEventsV2::class)
    fun bindMsgHistoryEventsV2(injector: MembersInjector<MsgHistoryEventsV2>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(MsgHistoryGlucose::class)
    fun bindMsgHistoryGlucose(injector: MembersInjector<MsgHistoryGlucose>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(MsgHistoryNew::class)
    fun bindMsgHistoryNew(injector: MembersInjector<MsgHistoryNew>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(MsgHistoryNewDone::class)
    fun bindMsgHistoryNewDone(injector: MembersInjector<MsgHistoryNewDone>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(MsgHistoryRefill::class)
    fun bindMsgHistoryRefill(injector: MembersInjector<MsgHistoryRefill>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(MsgHistorySuspend::class)
    fun bindMsgHistorySuspend(injector: MembersInjector<MsgHistorySuspend>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(MsgInitConnStatusBasic::class)
    fun bindMsgInitConnStatusBasic(injector: MembersInjector<MsgInitConnStatusBasic>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(MsgInitConnStatusBasicK::class)
    fun bindMsgInitConnStatusBasicK(injector: MembersInjector<MsgInitConnStatusBasicK>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(MsgInitConnStatusBolus::class)
    fun bindMsgInitConnStatusBolus(injector: MembersInjector<MsgInitConnStatusBolus>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(MsgInitConnStatusBolusK::class)
    fun bindMsgInitConnStatusBolusK(injector: MembersInjector<MsgInitConnStatusBolusK>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(MsgInitConnStatusOption::class)
    fun bindMsgInitConnStatusOption(injector: MembersInjector<MsgInitConnStatusOption>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(MsgInitConnStatusTime::class)
    fun bindMsgInitConnStatusTime(injector: MembersInjector<MsgInitConnStatusTime>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(MsgInitConnStatusTimeK::class)
    fun bindMsgInitConnStatusTimeK(injector: MembersInjector<MsgInitConnStatusTimeK>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(MsgPCCommStart::class)
    fun bindMsgPCCommStart(injector: MembersInjector<MsgPCCommStart>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(MsgPCCommStop::class)
    fun bindMsgPCCommStop(injector: MembersInjector<MsgPCCommStop>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(MsgSetAPSTempBasalStartV2::class)
    fun bindMsgSetAPSTempBasalStartV2(injector: MembersInjector<MsgSetAPSTempBasalStartV2>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(MsgSetActivateBasalProfile::class)
    fun bindMsgSetActivateBasalProfile(injector: MembersInjector<MsgSetActivateBasalProfile>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(MsgSetBasalProfile::class)
    fun bindMsgSetBasalProfile(injector: MembersInjector<MsgSetBasalProfile>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(MsgSetCarbsEntry::class)
    fun bindMsgSetCarbsEntry(injector: MembersInjector<MsgSetCarbsEntry>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(MsgSetExtendedBolusStart::class)
    fun bindMsgSetExtendedBolusStart(injector: MembersInjector<MsgSetExtendedBolusStart>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(MsgSetExtendedBolusStop::class)
    fun bindMsgSetExtendedBolusStop(injector: MembersInjector<MsgSetExtendedBolusStop>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(MsgSetHistoryEntryV2::class)
    fun bindMsgSetHistoryEntryV2(injector: MembersInjector<MsgSetHistoryEntryV2>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(MsgSetSingleBasalProfile::class)
    fun bindMsgSetSingleBasalProfile(injector: MembersInjector<MsgSetSingleBasalProfile>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(MsgSetTempBasalStart::class)
    fun bindMsgSetTempBasalStart(injector: MembersInjector<MsgSetTempBasalStart>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(MsgSetTempBasalStop::class)
    fun bindMsgSetTempBasalStop(injector: MembersInjector<MsgSetTempBasalStop>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(MsgSetTime::class)
    fun bindMsgSetTime(injector: MembersInjector<MsgSetTime>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(MsgSetUserOptions::class)
    fun bindMsgSetUserOptions(injector: MembersInjector<MsgSetUserOptions>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(MsgSettingActiveProfile::class)
    fun bindMsgSettingActiveProfile(injector: MembersInjector<MsgSettingActiveProfile>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(MsgSettingBasal::class)
    fun bindMsgSettingBasal(injector: MembersInjector<MsgSettingBasal>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(MsgSettingBasalProfileAll::class)
    fun bindMsgSettingBasalProfileAll(injector: MembersInjector<MsgSettingBasalProfileAll>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(MsgSettingBasalProfileAllK::class)
    fun bindMsgSettingBasalProfileAllK(injector: MembersInjector<MsgSettingBasalProfileAllK>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(MsgSettingBasal_k::class)
    fun bindMsgSettingBasalK(injector: MembersInjector<MsgSettingBasal_k>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(MsgStatusBasic_k::class)
    fun bindMsgStatusBasicK(injector: MembersInjector<MsgStatusBasic_k>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(MsgStatus_k::class)
    fun bindMsgStatusK(injector: MembersInjector<MsgStatus_k>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(MsgSettingGlucose::class)
    fun bindMsgSettingGlucose(injector: MembersInjector<MsgSettingGlucose>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(MsgSettingMaxValues::class)
    fun bindMsgSettingMaxValues(injector: MembersInjector<MsgSettingMaxValues>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(MsgSettingMeal::class)
    fun bindMsgSettingMeal(injector: MembersInjector<MsgSettingMeal>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(MsgSettingProfileRatios::class)
    fun bindMsgSettingProfileRatios(injector: MembersInjector<MsgSettingProfileRatios>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(MsgSettingProfileRatiosAll::class)
    fun bindMsgSettingProfileRatiosAll(injector: MembersInjector<MsgSettingProfileRatiosAll>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(MsgSettingPumpTime::class)
    fun bindMsgSettingPumpTime(injector: MembersInjector<MsgSettingPumpTime>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(MsgSettingShippingInfo::class)
    fun bindMsgSettingShippingInfo(injector: MembersInjector<MsgSettingShippingInfo>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(MsgSettingUserOptions::class)
    fun bindMsgSettingUserOptions(injector: MembersInjector<MsgSettingUserOptions>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(MsgStatus::class)
    fun bindMsgStatus(injector: MembersInjector<MsgStatus>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(MsgStatusAPSV2::class)
    fun bindMsgStatusAPSV2(injector: MembersInjector<MsgStatusAPSV2>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(MsgStatusBasic::class)
    fun bindMsgStatusBasic(injector: MembersInjector<MsgStatusBasic>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(MsgStatusBolusExtended::class)
    fun bindMsgStatusBolusExtended(injector: MembersInjector<MsgStatusBolusExtended>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(MsgStatusProfile::class)
    fun bindMsgStatusProfile(injector: MembersInjector<MsgStatusProfile>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(MsgStatusTempBasal::class)
    fun bindMsgStatusTempBasal(injector: MembersInjector<MsgStatusTempBasal>): MembersInjector<*> = injector
}
