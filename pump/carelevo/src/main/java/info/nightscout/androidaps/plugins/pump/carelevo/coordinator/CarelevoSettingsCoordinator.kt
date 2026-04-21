package info.nightscout.androidaps.plugins.pump.carelevo.coordinator

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.keys.DoubleKey
import app.aaps.core.keys.interfaces.Preferences
import info.nightscout.androidaps.plugins.pump.carelevo.common.CarelevoPatch
import info.nightscout.androidaps.plugins.pump.carelevo.common.keys.CarelevoBooleanPreferenceKey
import info.nightscout.androidaps.plugins.pump.carelevo.common.keys.CarelevoIntPreferenceKey
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.ResponseResult
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.patch.CarelevoPatchTimeZoneUpdateUseCase
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.patch.model.CarelevoPatchTimeZoneRequestModel
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.userSetting.CarelevoDeleteUserSettingInfoUseCase
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.userSetting.CarelevoPatchBuzzModifyUseCase
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.userSetting.CarelevoPatchExpiredThresholdModifyUseCase
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.userSetting.CarelevoUpdateLowInsulinNoticeAmountUseCase
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.userSetting.CarelevoUpdateMaxBolusDoseUseCase
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.userSetting.model.CarelevoPatchBuzzRequestModel
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.userSetting.model.CarelevoPatchExpiredThresholdModifyRequestModel
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.userSetting.model.CarelevoUserSettingInfoRequestModel
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.jvm.optionals.getOrNull

@Singleton
class CarelevoSettingsCoordinator @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val aapsSchedulers: AapsSchedulers,
    private val preferences: Preferences,
    private val sp: SP,
    private val carelevoPatch: CarelevoPatch,
    private val updateMaxBolusDoseUseCase: CarelevoUpdateMaxBolusDoseUseCase,
    private val updateLowInsulinNoticeAmountUseCase: CarelevoUpdateLowInsulinNoticeAmountUseCase,
    private val deleteUserSettingInfoUseCase: CarelevoDeleteUserSettingInfoUseCase,
    private val carelevoPatchTimeZoneUpdateUseCase: CarelevoPatchTimeZoneUpdateUseCase,
    private val carelevoPatchExpiredThresholdModifyUseCase: CarelevoPatchExpiredThresholdModifyUseCase,
    private val carelevoPatchBuzzModifyUseCase: CarelevoPatchBuzzModifyUseCase
) {

    fun updateMaxBolusDose(
        pluginDisposable: CompositeDisposable,
        onLastDataUpdated: () -> Unit
    ) {
        val maxBolusDose = preferences.get(DoubleKey.SafetyMaxBolus)
        val patchState = carelevoPatch.patchState.value?.getOrNull()
        pluginDisposable += updateMaxBolusDoseUseCase.execute(
            CarelevoUserSettingInfoRequestModel(
                patchState = patchState,
                maxBolusDose = maxBolusDose
            )
        )
            .observeOn(aapsSchedulers.io)
            .subscribeOn(aapsSchedulers.io)
            .timeout(3000L, TimeUnit.MILLISECONDS)
            .subscribe { response ->
                when (response) {
                    is ResponseResult.Success -> {
                        onLastDataUpdated()
                        aapsLogger.debug(LTag.PUMPCOMM, "updateMaxBolusDose.success")
                    }

                    is ResponseResult.Error -> {
                        aapsLogger.debug(LTag.PUMPCOMM, "updateMaxBolusDose.responseError error=${response.e}")
                    }

                    else -> {
                        aapsLogger.debug(LTag.PUMPCOMM, "updateMaxBolusDose.failure")
                    }
                }
            }
    }

    fun updateLowInsulinNoticeAmount(
        pluginDisposable: CompositeDisposable,
        onLastDataUpdated: () -> Unit
    ) {
        val lowInsulinNoticeAmount = sp.getInt(CarelevoIntPreferenceKey.CARELEVO_LOW_INSULIN_EXPIRATION_REMINDER_HOURS.key, 0)
        val patchState = carelevoPatch.patchState.value?.getOrNull()

        if (lowInsulinNoticeAmount == 0) {
            aapsLogger.debug(LTag.PUMPCOMM, "updateLowInsulinNoticeAmount.skip reason=zero")
            return
        }

        pluginDisposable += updateLowInsulinNoticeAmountUseCase.execute(
            CarelevoUserSettingInfoRequestModel(
                patchState = patchState,
                lowInsulinNoticeAmount = lowInsulinNoticeAmount
            )
        )
            .observeOn(aapsSchedulers.io)
            .timeout(3000L, TimeUnit.MILLISECONDS)
            .subscribe { response ->
                when (response) {
                    is ResponseResult.Success -> {
                        onLastDataUpdated()
                        aapsLogger.debug(LTag.PUMPCOMM, "updateLowInsulinNoticeAmount.success")
                    }

                    is ResponseResult.Error -> {
                        aapsLogger.debug(LTag.PUMPCOMM, "updateLowInsulinNoticeAmount.responseError error=${response.e}")
                    }

                    else -> {
                        aapsLogger.debug(LTag.PUMPCOMM, "updateLowInsulinNoticeAmount.failure")
                    }
                }
            }
    }

    fun updatePatchExpiredThreshold(
        pluginDisposable: CompositeDisposable,
        onLastDataUpdated: () -> Unit
    ) {
        val patchExpiredThreshold = sp.getInt(CarelevoIntPreferenceKey.CARELEVO_PATCH_EXPIRATION_REMINDER_HOURS.key, 0)
        val patchState = carelevoPatch.patchState.value?.getOrNull()

        val request = CarelevoPatchExpiredThresholdModifyRequestModel(
            patchState = patchState,
            patchExpiredThreshold = patchExpiredThreshold
        )

        pluginDisposable += carelevoPatchExpiredThresholdModifyUseCase.execute(request)
            .observeOn(aapsSchedulers.io)
            .timeout(3000L, TimeUnit.MILLISECONDS)
            .subscribe { response ->
                when (response) {
                    is ResponseResult.Success -> {
                        onLastDataUpdated()
                        aapsLogger.debug(LTag.PUMPCOMM, "updatePatchExpiredThreshold.success")
                    }

                    is ResponseResult.Error -> {
                        aapsLogger.debug(LTag.PUMPCOMM, "updatePatchExpiredThreshold.responseError error=${response.e}")
                    }

                    else -> {
                        aapsLogger.debug(LTag.PUMPCOMM, "updatePatchExpiredThreshold.failure")
                    }
                }
            }
    }

    fun updatePatchBuzzer(
        pluginDisposable: CompositeDisposable,
        onLastDataUpdated: () -> Unit
    ) {
        val isBuzzOn = sp.getBoolean(CarelevoBooleanPreferenceKey.CARELEVO_BUZZER_REMINDER.key, false)
        val patchState = carelevoPatch.patchState.value?.getOrNull()

        val request = CarelevoPatchBuzzRequestModel(
            patchState = patchState,
            settingsAlarmBuzz = isBuzzOn
        )

        pluginDisposable += carelevoPatchBuzzModifyUseCase.execute(request)
            .observeOn(aapsSchedulers.io)
            .timeout(3000L, TimeUnit.MILLISECONDS)
            .subscribe { response ->
                when (response) {
                    is ResponseResult.Success -> {
                        onLastDataUpdated()
                        aapsLogger.debug(LTag.PUMPCOMM, "updatePatchBuzzer.success")
                    }

                    is ResponseResult.Error -> {
                        aapsLogger.debug(LTag.PUMPCOMM, "updatePatchBuzzer.responseError error=${response.e}")
                    }

                    else -> {
                        aapsLogger.debug(LTag.PUMPCOMM, "updatePatchBuzzer.failure")
                    }
                }
            }
    }

    fun clearUserSettings(pluginDisposable: CompositeDisposable) {
        pluginDisposable += deleteUserSettingInfoUseCase.execute()
            .observeOn(aapsSchedulers.io)
            .subscribeOn(aapsSchedulers.io)
            .timeout(3000L, TimeUnit.MILLISECONDS)
            .subscribe { response ->
                when (response) {
                    is ResponseResult.Success -> {
                        aapsLogger.debug(LTag.PUMPCOMM, "deleteUserSettingInfo.success")
                    }

                    is ResponseResult.Error -> {
                        aapsLogger.debug(LTag.PUMPCOMM, "deleteUserSettingInfo.responseError error=${response.e}")
                    }

                    else -> {
                        aapsLogger.debug(LTag.PUMPCOMM, "deleteUserSettingInfo.failure")
                    }
                }
            }
    }

    fun timezoneOrDSTChanged(
        pluginDisposable: CompositeDisposable,
        onLastDataUpdated: () -> Unit
    ) {
        val insulin = carelevoPatch.patchInfo.value?.getOrNull()?.insulinRemain?.toInt() ?: 0
        aapsLogger.debug(LTag.PUMPCOMM, "timezoneOrDSTChanged.start insulin=$insulin")
        pluginDisposable += carelevoPatchTimeZoneUpdateUseCase.execute(
            CarelevoPatchTimeZoneRequestModel(insulinAmount = insulin)
        )
            .observeOn(aapsSchedulers.main)
            .subscribeOn(aapsSchedulers.io)
            .timeout(3000L, TimeUnit.MILLISECONDS)
            .doOnSuccess {
                onLastDataUpdated()
                aapsLogger.debug(LTag.PUMPCOMM, "timezoneOrDSTChanged.success")
            }
            .doOnError { e ->
                aapsLogger.error(LTag.PUMPCOMM, "timezoneOrDSTChanged.error", e)
            }
            .subscribe()
    }
}
