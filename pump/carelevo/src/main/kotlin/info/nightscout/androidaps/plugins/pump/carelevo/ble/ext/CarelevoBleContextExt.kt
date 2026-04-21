package info.nightscout.androidaps.plugins.pump.carelevo.ble.ext

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

internal fun Context.hasPermission(permissionType : String) : Boolean {
    return ContextCompat.checkSelfPermission(this, permissionType) == PackageManager.PERMISSION_GRANTED
}