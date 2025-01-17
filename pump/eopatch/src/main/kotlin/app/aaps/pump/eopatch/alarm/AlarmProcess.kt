package app.aaps.pump.eopatch.alarm

import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.pump.eopatch.R
import app.aaps.pump.eopatch.alarm.AlarmCode.A002
import app.aaps.pump.eopatch.alarm.AlarmCode.A003
import app.aaps.pump.eopatch.alarm.AlarmCode.A004
import app.aaps.pump.eopatch.alarm.AlarmCode.A005
import app.aaps.pump.eopatch.alarm.AlarmCode.A007
import app.aaps.pump.eopatch.alarm.AlarmCode.A016
import app.aaps.pump.eopatch.alarm.AlarmCode.A018
import app.aaps.pump.eopatch.alarm.AlarmCode.A019
import app.aaps.pump.eopatch.alarm.AlarmCode.A020
import app.aaps.pump.eopatch.alarm.AlarmCode.A022
import app.aaps.pump.eopatch.alarm.AlarmCode.A023
import app.aaps.pump.eopatch.alarm.AlarmCode.A034
import app.aaps.pump.eopatch.alarm.AlarmCode.A041
import app.aaps.pump.eopatch.alarm.AlarmCode.A042
import app.aaps.pump.eopatch.alarm.AlarmCode.A043
import app.aaps.pump.eopatch.alarm.AlarmCode.A044
import app.aaps.pump.eopatch.alarm.AlarmCode.A106
import app.aaps.pump.eopatch.alarm.AlarmCode.A107
import app.aaps.pump.eopatch.alarm.AlarmCode.A108
import app.aaps.pump.eopatch.alarm.AlarmCode.A116
import app.aaps.pump.eopatch.alarm.AlarmCode.A117
import app.aaps.pump.eopatch.alarm.AlarmCode.A118
import app.aaps.pump.eopatch.alarm.AlarmCode.B000
import app.aaps.pump.eopatch.alarm.AlarmCode.B001
import app.aaps.pump.eopatch.alarm.AlarmCode.B003
import app.aaps.pump.eopatch.alarm.AlarmCode.B005
import app.aaps.pump.eopatch.alarm.AlarmCode.B006
import app.aaps.pump.eopatch.alarm.AlarmCode.B012
import app.aaps.pump.eopatch.alarm.AlarmCode.B018
import app.aaps.pump.eopatch.ble.IPatchManager
import app.aaps.pump.eopatch.ble.PatchManagerExecutor
import app.aaps.pump.eopatch.core.response.BaseResponse
import app.aaps.pump.eopatch.core.response.PatchBooleanResponse
import app.aaps.pump.eopatch.core.response.TemperatureResponse
import app.aaps.pump.eopatch.event.EventDialog
import app.aaps.pump.eopatch.event.EventProgressDialog
import app.aaps.pump.eopatch.extension.takeOne
import app.aaps.pump.eopatch.ui.EopatchActivity
import app.aaps.pump.eopatch.ui.EopatchActivity.Companion.createIntentForCannulaInsertionError
import app.aaps.pump.eopatch.ui.EopatchActivity.Companion.createIntentForCheckConnection
import app.aaps.pump.eopatch.ui.EopatchActivity.Companion.createIntentForDiscarded
import app.aaps.pump.eopatch.ui.dialogs.CommonDialog
import io.reactivex.rxjava3.core.Single
import java.util.concurrent.Callable

class AlarmProcess(val patchManager: IPatchManager, val patchManagerExecutor: PatchManagerExecutor, val rxBus: RxBus) : IAlarmProcess {

    override fun doAction(context: Context, code: AlarmCode): Single<Int> {
        return when (code) {
            B001                   -> resumeBasalAction(context)
            A002, A003, A004, A005, A018, A019,
            A020, A022, A023, A034, A041, A042,
            A043, A044, A106, A107, A108, A116,
            A117, A118             -> patchDeactivationAction(context)

            A007                   -> inappropriateTemperatureAction(context)
            A016                   -> needleInsertionErrorAction(context)
            B000                   -> Single.just(IAlarmProcess.ALARM_HANDLED)
            B003, B005, B006, B018 -> stopAeBeepAction(code)
            B012                   -> Single.just(IAlarmProcess.ALARM_HANDLED)
        }
    }

    private fun startActivityWithSingleTop(context: Context, intent: Intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        context.startActivity(intent)
    }

    private fun showCommunicationFailedDialog(onConfirmed: Runnable) {
        val dialog = CommonDialog().apply {
            title = R.string.patch_communication_failed
            message = R.string.patch_communication_check_helper_1
            positiveBtn = R.string.string_communication_check
            positiveListener = DialogInterface.OnClickListener { _, _ ->
                onConfirmed.run()
                dismiss()
            }
        }

        rxBus.send(EventDialog(dialog, true))
    }

    private fun actionWithPatchCheckConnection(context: Context, action: Callable<Single<Int>>): Single<Int> {
        return if (patchManagerExecutor.patchConnectionState.isConnected) {
            try {
                action.call()
            } catch (_: Exception) {
                Single.just(IAlarmProcess.ALARM_PAUSE)
            }
        } else {
            Single.fromCallable {
                showCommunicationFailedDialog {
                    startActivityWithSingleTop(
                        context,
                        createIntentForCheckConnection(context, goHomeAfterDiscard = true, forceDiscard = true, isAlarmHandling = true)
                    )
                }
                IAlarmProcess.ALARM_PAUSE
            }
        }
    }

    private fun resumeBasalAction(context: Context): Single<Int> {
        return actionWithPatchCheckConnection(context) {
            patchManagerExecutor.resumeBasal()
                .map { obj: BaseResponse -> obj.isSuccess }
                .onErrorReturn { false }
                .flatMap { Single.just(it.takeOne(IAlarmProcess.ALARM_HANDLED, IAlarmProcess.ALARM_UNHANDLED)) }
        }
    }

    private fun patchDeactivationAction(context: Context): Single<Int> {
        return actionWithPatchCheckConnection(context) {
            rxBus.send(EventProgressDialog(true, R.string.string_in_progress))
            patchManagerExecutor.deactivate(6000, true)
                .doFinally {
                    rxBus.send(EventProgressDialog(false, R.string.string_in_progress))
                    startActivityWithSingleTop(context, createIntentForDiscarded(context))
                }
                .flatMap { Single.just(IAlarmProcess.ALARM_HANDLED) }
        }
    }

    private fun needleInsertionErrorAction(context: Context): Single<Int> {
        return Single.fromCallable {
            startActivityWithSingleTop(context, createIntentForCannulaInsertionError(context))
            IAlarmProcess.ALARM_HANDLED
        }
    }

    private fun inappropriateTemperatureAction(context: Context): Single<Int> {
        return actionWithPatchCheckConnection(context) {
            patchManagerExecutor.temperature
                .map(TemperatureResponse::getTemperature)
                .map { temp -> (temp >= EopatchActivity.NORMAL_TEMPERATURE_MIN && temp <= EopatchActivity.NORMAL_TEMPERATURE_MAX) }
                .filter { ok -> ok }
                .flatMap { patchManagerExecutor.resumeBasal().map { it.isSuccess.takeOne(IAlarmProcess.ALARM_HANDLED, IAlarmProcess.ALARM_UNHANDLED) }.toMaybe() }
                .defaultIfEmpty(IAlarmProcess.ALARM_UNHANDLED)
        }
    }

    private fun stopAeBeepAction(alarm: AlarmCode): Single<Int> {
        return if (patchManagerExecutor.patchConnectionState.isConnected) {
            patchManagerExecutor.stopAeBeep(alarm.aeCode)
                .map { obj: PatchBooleanResponse -> obj.isSuccess }
                .onErrorReturn { false }
                .flatMap { Single.just(it.takeOne(IAlarmProcess.ALARM_HANDLED, IAlarmProcess.ALARM_HANDLED_BUT_NEED_STOP_BEEP)) }
        } else {
            Single.just(IAlarmProcess.ALARM_HANDLED_BUT_NEED_STOP_BEEP)
        }
    }
}