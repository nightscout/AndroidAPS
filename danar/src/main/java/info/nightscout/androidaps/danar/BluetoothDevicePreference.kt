package info.nightscout.androidaps.danar

import kotlin.jvm.JvmOverloads
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.util.AttributeSet
import androidx.preference.ListPreference
import java.util.*

class BluetoothDevicePreference @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : ListPreference(context, attrs) {
    init {
        val entries = Vector<CharSequence>()
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?)?.adapter?.let { bta ->
            for (dev in bta.bondedDevices)
                dev.name?.let { name -> entries.add(name) }
        }
        setEntries(entries.toTypedArray())
        entryValues = entries.toTypedArray()
    }
}