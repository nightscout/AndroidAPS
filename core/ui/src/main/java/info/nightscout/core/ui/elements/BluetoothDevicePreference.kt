package info.nightscout.core.ui.elements

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.AttributeSet
import androidx.core.app.ActivityCompat
import androidx.preference.ListPreference
import info.nightscout.core.ui.R
import info.nightscout.core.ui.toast.ToastUtils
import java.util.Vector

class BluetoothDevicePreference @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : ListPreference(context, attrs) {

    init {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
            val devices = Vector<CharSequence>()
            (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?)?.adapter?.let { bta ->
                for (dev in bta.bondedDevices)
                    dev.name?.let { name -> devices.add(name) }
            }
            entries = devices.toTypedArray()
            entryValues = devices.toTypedArray()
        } else {
            entries = emptyArray()
            entryValues = emptyArray()
            ToastUtils.errorToast(context, context.getString(R.string.need_connect_permission))
        }
    }
}