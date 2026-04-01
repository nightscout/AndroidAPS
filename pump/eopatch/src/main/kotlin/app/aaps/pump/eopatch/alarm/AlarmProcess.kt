package app.aaps.pump.eopatch.alarm

import app.aaps.pump.eopatch.alarm.AlarmCode.*
import app.aaps.pump.eopatch.ble.IPatchManager
import app.aaps.pump.eopatch.ble.PatchManagerExecutor
import app.aaps.pump.eopatch.extension.takeOne
import io.reactivex.rxjava3.core.Single

class AlarmProcess(
    val patchManager: IPatchManager,
    val patchManagerExecutor: PatchManagerExecutor
) : IAlarmProcess {

    override fun doAction(code: AlarmCode): Single<Int> = when (code) {
        B001                                                   -> resumeBasalAction()
        A002, A003, A004, A005, A018, A019,
        A020, A022, A023, A034, A041, A042,
        A043, A044, A106, A107, A108, A116,
        A117, A118                                             -> patchDeactivationAction()
        A007                                                   -> inappropriateTemperatureAction()
        A016                                                   -> Single.just(IAlarmProcess.ALARM_HANDLED)
        B000                                                   -> Single.just(IAlarmProcess.ALARM_HANDLED)
        B003, B005, B006, B018                                 -> stopAeBeepAction(code)
        B012                                                   -> Single.just(IAlarmProcess.ALARM_HANDLED)
    }

    private fun resumeBasalAction(): Single<Int> =
        if (patchManagerExecutor.patchConnectionState.isConnected) {
            patchManagerExecutor.resumeBasal()
                .map { it.isSuccess }
                .onErrorReturn { false }
                .map { it.takeOne(IAlarmProcess.ALARM_HANDLED, IAlarmProcess.ALARM_UNHANDLED) }
        } else {
            Single.just(IAlarmProcess.ALARM_UNHANDLED)
        }

    private fun patchDeactivationAction(): Single<Int> =
        if (patchManagerExecutor.patchConnectionState.isConnected) {
            patchManagerExecutor.deactivate(6000, true)
                .map { IAlarmProcess.ALARM_HANDLED }
                .onErrorReturn { IAlarmProcess.ALARM_UNHANDLED }
        } else {
            Single.just(IAlarmProcess.ALARM_UNHANDLED)
        }

    private fun inappropriateTemperatureAction(): Single<Int> =
        if (patchManagerExecutor.patchConnectionState.isConnected) {
            patchManagerExecutor.temperature
                .map { it.temperature }
                .map { temp -> temp in NORMAL_TEMPERATURE_MIN..NORMAL_TEMPERATURE_MAX }
                .filter { it }
                .flatMap {
                    patchManagerExecutor.resumeBasal()
                        .map { it.isSuccess.takeOne(IAlarmProcess.ALARM_HANDLED, IAlarmProcess.ALARM_UNHANDLED) }
                        .toMaybe()
                }
                .defaultIfEmpty(IAlarmProcess.ALARM_UNHANDLED)
        } else {
            Single.just(IAlarmProcess.ALARM_UNHANDLED)
        }

    private fun stopAeBeepAction(alarm: AlarmCode): Single<Int> =
        if (patchManagerExecutor.patchConnectionState.isConnected) {
            patchManagerExecutor.stopAeBeep(alarm.aeCode)
                .map { it.isSuccess }
                .onErrorReturn { false }
                .map { it.takeOne(IAlarmProcess.ALARM_HANDLED, IAlarmProcess.ALARM_HANDLED_BUT_NEED_STOP_BEEP) }
        } else {
            Single.just(IAlarmProcess.ALARM_HANDLED_BUT_NEED_STOP_BEEP)
        }

    companion object {
        const val NORMAL_TEMPERATURE_MIN = 4
        const val NORMAL_TEMPERATURE_MAX = 45
    }
}
