package app.aaps.pump.carelevo.coordinator

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.pump.carelevo.domain.model.ResponseResult
import app.aaps.pump.carelevo.domain.usecase.userSetting.CarelevoDeleteUserSettingInfoUseCase
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The preference-driven patch settings (max bolus, reminders, buzzer) are routed through the AAPS
 * CommandQueue as custom commands ([app.aaps.pump.carelevo.command.CarelevoActivationExecutor]); this
 * coordinator now only owns the shutdown-time settings clear.
 */
@Singleton
class CarelevoSettingsCoordinator @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val aapsSchedulers: AapsSchedulers,
    private val deleteUserSettingInfoUseCase: CarelevoDeleteUserSettingInfoUseCase
) {

    // Best-effort DIRECT write, intentionally NOT queued: it runs from the plugin's onStop() during
    // shutdown, where reconnecting via the queue is unreliable and would block teardown. Reminder
    // settings on the patch are non-critical, so a missed clear on stop is acceptable.
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

                    is ResponseResult.Error   -> {
                        aapsLogger.debug(LTag.PUMPCOMM, "deleteUserSettingInfo.responseError error=${response.e}")
                    }

                    else                      -> {
                        aapsLogger.debug(LTag.PUMPCOMM, "deleteUserSettingInfo.failure")
                    }
                }
            }
    }
}
