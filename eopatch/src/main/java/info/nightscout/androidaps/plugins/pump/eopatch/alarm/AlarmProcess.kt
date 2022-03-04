package info.nightscout.androidaps.plugins.pump.eopatch.alarm

import android.content.Context
import android.content.DialogInterface
import info.nightscout.androidaps.plugins.pump.eopatch.ui.EopatchActivity.Companion.createIntentForCheckConnection
import info.nightscout.androidaps.plugins.pump.eopatch.ui.EopatchActivity.Companion.createIntentForDiscarded
import info.nightscout.androidaps.plugins.pump.eopatch.ui.EopatchActivity.Companion.createIntentForCannulaInsertionError
import info.nightscout.androidaps.plugins.pump.eopatch.ble.IPatchManager
import info.nightscout.androidaps.plugins.pump.eopatch.alarm.AlarmCode.*
import android.content.Intent
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.plugins.pump.eopatch.R
import info.nightscout.androidaps.plugins.pump.eopatch.ui.EopatchActivity
import info.nightscout.androidaps.plugins.pump.eopatch.core.response.BaseResponse
import info.nightscout.androidaps.plugins.pump.eopatch.core.response.TemperatureResponse
import info.nightscout.androidaps.plugins.pump.eopatch.event.EventDialog
import info.nightscout.androidaps.plugins.pump.eopatch.event.EventProgressDialog
import info.nightscout.androidaps.plugins.pump.eopatch.extension.takeOne
import info.nightscout.androidaps.plugins.pump.eopatch.ui.dialogs.CommonDialog
import io.reactivex.Single
import java.lang.Exception
import java.util.concurrent.Callable

interface IAlarmProcess {
    fun doAction(context: Context, code: AlarmCode): Single<Int>

    companion object {
        const val ALARM_UNHANDLED = 0
        const val ALARM_PAUSE = 1
        const val ALARM_HANDLED = 2
    }
}

class AlarmProcess(val patchManager: IPatchManager, val rxBus: RxBus) : IAlarmProcess {
    override fun doAction(context: Context, code: AlarmCode): Single<Int> {
        return when (code) {
            B001 -> resumeBasalAction(context)
            A002, A003, A004, A005, A018, A019,
            A020, A022, A023, A034, A041, A042,
            A043, A044, A106, A107, A108, A116,
            A117, A118 -> patchDeactivationAction(context)
            A007 -> inappropriateTemperatureAction(context)
            A016 -> needleInsertionErrorAction(context)
            B003, B018 -> Single.just(IAlarmProcess.ALARM_HANDLED)
            B005, B006 ->  Single.just(IAlarmProcess.ALARM_HANDLED)
            B012 -> Single.just(IAlarmProcess.ALARM_HANDLED)
        }
    }

    private fun startActivityWithSingleTop(context: Context, intent: Intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
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
        return if (patchManager.patchConnectionState.isConnected) {
            try {
                action.call()
            } catch (e: Exception) {
                Single.just(IAlarmProcess.ALARM_PAUSE)
            }
        } else {
            Single.fromCallable {
                showCommunicationFailedDialog {
                    startActivityWithSingleTop(context,
                        createIntentForCheckConnection(context, goHomeAfterDiscard = true, forceDiscard = true))
                }
                IAlarmProcess.ALARM_PAUSE
            }
        }
    }

    private fun resumeBasalAction(context: Context): Single<Int> {
        return actionWithPatchCheckConnection(context) {
            patchManager.resumeBasal()
                .map { obj: BaseResponse -> obj.isSuccess }
                .flatMap { Single.just(it.takeOne(IAlarmProcess.ALARM_HANDLED, IAlarmProcess.ALARM_UNHANDLED)) }
        }
    }

    private fun patchDeactivationAction(context: Context): Single<Int> {
        return actionWithPatchCheckConnection(context) {
            rxBus.send(EventProgressDialog(true, R.string.string_in_progress))
            patchManager.deactivate(6000, true)
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
            patchManager.temperature
                .map(TemperatureResponse::getTemperature)
                .map { temp -> (temp >= EopatchActivity.NORMAL_TEMPERATURE_MIN && temp <= EopatchActivity.NORMAL_TEMPERATURE_MAX) }
                .filter{ok -> ok}
                .flatMap { patchManager.resumeBasal().map { it.isSuccess.takeOne(IAlarmProcess.ALARM_HANDLED, IAlarmProcess.ALARM_UNHANDLED) }.toMaybe() }
                .defaultIfEmpty(IAlarmProcess.ALARM_UNHANDLED)
                .toSingle()
        }
    }
}