package info.nightscout.androidaps.plugins.pump.carelevo.di

import app.aaps.core.interfaces.logging.AAPSLogger
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import info.nightscout.androidaps.plugins.pump.carelevo.domain.CarelevoPatchObserver
import info.nightscout.androidaps.plugins.pump.carelevo.domain.repository.CarelevoAlarmInfoRepository
import info.nightscout.androidaps.plugins.pump.carelevo.domain.repository.CarelevoBasalRepository
import info.nightscout.androidaps.plugins.pump.carelevo.domain.repository.CarelevoBolusRepository
import info.nightscout.androidaps.plugins.pump.carelevo.domain.repository.CarelevoInfusionInfoRepository
import info.nightscout.androidaps.plugins.pump.carelevo.domain.repository.CarelevoPatchInfoRepository
import info.nightscout.androidaps.plugins.pump.carelevo.domain.repository.CarelevoPatchRepository
import info.nightscout.androidaps.plugins.pump.carelevo.domain.repository.CarelevoUserSettingInfoRepository
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.alarm.AlarmClearPatchDiscardUseCase
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.alarm.AlarmClearRequestUseCase
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.alarm.CarelevoAlarmInfoUseCase
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.basal.CarelevoCancelTempBasalInfusionUseCase
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.basal.CarelevoSetBasalProgramUseCase
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.basal.CarelevoStartTempBasalInfusionUseCase
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.basal.CarelevoUpdateBasalProgramUseCase
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.bolus.CarelevoCancelExtendBolusInfusionUseCase
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.bolus.CarelevoCancelImmeBolusInfusionUseCase
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.bolus.CarelevoFinishImmeBolusInfusionUseCase
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.bolus.CarelevoStartExtendBolusInfusionUseCase
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.bolus.CarelevoStartImmeBolusInfusionUseCase
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.infusion.CarelevoInfusionInfoMonitorUseCase
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.infusion.CarelevoPumpResumeUseCase
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.infusion.CarelevoPumpStopUseCase
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.patch.CarelevoConnectNewPatchUseCase
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.patch.CarelevoPatchAdditionalPrimingUseCase
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.patch.CarelevoPatchCannulaInsertionConfirmUseCase
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.patch.CarelevoPatchDiscardUseCase
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.patch.CarelevoPatchForceDiscardUseCase
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.patch.CarelevoPatchInfoMonitorUseCase
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.patch.CarelevoPatchNeedleInsertionCheckUseCase
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.patch.CarelevoPatchRptInfusionInfoProcessUseCase
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.patch.CarelevoPatchSafetyCheckUseCase
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.patch.CarelevoPatchTimeZoneUpdateUseCase
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.patch.CarelevoRequestPatchInfusionInfoUseCase
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.userSetting.CarelevoCreateUserSettingInfoUseCase
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.userSetting.CarelevoDeleteUserSettingInfoUseCase
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.userSetting.CarelevoPatchBuzzModifyUseCase
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.userSetting.CarelevoPatchExpiredThresholdModifyUseCase
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.userSetting.CarelevoUpdateLowInsulinNoticeAmountUseCase
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.userSetting.CarelevoUpdateMaxBolusDoseUseCase
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.userSetting.CarelevoUserSettingInfoMonitorUseCase

@Module
@InstallIn(SingletonComponent::class)
class CarelevoUseCaseModule {

    @Provides
    fun provideCarelevoConnectNewPatchUseCase(
        aapsLogger: AAPSLogger,
        carelevoPatchObserver: CarelevoPatchObserver,
        carelevoPatchRepository: CarelevoPatchRepository,
        carelevoPatchInfoRepository: CarelevoPatchInfoRepository
    ): CarelevoConnectNewPatchUseCase {
        return CarelevoConnectNewPatchUseCase(
            aapsLogger,
            carelevoPatchObserver,
            carelevoPatchRepository,
            carelevoPatchInfoRepository
        )
    }

    @Provides
    fun provideCarelevoInfusionInfoMonitorUseCase(
        carelevoInfusionInfoRepository: CarelevoInfusionInfoRepository
    ): CarelevoInfusionInfoMonitorUseCase {
        return CarelevoInfusionInfoMonitorUseCase(
            carelevoInfusionInfoRepository
        )
    }

    @Provides
    fun provideCarelevoPatchInfoMonitorUseCase(
        carelevoPatchInfoRepository: CarelevoPatchInfoRepository
    ): CarelevoPatchInfoMonitorUseCase {
        return CarelevoPatchInfoMonitorUseCase(
            carelevoPatchInfoRepository
        )
    }

    @Provides
    fun provideCarelevoUserSettingInfoMonitorUseCase(
        carelevoUserSettingInfoRepository: CarelevoUserSettingInfoRepository
    ): CarelevoUserSettingInfoMonitorUseCase {
        return CarelevoUserSettingInfoMonitorUseCase(
            carelevoUserSettingInfoRepository
        )
    }

    //==========================================================================================
    // about basal
    @Provides
    fun provideCarelevoSetBasalProgramUseCase(
        aapsLogger: AAPSLogger,
        carelevoPatchObserver: CarelevoPatchObserver,
        carelevoBasalRepository: CarelevoBasalRepository,
        carelevoPatchInfoRepository: CarelevoPatchInfoRepository,
        carelevoInfusionInfoRepository: CarelevoInfusionInfoRepository
    ): CarelevoSetBasalProgramUseCase {
        return CarelevoSetBasalProgramUseCase(
            aapsLogger,
            carelevoPatchObserver,
            carelevoBasalRepository,
            carelevoPatchInfoRepository,
            carelevoInfusionInfoRepository
        )
    }

    @Provides
    fun provideCarelevoUpdateBasalProgramUseCase(
        aapsLogger: AAPSLogger,
        carelevoPatchObserver: CarelevoPatchObserver,
        carelevoBasalRepository: CarelevoBasalRepository,
        carelevoPatchInfoRepository: CarelevoPatchInfoRepository,
        carelevoInfusionInfoRepository: CarelevoInfusionInfoRepository
    ): CarelevoUpdateBasalProgramUseCase {
        return CarelevoUpdateBasalProgramUseCase(
            aapsLogger,
            carelevoPatchObserver,
            carelevoBasalRepository,
            carelevoPatchInfoRepository,
            carelevoInfusionInfoRepository
        )
    }

    @Provides
    fun provideCarelevoStartTempBasalInfusionUseCase(
        carelevoPatchObserver: CarelevoPatchObserver,
        carelevoBasalRepository: CarelevoBasalRepository,
        carelevoPatchInfoRepository: CarelevoPatchInfoRepository,
        carelevoInfusionInfoRepository: CarelevoInfusionInfoRepository
    ): CarelevoStartTempBasalInfusionUseCase {
        return CarelevoStartTempBasalInfusionUseCase(
            carelevoPatchObserver,
            carelevoBasalRepository,
            carelevoPatchInfoRepository,
            carelevoInfusionInfoRepository
        )
    }

    @Provides
    fun provideCarelevoCancelTempBasalInfusionUseCase(
        carelevoPatchObserver: CarelevoPatchObserver,
        carelevoBasalRepository: CarelevoBasalRepository,
        carelevoPatchInfoRepository: CarelevoPatchInfoRepository,
        carelevoInfusionInfoRepository: CarelevoInfusionInfoRepository
    ): CarelevoCancelTempBasalInfusionUseCase {
        return CarelevoCancelTempBasalInfusionUseCase(
            carelevoPatchObserver,
            carelevoBasalRepository,
            carelevoPatchInfoRepository,
            carelevoInfusionInfoRepository
        )
    }

    //==========================================================================================
    // about bolus
    @Provides
    fun provideCarelevoStartImmeBolusInfusionUseCase(
        carelevoPatchObserver: CarelevoPatchObserver,
        carelevoBolusRepository: CarelevoBolusRepository,
        carelevoPatchInfoRepository: CarelevoPatchInfoRepository,
        carelevoInfusionInfoRepository: CarelevoInfusionInfoRepository
    ): CarelevoStartImmeBolusInfusionUseCase {
        return CarelevoStartImmeBolusInfusionUseCase(
            carelevoPatchObserver,
            carelevoBolusRepository,
            carelevoPatchInfoRepository,
            carelevoInfusionInfoRepository
        )
    }

    @Provides
    fun provideCarelevoStartExtendBolusInfusionUseCase(
        carelevoPatchObserver: CarelevoPatchObserver,
        carelevoBolusRepository: CarelevoBolusRepository,
        carelevoPatchInfoRepository: CarelevoPatchInfoRepository,
        carelevoInfusionInfoRepository: CarelevoInfusionInfoRepository
    ): CarelevoStartExtendBolusInfusionUseCase {
        return CarelevoStartExtendBolusInfusionUseCase(
            carelevoPatchObserver,
            carelevoBolusRepository,
            carelevoPatchInfoRepository,
            carelevoInfusionInfoRepository
        )
    }

    @Provides
    fun provideCarelevoCancelImmeBolusInfusionUseCase(
        carelevoPatchObserver: CarelevoPatchObserver,
        carelevoBolusRepository: CarelevoBolusRepository,
        carelevoPatchInfoRepository: CarelevoPatchInfoRepository,
        carelevoInfusionInfoRepository: CarelevoInfusionInfoRepository
    ): CarelevoCancelImmeBolusInfusionUseCase {
        return CarelevoCancelImmeBolusInfusionUseCase(
            carelevoPatchObserver,
            carelevoBolusRepository,
            carelevoPatchInfoRepository,
            carelevoInfusionInfoRepository
        )
    }

    @Provides
    fun provideCarelevoCancelExtendBolusInfusionUseCase(
        carelevoPatchObserver: CarelevoPatchObserver,
        carelevoBolusRepository: CarelevoBolusRepository,
        carelevoPatchInfoRepository: CarelevoPatchInfoRepository,
        carelevoInfusionInfoRepository: CarelevoInfusionInfoRepository
    ): CarelevoCancelExtendBolusInfusionUseCase {
        return CarelevoCancelExtendBolusInfusionUseCase(
            carelevoPatchObserver,
            carelevoBolusRepository,
            carelevoPatchInfoRepository,
            carelevoInfusionInfoRepository
        )
    }

    @Provides
    fun provideCarelevoFinishImmeBolusInfusionUseCase(
        carelevoPatchInfoRepository: CarelevoPatchInfoRepository,
        carelevoInfusionInfoRepository: CarelevoInfusionInfoRepository
    ): CarelevoFinishImmeBolusInfusionUseCase {
        return CarelevoFinishImmeBolusInfusionUseCase(
            carelevoPatchInfoRepository,
            carelevoInfusionInfoRepository
        )
    }

    //==========================================================================================
    // about user setting info
    @Provides
    fun provideCarelevoUpdateMaxBolusDoseUseCase(
        aapsLogger: AAPSLogger,
        carelevoPatchObserver: CarelevoPatchObserver,
        carelevoPatchRepository: CarelevoPatchRepository,
        carelevoInfusionInfoRepository: CarelevoInfusionInfoRepository,
        carelevoUserSettingInfoRepository: CarelevoUserSettingInfoRepository
    ): CarelevoUpdateMaxBolusDoseUseCase {
        return CarelevoUpdateMaxBolusDoseUseCase(
            aapsLogger,
            carelevoPatchObserver,
            carelevoPatchRepository,
            carelevoInfusionInfoRepository,
            carelevoUserSettingInfoRepository
        )
    }

    @Provides
    fun provideCarelevoUpdateLowInsulinNoticeAmountUseCase(
        aapsLogger: AAPSLogger,
        carelevoPatchObserver: CarelevoPatchObserver,
        carelevoPatchRepository: CarelevoPatchRepository,
        carelevoUserSettingInfoRepository: CarelevoUserSettingInfoRepository
    ): CarelevoUpdateLowInsulinNoticeAmountUseCase {
        return CarelevoUpdateLowInsulinNoticeAmountUseCase(
            aapsLogger,
            carelevoPatchObserver,
            carelevoPatchRepository,
            carelevoUserSettingInfoRepository
        )
    }

    @Provides
    fun provideCarelevoDeleteUserSettingInfoUseCase(
        carelevoUserSettingInfoRepository: CarelevoUserSettingInfoRepository
    ): CarelevoDeleteUserSettingInfoUseCase {
        return CarelevoDeleteUserSettingInfoUseCase(
            carelevoUserSettingInfoRepository
        )
    }

    @Provides
    fun provideCarelevoCreateUserSettingInfoUseCase(
        carelevoUserSettingInfoRepository: CarelevoUserSettingInfoRepository
    ): CarelevoCreateUserSettingInfoUseCase {
        return CarelevoCreateUserSettingInfoUseCase(
            carelevoUserSettingInfoRepository
        )
    }

    @Provides
    fun provideCarelevoPatchExpiredThresholdModifyUseCase(
        carelevoPatchObserver: CarelevoPatchObserver,
        carelevoPatchRepository: CarelevoPatchRepository
    ): CarelevoPatchExpiredThresholdModifyUseCase {
        return CarelevoPatchExpiredThresholdModifyUseCase(carelevoPatchObserver, carelevoPatchRepository)
    }

    //==========================================================================================
    // about patch
    @Provides
    fun provideCarelevoRequestPatchInfusionInfoUseCase(
        carelevoPatchRepository: CarelevoPatchRepository
    ): CarelevoRequestPatchInfusionInfoUseCase {
        return CarelevoRequestPatchInfusionInfoUseCase(
            carelevoPatchRepository
        )
    }

    @Provides
    fun provideCarelevoPatchRptInfusionInfoProcessUseCase(
        carelevoPatchInfoRepository: CarelevoPatchInfoRepository
    ): CarelevoPatchRptInfusionInfoProcessUseCase {
        return CarelevoPatchRptInfusionInfoProcessUseCase(
            carelevoPatchInfoRepository
        )
    }

    @Provides
    fun provideCarelevoPatchDiscardUseCase(
        carelevoPatchObserver: CarelevoPatchObserver,
        carelevoPatchRepository: CarelevoPatchRepository,
        carelevoPatchInfoRepository: CarelevoPatchInfoRepository,
        carelevoInfusionInfoRepository: CarelevoInfusionInfoRepository,
        carelevoUserSettingInfoRepository: CarelevoUserSettingInfoRepository
    ): CarelevoPatchDiscardUseCase {
        return CarelevoPatchDiscardUseCase(
            carelevoPatchObserver,
            carelevoPatchRepository,
            carelevoPatchInfoRepository,
            carelevoInfusionInfoRepository,
            carelevoUserSettingInfoRepository
        )
    }

    @Provides
    fun provideCarelevoPatchForceDiscardUseCase(
        carelevoPatchInfoRepository: CarelevoPatchInfoRepository,
        carelevoInfusionInfoRepository: CarelevoInfusionInfoRepository,
        carelevoUserSettingInfoRepository: CarelevoUserSettingInfoRepository
    ): CarelevoPatchForceDiscardUseCase {
        return CarelevoPatchForceDiscardUseCase(
            carelevoPatchInfoRepository,
            carelevoInfusionInfoRepository,
            carelevoUserSettingInfoRepository
        )
    }

    @Provides
    fun provideCarelevoPatchSafetyCheckUseCase(
        carelevoPatchObserver: CarelevoPatchObserver,
        carelevoPatchRepository: CarelevoPatchRepository,
        carelevoPatchInfoRepository: CarelevoPatchInfoRepository
    ): CarelevoPatchSafetyCheckUseCase {
        return CarelevoPatchSafetyCheckUseCase(
            carelevoPatchObserver,
            carelevoPatchRepository,
            carelevoPatchInfoRepository
        )
    }

    @Provides
    fun provideCarelevoPatchCannulaInsertionCheckUseCase(
        aapsLogger: AAPSLogger,
        carelevoPatchObserver: CarelevoPatchObserver,
        carelevoPatchRepository: CarelevoPatchRepository,
        carelevoPatchInfoRepository: CarelevoPatchInfoRepository
    ): CarelevoPatchNeedleInsertionCheckUseCase {
        return CarelevoPatchNeedleInsertionCheckUseCase(
            aapsLogger,
            carelevoPatchObserver,
            carelevoPatchRepository,
            carelevoPatchInfoRepository
        )
    }

    @Provides
    fun provideCarelevoPatchCannulaInsertionConfirmUseCase(
        carelevoPatchObserver: CarelevoPatchObserver,
        carelevoPatchRepository: CarelevoPatchRepository,
        carelevoPatchInfoRepository: CarelevoPatchInfoRepository
    ): CarelevoPatchCannulaInsertionConfirmUseCase {
        return CarelevoPatchCannulaInsertionConfirmUseCase(
            carelevoPatchObserver,
            carelevoPatchRepository,
            carelevoPatchInfoRepository
        )
    }

    //==========================================================================================
    // about infusion
    @Provides
    fun provideCarelevoPumpResumeUseCase(
        carelevoPatchObserver: CarelevoPatchObserver,
        carelevoPatchRepository: CarelevoPatchRepository,
        carelevoPatchInfoRepository: CarelevoPatchInfoRepository,
        carelevoInfusionInfoRepository: CarelevoInfusionInfoRepository
    ): CarelevoPumpResumeUseCase {
        return CarelevoPumpResumeUseCase(
            carelevoPatchObserver,
            carelevoPatchRepository,
            carelevoPatchInfoRepository,
            carelevoInfusionInfoRepository
        )
    }

    @Provides
    fun provideCarelevoPumpStopUseCase(
        carelevoPatchObserver: CarelevoPatchObserver,
        carelevoPatchRepository: CarelevoPatchRepository,
        carelevoPatchInfoRepository: CarelevoPatchInfoRepository,
        carelevoInfusionInfoRepository: CarelevoInfusionInfoRepository
    ): CarelevoPumpStopUseCase {
        return CarelevoPumpStopUseCase(
            carelevoPatchObserver,
            carelevoPatchRepository,
            carelevoPatchInfoRepository,
            carelevoInfusionInfoRepository
        )
    }

    @Provides
    fun provideCarelevoAlarmInfoUseCase(
        carelevoAlarmInfoRepository: CarelevoAlarmInfoRepository
    ): CarelevoAlarmInfoUseCase {
        return CarelevoAlarmInfoUseCase(carelevoAlarmInfoRepository)
    }

    @Provides
    fun provideAlarmClearRequestUseCase(
        patchObserver: CarelevoPatchObserver,
        patchRepository: CarelevoPatchRepository,
        alarmRepository: CarelevoAlarmInfoRepository
    ): AlarmClearRequestUseCase {
        return AlarmClearRequestUseCase(patchObserver, patchRepository, alarmRepository)
    }

    @Provides
    fun provideAlarmClearPatchDiscardUseCase(
        patchObserver: CarelevoPatchObserver,
        patchRepository: CarelevoPatchRepository,
        alarmRepository: CarelevoAlarmInfoRepository,
        patchInfoRepository: CarelevoPatchInfoRepository,
        userSettingInfoRepository: CarelevoUserSettingInfoRepository,
        infusionInfoRepository: CarelevoInfusionInfoRepository
    ): AlarmClearPatchDiscardUseCase {
        return AlarmClearPatchDiscardUseCase(patchObserver, patchRepository, alarmRepository, patchInfoRepository, userSettingInfoRepository, infusionInfoRepository)
    }

    @Provides
    fun provideCarelevoPatchTimeZoneUpdateUseCase(
        patchRepository: CarelevoPatchRepository,
        patchObserver: CarelevoPatchObserver
    ): CarelevoPatchTimeZoneUpdateUseCase {
        return CarelevoPatchTimeZoneUpdateUseCase(patchRepository, patchObserver)
    }

    @Provides
    fun provideCarelevoPatchBuzzModifyUseCase(
        patchRepository: CarelevoPatchRepository,
        patchObserver: CarelevoPatchObserver
    ): CarelevoPatchBuzzModifyUseCase {
        return CarelevoPatchBuzzModifyUseCase(patchObserver, patchRepository)
    }

    @Provides
    fun provideCarelevoPatchAdditionalPrimingUseCase(
        patchRepository: CarelevoPatchRepository,
        patchObserver: CarelevoPatchObserver
    ): CarelevoPatchAdditionalPrimingUseCase {
        return CarelevoPatchAdditionalPrimingUseCase(patchRepository, patchObserver)

    }
}
