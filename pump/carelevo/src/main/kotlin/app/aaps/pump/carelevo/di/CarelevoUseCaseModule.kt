package app.aaps.pump.carelevo.di

import app.aaps.pump.carelevo.domain.repository.CarelevoAlarmInfoRepository
import app.aaps.pump.carelevo.domain.repository.CarelevoInfusionInfoRepository
import app.aaps.pump.carelevo.domain.repository.CarelevoPatchInfoRepository
import app.aaps.pump.carelevo.domain.repository.CarelevoUserSettingInfoRepository
import app.aaps.pump.carelevo.domain.usecase.alarm.AlarmClearPatchDiscardUseCase
import app.aaps.pump.carelevo.domain.usecase.alarm.AlarmClearRequestUseCase
import app.aaps.pump.carelevo.domain.usecase.alarm.CarelevoAlarmInfoUseCase
import app.aaps.pump.carelevo.domain.usecase.basal.CarelevoCancelTempBasalInfusionUseCase
import app.aaps.pump.carelevo.domain.usecase.basal.CarelevoSetBasalProgramUseCase
import app.aaps.pump.carelevo.domain.usecase.basal.CarelevoStartTempBasalInfusionUseCase
import app.aaps.pump.carelevo.domain.usecase.bolus.CarelevoCancelExtendBolusInfusionUseCase
import app.aaps.pump.carelevo.domain.usecase.bolus.CarelevoCancelImmeBolusInfusionUseCase
import app.aaps.pump.carelevo.domain.usecase.bolus.CarelevoFinishImmeBolusInfusionUseCase
import app.aaps.pump.carelevo.domain.usecase.bolus.CarelevoStartExtendBolusInfusionUseCase
import app.aaps.pump.carelevo.domain.usecase.bolus.CarelevoStartImmeBolusInfusionUseCase
import app.aaps.pump.carelevo.domain.usecase.infusion.CarelevoInfusionInfoMonitorUseCase
import app.aaps.pump.carelevo.domain.usecase.infusion.CarelevoPumpResumeUseCase
import app.aaps.pump.carelevo.domain.usecase.infusion.CarelevoPumpStopUseCase
import app.aaps.pump.carelevo.domain.usecase.patch.CarelevoConnectNewPatchUseCase
import app.aaps.pump.carelevo.domain.usecase.patch.CarelevoPatchForceDiscardUseCase
import app.aaps.pump.carelevo.domain.usecase.patch.CarelevoPatchInfoMonitorUseCase
import app.aaps.pump.carelevo.domain.usecase.patch.CarelevoPatchNeedleInsertionCheckUseCase
import app.aaps.pump.carelevo.domain.usecase.patch.CarelevoPatchRptInfusionInfoProcessUseCase
import app.aaps.pump.carelevo.domain.usecase.patch.CarelevoPatchSafetyCheckUseCase
import app.aaps.pump.carelevo.domain.usecase.userSetting.CarelevoCreateUserSettingInfoUseCase
import app.aaps.pump.carelevo.domain.usecase.userSetting.CarelevoDeleteUserSettingInfoUseCase
import app.aaps.pump.carelevo.domain.usecase.userSetting.CarelevoUpdateLowInsulinNoticeAmountUseCase
import app.aaps.pump.carelevo.domain.usecase.userSetting.CarelevoUpdateMaxBolusDoseUseCase
import app.aaps.pump.carelevo.domain.usecase.userSetting.CarelevoUserSettingInfoMonitorUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
class CarelevoUseCaseModule {

    @Provides
    fun provideCarelevoConnectNewPatchUseCase(
        carelevoPatchInfoRepository: CarelevoPatchInfoRepository
    ): CarelevoConnectNewPatchUseCase {
        return CarelevoConnectNewPatchUseCase(
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
        carelevoPatchInfoRepository: CarelevoPatchInfoRepository,
        carelevoInfusionInfoRepository: CarelevoInfusionInfoRepository
    ): CarelevoSetBasalProgramUseCase {
        return CarelevoSetBasalProgramUseCase(
            carelevoPatchInfoRepository,
            carelevoInfusionInfoRepository
        )
    }

    @Provides
    fun provideCarelevoStartTempBasalInfusionUseCase(
        carelevoPatchInfoRepository: CarelevoPatchInfoRepository,
        carelevoInfusionInfoRepository: CarelevoInfusionInfoRepository
    ): CarelevoStartTempBasalInfusionUseCase {
        return CarelevoStartTempBasalInfusionUseCase(
            carelevoPatchInfoRepository,
            carelevoInfusionInfoRepository
        )
    }

    @Provides
    fun provideCarelevoCancelTempBasalInfusionUseCase(
        carelevoPatchInfoRepository: CarelevoPatchInfoRepository,
        carelevoInfusionInfoRepository: CarelevoInfusionInfoRepository
    ): CarelevoCancelTempBasalInfusionUseCase {
        return CarelevoCancelTempBasalInfusionUseCase(
            carelevoPatchInfoRepository,
            carelevoInfusionInfoRepository
        )
    }

    //==========================================================================================
    // about bolus
    @Provides
    fun provideCarelevoStartImmeBolusInfusionUseCase(
        carelevoPatchInfoRepository: CarelevoPatchInfoRepository,
        carelevoInfusionInfoRepository: CarelevoInfusionInfoRepository
    ): CarelevoStartImmeBolusInfusionUseCase {
        return CarelevoStartImmeBolusInfusionUseCase(
            carelevoPatchInfoRepository,
            carelevoInfusionInfoRepository
        )
    }

    @Provides
    fun provideCarelevoStartExtendBolusInfusionUseCase(
        carelevoPatchInfoRepository: CarelevoPatchInfoRepository,
        carelevoInfusionInfoRepository: CarelevoInfusionInfoRepository
    ): CarelevoStartExtendBolusInfusionUseCase {
        return CarelevoStartExtendBolusInfusionUseCase(
            carelevoPatchInfoRepository,
            carelevoInfusionInfoRepository
        )
    }

    @Provides
    fun provideCarelevoCancelImmeBolusInfusionUseCase(
        carelevoPatchInfoRepository: CarelevoPatchInfoRepository,
        carelevoInfusionInfoRepository: CarelevoInfusionInfoRepository
    ): CarelevoCancelImmeBolusInfusionUseCase {
        return CarelevoCancelImmeBolusInfusionUseCase(
            carelevoPatchInfoRepository,
            carelevoInfusionInfoRepository
        )
    }

    @Provides
    fun provideCarelevoCancelExtendBolusInfusionUseCase(
        carelevoPatchInfoRepository: CarelevoPatchInfoRepository,
        carelevoInfusionInfoRepository: CarelevoInfusionInfoRepository
    ): CarelevoCancelExtendBolusInfusionUseCase {
        return CarelevoCancelExtendBolusInfusionUseCase(
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
        carelevoInfusionInfoRepository: CarelevoInfusionInfoRepository,
        carelevoUserSettingInfoRepository: CarelevoUserSettingInfoRepository
    ): CarelevoUpdateMaxBolusDoseUseCase {
        return CarelevoUpdateMaxBolusDoseUseCase(
            carelevoInfusionInfoRepository,
            carelevoUserSettingInfoRepository
        )
    }

    @Provides
    fun provideCarelevoUpdateLowInsulinNoticeAmountUseCase(
        carelevoUserSettingInfoRepository: CarelevoUserSettingInfoRepository
    ): CarelevoUpdateLowInsulinNoticeAmountUseCase {
        return CarelevoUpdateLowInsulinNoticeAmountUseCase(
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

    //==========================================================================================
    // about patch
    @Provides
    fun provideCarelevoPatchRptInfusionInfoProcessUseCase(
        carelevoPatchInfoRepository: CarelevoPatchInfoRepository
    ): CarelevoPatchRptInfusionInfoProcessUseCase {
        return CarelevoPatchRptInfusionInfoProcessUseCase(
            carelevoPatchInfoRepository
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
        carelevoPatchInfoRepository: CarelevoPatchInfoRepository
    ): CarelevoPatchSafetyCheckUseCase {
        return CarelevoPatchSafetyCheckUseCase(
            carelevoPatchInfoRepository
        )
    }

    @Provides
    fun provideCarelevoPatchCannulaInsertionCheckUseCase(
        carelevoPatchInfoRepository: CarelevoPatchInfoRepository
    ): CarelevoPatchNeedleInsertionCheckUseCase {
        return CarelevoPatchNeedleInsertionCheckUseCase(
            carelevoPatchInfoRepository
        )
    }

    //==========================================================================================
    // about infusion
    @Provides
    fun provideCarelevoPumpResumeUseCase(
        carelevoPatchInfoRepository: CarelevoPatchInfoRepository,
        carelevoInfusionInfoRepository: CarelevoInfusionInfoRepository
    ): CarelevoPumpResumeUseCase {
        return CarelevoPumpResumeUseCase(
            carelevoPatchInfoRepository,
            carelevoInfusionInfoRepository
        )
    }

    @Provides
    fun provideCarelevoPumpStopUseCase(
        carelevoPatchInfoRepository: CarelevoPatchInfoRepository,
        carelevoInfusionInfoRepository: CarelevoInfusionInfoRepository
    ): CarelevoPumpStopUseCase {
        return CarelevoPumpStopUseCase(
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
        alarmRepository: CarelevoAlarmInfoRepository
    ): AlarmClearRequestUseCase {
        return AlarmClearRequestUseCase(alarmRepository)
    }

    @Provides
    fun provideAlarmClearPatchDiscardUseCase(
        alarmRepository: CarelevoAlarmInfoRepository,
        patchInfoRepository: CarelevoPatchInfoRepository,
        userSettingInfoRepository: CarelevoUserSettingInfoRepository,
        infusionInfoRepository: CarelevoInfusionInfoRepository
    ): AlarmClearPatchDiscardUseCase {
        return AlarmClearPatchDiscardUseCase(alarmRepository, patchInfoRepository, userSettingInfoRepository, infusionInfoRepository)
    }
}
