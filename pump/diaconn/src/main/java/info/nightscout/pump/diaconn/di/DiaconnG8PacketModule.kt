package info.nightscout.pump.diaconn.di

import dagger.Module
import dagger.android.ContributesAndroidInjector
import info.nightscout.pump.diaconn.packet.AppCancelSettingPacket
import info.nightscout.pump.diaconn.packet.AppCancelSettingResponsePacket
import info.nightscout.pump.diaconn.packet.AppConfirmSettingPacket
import info.nightscout.pump.diaconn.packet.AppConfirmSettingResponsePacket
import info.nightscout.pump.diaconn.packet.BasalLimitInquirePacket
import info.nightscout.pump.diaconn.packet.BasalLimitInquireResponsePacket
import info.nightscout.pump.diaconn.packet.BasalPauseReportPacket
import info.nightscout.pump.diaconn.packet.BasalPauseSettingPacket
import info.nightscout.pump.diaconn.packet.BasalPauseSettingResponsePacket
import info.nightscout.pump.diaconn.packet.BasalSettingPacket
import info.nightscout.pump.diaconn.packet.BasalSettingReportPacket
import info.nightscout.pump.diaconn.packet.BasalSettingResponsePacket
import info.nightscout.pump.diaconn.packet.BatteryWarningReportPacket
import info.nightscout.pump.diaconn.packet.BigAPSMainInfoInquirePacket
import info.nightscout.pump.diaconn.packet.BigAPSMainInfoInquireResponsePacket
import info.nightscout.pump.diaconn.packet.BigLogInquirePacket
import info.nightscout.pump.diaconn.packet.BigLogInquireResponsePacket
import info.nightscout.pump.diaconn.packet.BigMainInfoInquirePacket
import info.nightscout.pump.diaconn.packet.BigMainInfoInquireResponsePacket
import info.nightscout.pump.diaconn.packet.BolusSpeedInquirePacket
import info.nightscout.pump.diaconn.packet.BolusSpeedInquireResponsePacket
import info.nightscout.pump.diaconn.packet.BolusSpeedSettingPacket
import info.nightscout.pump.diaconn.packet.BolusSpeedSettingReportPacket
import info.nightscout.pump.diaconn.packet.BolusSpeedSettingResponsePacket
import info.nightscout.pump.diaconn.packet.ConfirmReportPacket
import info.nightscout.pump.diaconn.packet.DiaconnG8Packet
import info.nightscout.pump.diaconn.packet.DisplayTimeInquirePacket
import info.nightscout.pump.diaconn.packet.DisplayTimeInquireResponsePacket
import info.nightscout.pump.diaconn.packet.DisplayTimeoutSettingPacket
import info.nightscout.pump.diaconn.packet.DisplayTimeoutSettingResponsePacket
import info.nightscout.pump.diaconn.packet.IncarnationInquirePacket
import info.nightscout.pump.diaconn.packet.IncarnationInquireResponsePacket
import info.nightscout.pump.diaconn.packet.InjectionBasalReportPacket
import info.nightscout.pump.diaconn.packet.InjectionBasalSettingPacket
import info.nightscout.pump.diaconn.packet.InjectionBasalSettingResponsePacket
import info.nightscout.pump.diaconn.packet.InjectionBlockReportPacket
import info.nightscout.pump.diaconn.packet.InjectionCancelSettingPacket
import info.nightscout.pump.diaconn.packet.InjectionCancelSettingResponsePacket
import info.nightscout.pump.diaconn.packet.InjectionExtendedBolusResultReportPacket
import info.nightscout.pump.diaconn.packet.InjectionExtendedBolusSettingPacket
import info.nightscout.pump.diaconn.packet.InjectionExtendedBolusSettingResponsePacket
import info.nightscout.pump.diaconn.packet.InjectionSnackInquirePacket
import info.nightscout.pump.diaconn.packet.InjectionSnackInquireResponsePacket
import info.nightscout.pump.diaconn.packet.InjectionSnackResultReportPacket
import info.nightscout.pump.diaconn.packet.InjectionSnackSettingPacket
import info.nightscout.pump.diaconn.packet.InjectionSnackSettingResponsePacket
import info.nightscout.pump.diaconn.packet.InsulinLackReportPacket
import info.nightscout.pump.diaconn.packet.LanguageInquirePacket
import info.nightscout.pump.diaconn.packet.LanguageInquireResponsePacket
import info.nightscout.pump.diaconn.packet.LanguageSettingPacket
import info.nightscout.pump.diaconn.packet.LanguageSettingResponsePacket
import info.nightscout.pump.diaconn.packet.LogStatusInquirePacket
import info.nightscout.pump.diaconn.packet.LogStatusInquireResponsePacket
import info.nightscout.pump.diaconn.packet.RejectReportPacket
import info.nightscout.pump.diaconn.packet.SerialNumInquirePacket
import info.nightscout.pump.diaconn.packet.SerialNumInquireResponsePacket
import info.nightscout.pump.diaconn.packet.SneckLimitInquirePacket
import info.nightscout.pump.diaconn.packet.SneckLimitInquireResponsePacket
import info.nightscout.pump.diaconn.packet.SoundInquirePacket
import info.nightscout.pump.diaconn.packet.SoundInquireResponsePacket
import info.nightscout.pump.diaconn.packet.SoundSettingPacket
import info.nightscout.pump.diaconn.packet.SoundSettingResponsePacket
import info.nightscout.pump.diaconn.packet.TempBasalInquirePacket
import info.nightscout.pump.diaconn.packet.TempBasalInquireResponsePacket
import info.nightscout.pump.diaconn.packet.TempBasalReportPacket
import info.nightscout.pump.diaconn.packet.TempBasalSettingPacket
import info.nightscout.pump.diaconn.packet.TempBasalSettingResponsePacket
import info.nightscout.pump.diaconn.packet.TimeInquirePacket
import info.nightscout.pump.diaconn.packet.TimeInquireResponsePacket
import info.nightscout.pump.diaconn.packet.TimeReportPacket
import info.nightscout.pump.diaconn.packet.TimeSettingPacket
import info.nightscout.pump.diaconn.packet.TimeSettingResponsePacket

@Module
@Suppress("unused")
abstract class DiaconnG8PacketModule {
    @ContributesAndroidInjector abstract fun contributesDiaconnG8Packet(): DiaconnG8Packet
    @ContributesAndroidInjector abstract fun contributesAppCancelSettingPacket(): AppCancelSettingPacket
    @ContributesAndroidInjector abstract fun contributesAppCancelSettingResponsePacket(): AppCancelSettingResponsePacket
    @ContributesAndroidInjector abstract fun contributesAppConfirmSettingPacket(): AppConfirmSettingPacket
    @ContributesAndroidInjector abstract fun contributesAppConfirmSettingResponsePacket(): AppConfirmSettingResponsePacket
    @ContributesAndroidInjector abstract fun contributesSneckLimitInquirePacket(): SneckLimitInquirePacket
    @ContributesAndroidInjector abstract fun contributesBasalLimitInquirePacket(): BasalLimitInquirePacket
    @ContributesAndroidInjector abstract fun contributesSneckLimitInquireResponsePacket(): SneckLimitInquireResponsePacket
    @ContributesAndroidInjector abstract fun contributesBasalLimitInquireResponsePacket(): BasalLimitInquireResponsePacket
    @ContributesAndroidInjector abstract fun contributesBasalPauseReportPacket(): BasalPauseReportPacket
    @ContributesAndroidInjector abstract fun contributesBasalPauseSettingPacket(): BasalPauseSettingPacket
    @ContributesAndroidInjector abstract fun contributesBasalPauseSettingResponsePacket(): BasalPauseSettingResponsePacket
    @ContributesAndroidInjector abstract fun contributesBasalSettingPacket(): BasalSettingPacket
    @ContributesAndroidInjector abstract fun contributesBasalSettingReportPacket(): BasalSettingReportPacket
    @ContributesAndroidInjector abstract fun contributesBasalSettingResponsePacket(): BasalSettingResponsePacket
    @ContributesAndroidInjector abstract fun contributesBigMainInfoInquirePacket(): BigMainInfoInquirePacket
    @ContributesAndroidInjector abstract fun contributesBigMainInfoInquireResponsePacket(): BigMainInfoInquireResponsePacket
    @ContributesAndroidInjector abstract fun contributesBigLogInquirePacket(): BigLogInquirePacket
    @ContributesAndroidInjector abstract fun contributesBigLogInquireResponsePacket(): BigLogInquireResponsePacket
    @ContributesAndroidInjector abstract fun contributesConfirmReportPacket(): ConfirmReportPacket
    @ContributesAndroidInjector abstract fun contributesInjectionBasalSettingPacket(): InjectionBasalSettingPacket
    @ContributesAndroidInjector abstract fun contributesInjectionBasalSettingResponsePacket(): InjectionBasalSettingResponsePacket
    @ContributesAndroidInjector abstract fun contributesInjectionSnackResultReportPacket(): InjectionSnackResultReportPacket
    @ContributesAndroidInjector abstract fun contributesInjectionSnactSettingPacket(): InjectionSnackSettingPacket
    @ContributesAndroidInjector abstract fun contributesInjectionSnackSettingResponsePacket(): InjectionSnackSettingResponsePacket
    @ContributesAndroidInjector abstract fun contributesInjectionExtendedBolusResultReportPacket(): InjectionExtendedBolusResultReportPacket
    @ContributesAndroidInjector abstract fun contributesInjectionExtendedBolusSettingPacket(): InjectionExtendedBolusSettingPacket
    @ContributesAndroidInjector abstract fun contributesInjectionExtendedBolusSettingResponsePacket(): InjectionExtendedBolusSettingResponsePacket
    @ContributesAndroidInjector abstract fun contributesInjectionBasalReportPacket(): InjectionBasalReportPacket
    @ContributesAndroidInjector abstract fun contributesInjectionSnackInquirePacket(): InjectionSnackInquirePacket
    @ContributesAndroidInjector abstract fun contributesInjectionSnackInquireResponsePacket(): InjectionSnackInquireResponsePacket
    @ContributesAndroidInjector abstract fun contributesRejectReportPacket(): RejectReportPacket
    @ContributesAndroidInjector abstract fun contributesTempBasalReportPacket(): TempBasalReportPacket
    @ContributesAndroidInjector abstract fun contributesTempBasalSettingPacket(): TempBasalSettingPacket
    @ContributesAndroidInjector abstract fun contributesTempBasalSettingResponsePacket(): TempBasalSettingResponsePacket
    @ContributesAndroidInjector abstract fun contributesTempBasalInquirePacket(): TempBasalInquirePacket
    @ContributesAndroidInjector abstract fun contributesTempBasalInquireResponsePacket(): TempBasalInquireResponsePacket
    @ContributesAndroidInjector abstract fun contributesTimeInquirePacket(): TimeInquirePacket
    @ContributesAndroidInjector abstract fun contributesTimeInquireResponsePacket(): TimeInquireResponsePacket
    @ContributesAndroidInjector abstract fun contributesTimeReportPacket(): TimeReportPacket
    @ContributesAndroidInjector abstract fun contributesTimeSettingPacket(): TimeSettingPacket
    @ContributesAndroidInjector abstract fun contributesTimeSettingResponsePacket(): TimeSettingResponsePacket
    @ContributesAndroidInjector abstract fun contributesLogStatusInquirePacket(): LogStatusInquirePacket
    @ContributesAndroidInjector abstract fun contributesLogStatusInquireResponsePacket(): LogStatusInquireResponsePacket
    @ContributesAndroidInjector abstract fun contributesInjectionCancelSettingPacket(): InjectionCancelSettingPacket
    @ContributesAndroidInjector abstract fun contributesInjectionCancelSettingResponsePacket(): InjectionCancelSettingResponsePacket
    @ContributesAndroidInjector abstract fun contributesSoundSettingPacket(): SoundSettingPacket
    @ContributesAndroidInjector abstract fun contributesSoundSettingResponsePacket(): SoundSettingResponsePacket
    @ContributesAndroidInjector abstract fun contributesDisplayTimeoutSettingPacket(): DisplayTimeoutSettingPacket
    @ContributesAndroidInjector abstract fun contributesDisplayTimeoutSettingResponsePacket(): DisplayTimeoutSettingResponsePacket
    @ContributesAndroidInjector abstract fun contributesLanguageSettingPacket(): LanguageSettingPacket
    @ContributesAndroidInjector abstract fun contributesLanguageSettingResponsePacket(): LanguageSettingResponsePacket
    @ContributesAndroidInjector abstract fun contributesInjectionBlockReportPacket(): InjectionBlockReportPacket
    @ContributesAndroidInjector abstract fun contributesBatteryWarningReportPacket(): BatteryWarningReportPacket
    @ContributesAndroidInjector abstract fun contributesInsulinLackReportPacket(): InsulinLackReportPacket
    @ContributesAndroidInjector abstract fun contributesIncarnationInquirePacket(): IncarnationInquirePacket
    @ContributesAndroidInjector abstract fun contributesIncarnationInquireResponsePacket(): IncarnationInquireResponsePacket
    @ContributesAndroidInjector abstract fun contributesBolusSpeedSettingPacket(): BolusSpeedSettingPacket
    @ContributesAndroidInjector abstract fun contributesBolusSpeedSettingResponsePacket(): BolusSpeedSettingResponsePacket
    @ContributesAndroidInjector abstract fun contributesInjectionSpeedInquirePacket(): BolusSpeedInquirePacket
    @ContributesAndroidInjector abstract fun contributesInjectionSpeedInquireResponsePacket(): BolusSpeedInquireResponsePacket
    @ContributesAndroidInjector abstract fun contributesBolusSpeedSettingReportPacket(): BolusSpeedSettingReportPacket
    @ContributesAndroidInjector abstract fun contributesSoundInquirePacket(): SoundInquirePacket
    @ContributesAndroidInjector abstract fun contributesSoundInquireResponsePacket(): SoundInquireResponsePacket
    @ContributesAndroidInjector abstract fun contributesDisplayTimeInquirePacket(): DisplayTimeInquirePacket
    @ContributesAndroidInjector abstract fun contributesDisplayTimeInquireResponsePacket(): DisplayTimeInquireResponsePacket
    @ContributesAndroidInjector abstract fun contributesLanguageInquirePacket(): LanguageInquirePacket
    @ContributesAndroidInjector abstract fun contributesLanguageInquireResponsePacket(): LanguageInquireResponsePacket
    @ContributesAndroidInjector abstract fun contributesBigAPSMainInfoInquirePacket(): BigAPSMainInfoInquirePacket
    @ContributesAndroidInjector abstract fun contributesBigAPSMainInfoInquireResponsePacket(): BigAPSMainInfoInquireResponsePacket
    @ContributesAndroidInjector abstract fun contributesSerialNumInquirePacket(): SerialNumInquirePacket
    @ContributesAndroidInjector abstract fun contributesSerialNumInquireResponsePacket(): SerialNumInquireResponsePacket


}
