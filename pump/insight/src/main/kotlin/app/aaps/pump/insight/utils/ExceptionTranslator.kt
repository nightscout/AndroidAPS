package app.aaps.pump.insight.utils

import android.content.Context
import app.aaps.core.ui.toast.ToastUtils
import app.aaps.pump.insight.R
import app.aaps.pump.insight.exceptions.ConnectionFailedException
import app.aaps.pump.insight.exceptions.ConnectionLostException
import app.aaps.pump.insight.exceptions.DisconnectedException
import app.aaps.pump.insight.exceptions.SocketCreationFailedException
import app.aaps.pump.insight.exceptions.TimeoutException
import app.aaps.pump.insight.exceptions.app_layer_errors.MaximumNumberOfBolusTypeAlreadyRunningException
import app.aaps.pump.insight.exceptions.app_layer_errors.NoActiveTBRToCancelException
import app.aaps.pump.insight.exceptions.app_layer_errors.NoActiveTBRToChangeException
import app.aaps.pump.insight.exceptions.app_layer_errors.NoSuchBolusToCancelException
import app.aaps.pump.insight.exceptions.app_layer_errors.PumpAlreadyInThatStateException
import app.aaps.pump.insight.exceptions.app_layer_errors.PumpStoppedException
import app.aaps.pump.insight.exceptions.app_layer_errors.RunModeNotAllowedException
import app.aaps.pump.insight.exceptions.satl_errors.SatlPairingRejectedException

object ExceptionTranslator {

    private val TABLE: MutableMap<Class<out Exception>, Int> = HashMap()
    fun getString(context: Context, exception: Exception): String {
        val res = TABLE[exception.javaClass]
        return if (res == null) exception.javaClass.simpleName else context.getString(res)
    }

    fun makeToast(context: Context, exception: Exception) {
        ToastUtils.longErrorToast(context, getString(context, exception))
    }

    init {
        TABLE[ConnectionFailedException::class.java] = R.string.connection_failed
        TABLE[ConnectionLostException::class.java] = R.string.connection_lost
        TABLE[DisconnectedException::class.java] = app.aaps.core.ui.R.string.disconnected
        TABLE[SatlPairingRejectedException::class.java] = R.string.pairing_rejected
        TABLE[SocketCreationFailedException::class.java] = R.string.socket_creation_failed
        TABLE[TimeoutException::class.java] = R.string.timeout
        TABLE[MaximumNumberOfBolusTypeAlreadyRunningException::class.java] = R.string.maximum_number_of_bolus_type_already_running
        TABLE[NoActiveTBRToCancelException::class.java] = R.string.no_active_tbr_to_cancel
        TABLE[NoActiveTBRToChangeException::class.java] = R.string.no_active_tbr_to_change
        TABLE[NoSuchBolusToCancelException::class.java] = R.string.no_such_bolus_to_cancel
        TABLE[PumpAlreadyInThatStateException::class.java] = R.string.pump_already_in_that_state_exception
        TABLE[PumpStoppedException::class.java] = R.string.pump_stopped
        TABLE[RunModeNotAllowedException::class.java] = R.string.run_mode_not_allowed
    }
}