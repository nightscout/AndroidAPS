package app.aaps.pump.diaconn.di

import dagger.Module
import dagger.android.ContributesAndroidInjector
import app.aaps.pump.diaconn.packet.AppCancelSettingPacket
import app.aaps.pump.diaconn.packet.AppCancelSettingResponsePacket
import app.aaps.pump.diaconn.packet.AppConfirmSettingPacket
import app.aaps.pump.diaconn.packet.AppConfirmSettingResponsePacket
import app.aaps.pump.diaconn.packet.BasalLimitInquirePacket
import app.aaps.pump.diaconn.packet.BasalLimitInquireResponsePacket
import app.aaps.pump.diaconn.packet.BasalPauseReportPacket
import app.aaps.pump.diaconn.packet.BasalPauseSettingPacket
import app.aaps.pump.diaconn.packet.BasalPauseSettingResponsePacket
import app.aaps.pump.diaconn.packet.BasalSettingPacket
import app.aaps.pump.diaconn.packet.BasalSettingReportPacket
import app.aaps.pump.diaconn.packet.BasalSettingResponsePacket
import app.aaps.pump.diaconn.packet.BatteryWarningReportPacket
import app.aaps.pump.diaconn.packet.BigAPSMainInfoInquirePacket
import app.aaps.pump.diaconn.packet.BigAPSMainInfoInquireResponsePacket
import app.aaps.pump.diaconn.packet.BigLogInquirePacket
import app.aaps.pump.diaconn.packet.BigLogInquireResponsePacket
import app.aaps.pump.diaconn.packet.BigMainInfoInquirePacket
import app.aaps.pump.diaconn.packet.BigMainInfoInquireResponsePacket
import app.aaps.pump.diaconn.packet.BolusSpeedInquirePacket
import app.aaps.pump.diaconn.packet.BolusSpeedInquireResponsePacket
import app.aaps.pump.diaconn.packet.BolusSpeedSettingPacket
import app.aaps.pump.diaconn.packet.BolusSpeedSettingReportPacket
import app.aaps.pump.diaconn.packet.BolusSpeedSettingResponsePacket
import app.aaps.pump.diaconn.packet.ConfirmReportPacket
import app.aaps.pump.diaconn.packet.DiaconnG8Packet
import app.aaps.pump.diaconn.packet.DisplayTimeInquirePacket
import app.aaps.pump.diaconn.packet.DisplayTimeInquireResponsePacket
import app.aaps.pump.diaconn.packet.DisplayTimeoutSettingPacket
import app.aaps.pump.diaconn.packet.DisplayTimeoutSettingResponsePacket
import app.aaps.pump.diaconn.packet.IncarnationInquirePacket
import app.aaps.pump.diaconn.packet.IncarnationInquireResponsePacket
import app.aaps.pump.diaconn.packet.InjectionBasalReportPacket
import app.aaps.pump.diaconn.packet.InjectionBasalSettingPacket
import app.aaps.pump.diaconn.packet.InjectionBasalSettingResponsePacket
import app.aaps.pump.diaconn.packet.InjectionBlockReportPacket
import app.aaps.pump.diaconn.packet.InjectionCancelSettingPacket
import app.aaps.pump.diaconn.packet.InjectionCancelSettingResponsePacket
import app.aaps.pump.diaconn.packet.InjectionExtendedBolusResultReportPacket
import app.aaps.pump.diaconn.packet.InjectionExtendedBolusSettingPacket
import app.aaps.pump.diaconn.packet.InjectionExtendedBolusSettingResponsePacket
import app.aaps.pump.diaconn.packet.InjectionProgressReportPacket
import app.aaps.pump.diaconn.packet.InjectionSnackInquirePacket
import app.aaps.pump.diaconn.packet.InjectionSnackInquireResponsePacket
import app.aaps.pump.diaconn.packet.InjectionSnackResultReportPacket
import app.aaps.pump.diaconn.packet.InjectionSnackSettingPacket
import app.aaps.pump.diaconn.packet.InjectionSnackSettingResponsePacket
import app.aaps.pump.diaconn.packet.InsulinLackReportPacket
import app.aaps.pump.diaconn.packet.LanguageInquirePacket
import app.aaps.pump.diaconn.packet.LanguageInquireResponsePacket
import app.aaps.pump.diaconn.packet.LanguageSettingPacket
import app.aaps.pump.diaconn.packet.LanguageSettingResponsePacket
import app.aaps.pump.diaconn.packet.LogStatusInquirePacket
import app.aaps.pump.diaconn.packet.LogStatusInquireResponsePacket
import app.aaps.pump.diaconn.packet.RejectReportPacket
import app.aaps.pump.diaconn.packet.SerialNumInquirePacket
import app.aaps.pump.diaconn.packet.SerialNumInquireResponsePacket
import app.aaps.pump.diaconn.packet.SnackLimitInquirePacket
import app.aaps.pump.diaconn.packet.SnackLimitInquireResponsePacket
import app.aaps.pump.diaconn.packet.SoundInquirePacket
import app.aaps.pump.diaconn.packet.SoundInquireResponsePacket
import app.aaps.pump.diaconn.packet.SoundSettingPacket
import app.aaps.pump.diaconn.packet.SoundSettingResponsePacket
import app.aaps.pump.diaconn.packet.TempBasalInquirePacket
import app.aaps.pump.diaconn.packet.TempBasalInquireResponsePacket
import app.aaps.pump.diaconn.packet.TempBasalReportPacket
import app.aaps.pump.diaconn.packet.TempBasalSettingPacket
import app.aaps.pump.diaconn.packet.TempBasalSettingResponsePacket
import app.aaps.pump.diaconn.packet.TimeInquirePacket
import app.aaps.pump.diaconn.packet.TimeInquireResponsePacket
import app.aaps.pump.diaconn.packet.TimeReportPacket
import app.aaps.pump.diaconn.packet.TimeSettingPacket
import app.aaps.pump.diaconn.packet.TimeSettingResponsePacket

@Module
@Suppress("unused", "SpellCheckingInspection")
interface DiaconnG8PacketModule {
    @ContributesAndroidInjector fun contributesDiaconnG8Packet(): DiaconnG8Packet
    @ContributesAndroidInjector fun contributesAppCancelSettingPacket(): AppCancelSettingPacket
    @ContributesAndroidInjector fun contributesAppCancelSettingResponsePacket(): AppCancelSettingResponsePacket
    @ContributesAndroidInjector fun contributesAppConfirmSettingPacket(): AppConfirmSettingPacket
    @ContributesAndroidInjector fun contributesAppConfirmSettingResponsePacket(): AppConfirmSettingResponsePacket
    @ContributesAndroidInjector fun contributesSneckLimitInquirePacket(): SnackLimitInquirePacket
    @ContributesAndroidInjector fun contributesBasalLimitInquirePacket(): BasalLimitInquirePacket
    @ContributesAndroidInjector fun contributesSneckLimitInquireResponsePacket(): SnackLimitInquireResponsePacket
    @ContributesAndroidInjector fun contributesBasalLimitInquireResponsePacket(): BasalLimitInquireResponsePacket
    @ContributesAndroidInjector fun contributesBasalPauseReportPacket(): BasalPauseReportPacket
    @ContributesAndroidInjector fun contributesBasalPauseSettingPacket(): BasalPauseSettingPacket
    @ContributesAndroidInjector fun contributesBasalPauseSettingResponsePacket(): BasalPauseSettingResponsePacket
    @ContributesAndroidInjector fun contributesBasalSettingPacket(): BasalSettingPacket
    @ContributesAndroidInjector fun contributesBasalSettingReportPacket(): BasalSettingReportPacket
    @ContributesAndroidInjector fun contributesBasalSettingResponsePacket(): BasalSettingResponsePacket
    @ContributesAndroidInjector fun contributesBigMainInfoInquirePacket(): BigMainInfoInquirePacket
    @ContributesAndroidInjector fun contributesBigMainInfoInquireResponsePacket(): BigMainInfoInquireResponsePacket
    @ContributesAndroidInjector fun contributesBigLogInquirePacket(): BigLogInquirePacket
    @ContributesAndroidInjector fun contributesBigLogInquireResponsePacket(): BigLogInquireResponsePacket
    @ContributesAndroidInjector fun contributesConfirmReportPacket(): ConfirmReportPacket
    @ContributesAndroidInjector fun contributesInjectionBasalSettingPacket(): InjectionBasalSettingPacket
    @ContributesAndroidInjector fun contributesInjectionBasalSettingResponsePacket(): InjectionBasalSettingResponsePacket
    @ContributesAndroidInjector fun contributesInjectionSnackResultReportPacket(): InjectionSnackResultReportPacket
    @ContributesAndroidInjector fun contributesInjectionSnactSettingPacket(): InjectionSnackSettingPacket
    @ContributesAndroidInjector fun contributesInjectionSnackSettingResponsePacket(): InjectionSnackSettingResponsePacket
    @ContributesAndroidInjector fun contributesInjectionExtendedBolusResultReportPacket(): InjectionExtendedBolusResultReportPacket
    @ContributesAndroidInjector fun contributesInjectionExtendedBolusSettingPacket(): InjectionExtendedBolusSettingPacket
    @ContributesAndroidInjector fun contributesInjectionExtendedBolusSettingResponsePacket(): InjectionExtendedBolusSettingResponsePacket
    @ContributesAndroidInjector fun contributesInjectionBasalReportPacket(): InjectionBasalReportPacket
    @ContributesAndroidInjector fun contributesInjectionSnackInquirePacket(): InjectionSnackInquirePacket
    @ContributesAndroidInjector fun contributesInjectionSnackInquireResponsePacket(): InjectionSnackInquireResponsePacket
    @ContributesAndroidInjector fun contributesRejectReportPacket(): RejectReportPacket
    @ContributesAndroidInjector fun contributesTempBasalReportPacket(): TempBasalReportPacket
    @ContributesAndroidInjector fun contributesTempBasalSettingPacket(): TempBasalSettingPacket
    @ContributesAndroidInjector fun contributesTempBasalSettingResponsePacket(): TempBasalSettingResponsePacket
    @ContributesAndroidInjector fun contributesTempBasalInquirePacket(): TempBasalInquirePacket
    @ContributesAndroidInjector fun contributesTempBasalInquireResponsePacket(): TempBasalInquireResponsePacket
    @ContributesAndroidInjector fun contributesTimeInquirePacket(): TimeInquirePacket
    @ContributesAndroidInjector fun contributesTimeInquireResponsePacket(): TimeInquireResponsePacket
    @ContributesAndroidInjector fun contributesTimeReportPacket(): TimeReportPacket
    @ContributesAndroidInjector fun contributesTimeSettingPacket(): TimeSettingPacket
    @ContributesAndroidInjector fun contributesTimeSettingResponsePacket(): TimeSettingResponsePacket
    @ContributesAndroidInjector fun contributesLogStatusInquirePacket(): LogStatusInquirePacket
    @ContributesAndroidInjector fun contributesLogStatusInquireResponsePacket(): LogStatusInquireResponsePacket
    @ContributesAndroidInjector fun contributesInjectionCancelSettingPacket(): InjectionCancelSettingPacket
    @ContributesAndroidInjector fun contributesInjectionCancelSettingResponsePacket(): InjectionCancelSettingResponsePacket
    @ContributesAndroidInjector fun contributesSoundSettingPacket(): SoundSettingPacket
    @ContributesAndroidInjector fun contributesSoundSettingResponsePacket(): SoundSettingResponsePacket
    @ContributesAndroidInjector fun contributesDisplayTimeoutSettingPacket(): DisplayTimeoutSettingPacket
    @ContributesAndroidInjector fun contributesDisplayTimeoutSettingResponsePacket(): DisplayTimeoutSettingResponsePacket
    @ContributesAndroidInjector fun contributesLanguageSettingPacket(): LanguageSettingPacket
    @ContributesAndroidInjector fun contributesLanguageSettingResponsePacket(): LanguageSettingResponsePacket
    @ContributesAndroidInjector fun contributesInjectionBlockReportPacket(): InjectionBlockReportPacket
    @ContributesAndroidInjector fun contributesBatteryWarningReportPacket(): BatteryWarningReportPacket
    @ContributesAndroidInjector fun contributesInsulinLackReportPacket(): InsulinLackReportPacket
    @ContributesAndroidInjector fun contributesIncarnationInquirePacket(): IncarnationInquirePacket
    @ContributesAndroidInjector fun contributesIncarnationInquireResponsePacket(): IncarnationInquireResponsePacket
    @ContributesAndroidInjector fun contributesBolusSpeedSettingPacket(): BolusSpeedSettingPacket
    @ContributesAndroidInjector fun contributesBolusSpeedSettingResponsePacket(): BolusSpeedSettingResponsePacket
    @ContributesAndroidInjector fun contributesInjectionSpeedInquirePacket(): BolusSpeedInquirePacket
    @ContributesAndroidInjector fun contributesInjectionSpeedInquireResponsePacket(): BolusSpeedInquireResponsePacket
    @ContributesAndroidInjector fun contributesBolusSpeedSettingReportPacket(): BolusSpeedSettingReportPacket
    @ContributesAndroidInjector fun contributesSoundInquirePacket(): SoundInquirePacket
    @ContributesAndroidInjector fun contributesSoundInquireResponsePacket(): SoundInquireResponsePacket
    @ContributesAndroidInjector fun contributesDisplayTimeInquirePacket(): DisplayTimeInquirePacket
    @ContributesAndroidInjector fun contributesDisplayTimeInquireResponsePacket(): DisplayTimeInquireResponsePacket
    @ContributesAndroidInjector fun contributesLanguageInquirePacket(): LanguageInquirePacket
    @ContributesAndroidInjector fun contributesLanguageInquireResponsePacket(): LanguageInquireResponsePacket
    @ContributesAndroidInjector fun contributesBigAPSMainInfoInquirePacket(): BigAPSMainInfoInquirePacket
    @ContributesAndroidInjector fun contributesBigAPSMainInfoInquireResponsePacket(): BigAPSMainInfoInquireResponsePacket
    @ContributesAndroidInjector fun contributesSerialNumInquirePacket(): SerialNumInquirePacket
    @ContributesAndroidInjector fun contributesSerialNumInquireResponsePacket(): SerialNumInquireResponsePacket
    @ContributesAndroidInjector fun contributesInjectionProgressReportPacket(): InjectionProgressReportPacket
}
