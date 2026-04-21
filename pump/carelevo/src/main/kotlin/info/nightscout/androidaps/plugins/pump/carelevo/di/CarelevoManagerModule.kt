package info.nightscout.androidaps.plugins.pump.carelevo.di

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.keys.interfaces.Preferences
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import info.nightscout.androidaps.plugins.pump.carelevo.ble.core.CarelevoBleController
import info.nightscout.androidaps.plugins.pump.carelevo.common.CarelevoPatch
import info.nightscout.androidaps.plugins.pump.carelevo.domain.CarelevoPatchObserver
import info.nightscout.androidaps.plugins.pump.carelevo.domain.repository.CarelevoBasalRepository
import info.nightscout.androidaps.plugins.pump.carelevo.domain.repository.CarelevoBolusRepository
import info.nightscout.androidaps.plugins.pump.carelevo.domain.repository.CarelevoPatchRepository
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.alarm.CarelevoAlarmInfoUseCase
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.infusion.CarelevoInfusionInfoMonitorUseCase
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.patch.CarelevoPatchInfoMonitorUseCase
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.patch.CarelevoPatchRptInfusionInfoProcessUseCase
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.patch.CarelevoRequestPatchInfusionInfoUseCase
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.userSetting.CarelevoCreateUserSettingInfoUseCase
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.userSetting.CarelevoUpdateLowInsulinNoticeAmountUseCase
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.userSetting.CarelevoUpdateMaxBolusDoseUseCase
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.userSetting.CarelevoUserSettingInfoMonitorUseCase
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
