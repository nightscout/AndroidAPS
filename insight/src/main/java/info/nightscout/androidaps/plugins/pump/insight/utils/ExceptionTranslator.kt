package info.nightscout.androidaps.plugins.pump.insight.utils

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import info.nightscout.androidaps.insight.R
import info.nightscout.androidaps.plugins.pump.insight.exceptions.ConnectionFailedException
import info.nightscout.androidaps.plugins.pump.insight.exceptions.ConnectionLostException
import info.nightscout.androidaps.plugins.pump.insight.exceptions.DisconnectedException
import info.nightscout.androidaps.plugins.pump.insight.exceptions.SocketCreationFailedException
import info.nightscout.androidaps.plugins.pump.insight.exceptions.TimeoutException
import info.nightscout.androidaps.plugins.pump.insight.exceptions.app_layer_errors.*
import info.nightscout.androidaps.plugins.pump.insight.exceptions.satl_errors.SatlPairingRejectedException
import java.util.*

object ExceptionTranslator {

    private val TABLE: MutableMap<Class<out Exception>, Int> = HashMap()
    fun getString(context: Context, exception: Exception): String {
        val res = TABLE[exception.javaClass]
        return if (res == null) exception.javaClass.simpleName else context.getString(res)
    }

    fun makeToast(context: Context, exception: Exception) {
        Handler(Looper.getMainLooper()).post { Toast.makeText(context, getString(context, exception), Toast.LENGTH_LONG).show() }
    }

    init {
        TABLE[ConnectionFailedException::class.java] = R.string.connection_failed
        TABLE[ConnectionLostException::class.java] = R.string.connection_lost
        TABLE[DisconnectedException::class.java] = R.string.disconnected
        TABLE[SatlPairingRejectedException::class.java] = R.string.pairing_rejected
        TABLE[SocketCreationFailedException::class.java] = R.string.socket_creation_failed
        TABLE[TimeoutException::class.java] = R.string.timeout
        TABLE[MaximumNumberOfBolusTypeAlreadyRunningException::class.java] = R.string.maximum_number_of_bolus_type_already_running
        TABLE[NoActiveTBRToCanceLException::class.java] = R.string.no_active_tbr_to_cancel
        TABLE[NoActiveTBRToChangeException::class.java] = R.string.no_active_tbr_to_change
        TABLE[NoSuchBolusToCancelException::class.java] = R.string.no_such_bolus_to_cancel
        TABLE[PumpAlreadyInThatStateException::class.java] = R.string.pump_already_in_that_state_exception
        TABLE[PumpStoppedException::class.java] = R.string.pump_stopped
        TABLE[RunModeNotAllowedException::class.java] = R.string.run_mode_not_allowed
    }
}