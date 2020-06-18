package info.nightscout.androidaps.utils

import android.content.Context
import android.content.Intent
import android.os.Bundle
import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.R
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.interfaces.ProfileFunction
import info.nightscout.androidaps.services.Intents
import info.nightscout.androidaps.utils.resources.ResourceHelper
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class XdripCalibrations @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val resourceHelper: ResourceHelper,
    private val context: Context,
    private val profileFunction: ProfileFunction
) {

    fun sendIntent(bg: Double): Boolean {
        val bundle = Bundle()
        bundle.putDouble("glucose_number", bg)
        bundle.putString("units", if (profileFunction.getUnits() == Constants.MGDL) "mgdl" else "mmol")
        bundle.putLong("timestamp", System.currentTimeMillis())
        val intent = Intent(Intents.ACTION_REMOTE_CALIBRATION)
        intent.putExtras(bundle)
        intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
        context.sendBroadcast(intent)
        val q = context.packageManager.queryBroadcastReceivers(intent, 0)
        return if (q.size < 1) {
            ToastUtils.showToastInUiThread(context, resourceHelper.gs(R.string.xdripnotinstalled))
            aapsLogger.debug(resourceHelper.gs(R.string.xdripnotinstalled))
            false
        } else {
            ToastUtils.showToastInUiThread(context, resourceHelper.gs(R.string.calibrationsent))
            aapsLogger.debug(resourceHelper.gs(R.string.calibrationsent))
            true
        }
    }
}