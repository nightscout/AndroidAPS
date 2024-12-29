package app.aaps.wear.interaction.actions

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventWearToMobile
import app.aaps.core.interfaces.rx.weardata.EventData
import app.aaps.shared.impl.extensions.safeGetPackageInfo
import app.aaps.wear.R
import dagger.android.DaggerActivity
import javax.inject.Inject

/**
 * Send a snooze request to silence any alarm. Designed to be bound to a button for fast access
 */

class QuickSnoozeActivity : DaggerActivity() {

    @Inject lateinit var rxBus: RxBus

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Toast.makeText(this, R.string.sending_snooze, Toast.LENGTH_LONG).show()
        rxBus.send(EventWearToMobile(EventData.SnoozeAlert(System.currentTimeMillis())))

        val xDripPackageName = "com.eveningoutpost.dexdrip"
        if (isPackageExisted(xDripPackageName)) {
            try {
                val i = Intent()
                i.setClassName(xDripPackageName, "$xDripPackageName.QuickSnooze")
                startActivity(i)
            } catch (e: Exception) {
                Log.e("WEAR", "failed to snooze xDrip: ", e)
            }
        } else {
            Log.d("WEAR", "Package $xDripPackageName not available for snooze")
        }

        finish()
    }

    @Suppress("SameParameterValue")
    private fun isPackageExisted(targetPackage: String): Boolean {
        try {
            packageManager.safeGetPackageInfo(targetPackage, 0)
        } catch (_: PackageManager.NameNotFoundException) {
            return false
        }
        return true
    }
}
