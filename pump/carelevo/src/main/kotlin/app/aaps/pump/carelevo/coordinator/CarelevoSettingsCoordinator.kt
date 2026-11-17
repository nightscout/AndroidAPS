package app.aaps.pump.carelevo.coordinator

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.pump.carelevo.domain.model.ResponseResult
import app.aaps.pump.carelevo.domain.usecase.userSetting.CarelevoDeleteUserSettingInfoUseCase
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

/**
 * The preference-driven patch settings (max bolus, reminders, buzzer) are routed through the AAPS
 * CommandQueue as custom commands ([app.aaps.pump.carelevo.command.CarelevoActivationExecutor]); this
 * coordinator now only owns the shutdown-time settings clear.
 */
@SingleIn(AppScope::class)
class CarelevoSettingsCoordinator @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val deleteUserSettingInfoUseCase: CarelevoDeleteUserSettingInfoUseCase
) {

    // Best-effort DIRECT write, intentionally NOT queued: it runs from the plugin's onStop() during
    // shutdown, where reconnecting via the queue is unreliable and would block teardown. Reminder
    // settings on the patch are non-critical, so a missed clear on stop is acceptable.
    // It runs in the plugin's scope, which onStop() ends right after this call, as it did with RxJava.
    fun clearUserSettings(scope: CoroutineScope) {
        scope.launch {
            val response = withTimeoutOrNull(DELETE_TIMEOUT) { deleteUserSettingInfoUseCase.execute() } ?: return@launch
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

    private companion object {

        val DELETE_TIMEOUT = 3.seconds
    }
}
