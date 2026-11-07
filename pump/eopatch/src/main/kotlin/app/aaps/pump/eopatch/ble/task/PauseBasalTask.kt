package app.aaps.pump.eopatch.ble.task

import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.di.ApplicationScope
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.queue.Command
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.pump.eopatch.alarm.AlarmCode
import app.aaps.pump.eopatch.alarm.IAlarmRegistry
import app.aaps.pump.eopatch.core.api.BasalPause
import app.aaps.pump.eopatch.core.response.PatchBooleanResponse
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.functions.Consumer
import io.reactivex.rxjava3.functions.Function
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.minutes

@SingleIn(AppScope::class)
@Inject
class PauseBasalTask(
    private val alarmRegistry: IAlarmRegistry,
    private val commandQueue: CommandQueue,
    private val pumpSync: PumpSync,
    private val uel: UserEntryLogger,
    @ApplicationScope private val appScope: CoroutineScope
) : BolusTask(TaskFunc.PAUSE_BASAL) {

    @Inject lateinit var basalPause: BasalPause

    /**
     * Stops what delivers insulin, then pauses the basal. Built when subscribed, so nothing runs on the caller's thread.
     *
     * It used to signal "ready" through subjects of this singleton. They kept their `true` from the previous pause, so
     * a second pause did not wait for the temp basal cancel. A running SMB was not stopped, and the pause waited for
     * it within the command timeout: a suspend during an SMB failed.
     */
    fun pause(pauseDurationHour: Float, alarmCode: AlarmCode?): Single<PatchBooleanResponse> =
        Single.defer {
            if (pm.patchState.isNormalBasalPaused) return@defer Single.just(PatchBooleanResponse(true))

            enqueue(TaskFunc.UPDATE_CONNECTION)

            stopDelivery().toObservable()
                .flatMap(Function { isReady() })
                // The time limit is for the pause command only, not for the waiting above.
                .concatMapSingle(Function { pauseBasal(pauseDurationHour, alarmCode).timeout(PAUSE_TIME_OUT, TimeUnit.SECONDS) })
                .firstOrError()
                .doOnError(Consumer { e: Throwable -> aapsLogger.error(LTag.PUMPCOMM, e.message ?: "PauseBasalTask error") })
        }

    /** Stops a bolus or an SMB, an extended bolus and a temp basal, and waits until the queue has done it. */
    private fun stopDelivery(): Single<Boolean> = Single.create { emitter ->
        appScope.launch {
            try {
                if (commandQueue.isRunning(Command.CommandType.BOLUS) || commandQueue.isRunning(Command.CommandType.SMB_BOLUS)) {
                    uel.log(Action.CANCEL_BOLUS, Sources.EOPatch2, "", ArrayList())
                    commandQueue.cancelAllBoluses(null)
                    delay(650)
                }
                if (pumpSync.expectedPumpState().extendedBolus != null) {
                    uel.log(Action.CANCEL_EXTENDED_BOLUS, Sources.EOPatch2, "", ArrayList())
                    commandQueue.cancelExtended()
                }
                if (pumpSync.expectedPumpState().temporaryBasal != null) {
                    uel.log(Action.CANCEL_TEMP_BASAL, Sources.EOPatch2, "", ArrayList())
                    commandQueue.cancelTempBasal(enforceNew = true)
                }
                emitter.onSuccess(true)
            } catch (e: Exception) {
                emitter.tryOnError(e)
            }
        }
    }

    private fun pauseBasal(pauseDurationHour: Float, alarmCode: AlarmCode?): Single<PatchBooleanResponse> {
        if (alarmCode == null) {
            return basalPause.pause(pauseDurationHour)
                .doOnSuccess(Consumer { response: PatchBooleanResponse -> this.checkResponse(response) })
                .doOnSuccess(Consumer { onBasalPaused(pauseDurationHour, null) })
        }

        // 정지 알람 발생 시 basal pause 커맨드 전달하지 않음 - 주입 정지 이력만 생성
        onBasalPaused(pauseDurationHour, alarmCode)

        return Single.just(PatchBooleanResponse(true))
    }

    private fun onBasalPaused(pauseDurationHour: Float, alarmCode: AlarmCode?) {
        if (!normalBasalManager.isSuspended()) {
            if (alarmCode != null) {
                patchConfig.updateNormalBasalPausedSilently()
            } else {
                patchConfig.updateNormalBasalPaused(pauseDurationHour)
            }
            normalBasalManager.updateBasalSuspended()

            pm.flushNormalBasalManager()
            pm.flushPatchConfig()

            if ((alarmCode == null || alarmCode.type == AlarmCode.TYPE_ALERT) && pauseDurationHour != 0f) alarmRegistry.add(AlarmCode.B001, (pauseDurationHour * 60).toLong().minutes.inWholeMilliseconds, false).subscribe()
        }

        enqueue(TaskFunc.UPDATE_CONNECTION)
    }

    companion object {

        /** The time the patch has to answer the pause command. */
        private const val PAUSE_TIME_OUT = 10L
    }
}
