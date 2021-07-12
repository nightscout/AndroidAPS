package info.nightscout.androidaps.diaconn.di

import dagger.Module
import dagger.android.ContributesAndroidInjector
import info.nightscout.androidaps.diaconn.packet.*

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



}