package app.aaps.pump.carelevo.di

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.carelevo.ble.core.CarelevoBleController
import app.aaps.pump.carelevo.common.CarelevoPatch
import app.aaps.pump.carelevo.domain.CarelevoPatchObserver
import app.aaps.pump.carelevo.domain.repository.CarelevoBasalRepository
import app.aaps.pump.carelevo.domain.repository.CarelevoBolusRepository
import app.aaps.pump.carelevo.domain.repository.CarelevoPatchRepository
import app.aaps.pump.carelevo.domain.usecase.alarm.CarelevoAlarmInfoUseCase
import app.aaps.pump.carelevo.domain.usecase.infusion.CarelevoInfusionInfoMonitorUseCase
import app.aaps.pump.carelevo.domain.usecase.patch.CarelevoPatchInfoMonitorUseCase
import app.aaps.pump.carelevo.domain.usecase.patch.CarelevoPatchRptInfusionInfoProcessUseCase
import app.aaps.pump.carelevo.domain.usecase.patch.CarelevoRequestPatchInfusionInfoUseCase
import app.aaps.pump.carelevo.domain.usecase.userSetting.CarelevoCreateUserSettingInfoUseCase
import app.aaps.pump.carelevo.domain.usecase.userSetting.CarelevoUpdateLowInsulinNoticeAmountUseCase
import app.aaps.pump.carelevo.domain.usecase.userSetting.CarelevoUpdateMaxBolusDoseUseCase
import app.aaps.pump.carelevo.domain.usecase.userSetting.CarelevoUserSettingInfoMonitorUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
class CarelevoManagerModule {

    @Provides
    @Singleton
    fun provideCarelevoPatchObserver(
        carelevoBasalRepository: CarelevoBasalRepository,
        carelevoBolusRepository: CarelevoBolusRepository,
        carelevoPatchRepository: CarelevoPatchRepository,
        aapsSchedulers: AapsSchedulers,
        aapsLogger: AAPSLogger
    ): CarelevoPatchObserver {
        return CarelevoPatchObserver(
            carelevoPatchRepository,
            carelevoBasalRepository,
            carelevoBolusRepository,
            aapsSchedulers,
            aapsLogger
        )
    }

    @Provides
    @Singleton
    fun provideCarelevoPatch(
        carelevoBleController: CarelevoBleController,
        carelevoPatchObserver: CarelevoPatchObserver,
        aapsSchedulers: AapsSchedulers,
        rxBus: RxBus,
        sp: SP,
        preferences: Preferences,
        aapsLogger: AAPSLogger,
        carelevoInfusionInfoMonitorUseCase: CarelevoInfusionInfoMonitorUseCase,
        carelevoPatchInfoMonitorUseCase: CarelevoPatchInfoMonitorUseCase,
        carelevoUserSettingInfoMonitorUseCase: CarelevoUserSettingInfoMonitorUseCase,
        carelevoPatchRptInfusionInfoProcessUseCase: CarelevoPatchRptInfusionInfoProcessUseCase,
        carelevoUpdateMaxBolusDoseUseCase: CarelevoUpdateMaxBolusDoseUseCase,
        carelevoUpdateLowInfusionNoticeAmountUseCase: CarelevoUpdateLowInsulinNoticeAmountUseCase,
        carelevoCreateUserSettingInfoUserCase: CarelevoCreateUserSettingInfoUseCase,
        carelevoAlarmInfoUseCase: CarelevoAlarmInfoUseCase,
        requestPatchInfusionInfoUseCase: CarelevoRequestPatchInfusionInfoUseCase
    ): CarelevoPatch {
        return CarelevoPatch(
            carelevoBleController,
            carelevoPatchObserver,
            aapsSchedulers,
            rxBus,
            sp,
            preferences,
            aapsLogger,
            carelevoInfusionInfoMonitorUseCase,
            carelevoPatchInfoMonitorUseCase,
            carelevoUserSettingInfoMonitorUseCase,
            carelevoPatchRptInfusionInfoProcessUseCase,
            carelevoUpdateMaxBolusDoseUseCase,
            carelevoUpdateLowInfusionNoticeAmountUseCase,
            carelevoCreateUserSettingInfoUserCase,
            carelevoAlarmInfoUseCase,
            requestPatchInfusionInfoUseCase
        )
    }
}
