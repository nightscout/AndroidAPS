package app.aaps.pump.diaconn.activities

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.ParcelUuid
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView
import androidx.core.app.ActivityCompat
import app.aaps.core.interfaces.pump.BlePreCheck
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.activities.TranslatedDaggerAppCompatActivity
import app.aaps.core.ui.toast.ToastUtils
import app.aaps.core.utils.extensions.safeEnable
import app.aaps.pump.diaconn.R
import app.aaps.pump.diaconn.databinding.DiaconnG8BlescannerActivityBinding
import app.aaps.pump.diaconn.events.EventDiaconnG8DeviceChange
import app.aaps.pump.diaconn.keys.DiaconnStringNonKey
import java.util.UUID
import javax.inject.Inject

class DiaconnG8BLEScanActivity : TranslatedDaggerAppCompatActivity() {

    @Inject lateinit var preferences: Preferences
    @Inject lateinit var blePreCheck: BlePreCheck
    @Inject lateinit var context: Context
    @Inject lateinit var rxBus: RxBus

    private var listAdapter: ListAdapter? = null
    private val devices = ArrayList<BluetoothDeviceItem>()
    private val bluetoothAdapter: BluetoothAdapter? get() = (context.getSystemService(BLUETOOTH_SERVICE) as BluetoothManager?)?.adapter
    private val bluetoothLeScanner: BluetoothLeScanner? get() = bluetoothAdapter?.bluetoothLeScanner
    private val serviceUUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e") // BLE GATT Service UUID

    private lateinit var binding: DiaconnG8BlescannerActivityBinding

    @SuppressLint("SourceLockedOrientationActivity")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DiaconnG8BlescannerActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        title = getString(R.string.diaconn_pairing)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        if (!blePreCheck.prerequisitesCheck(this)) {
            ToastUtils.errorToast(this, getString(app.aaps.core.ui.R.string.need_connect_permission))
            finish()
        }

        listAdapter = ListAdapter()
        binding.bleScannerListview.emptyView = binding.bleScannerNoDevice
        binding.bleScannerListview.adapter = listAdapter
        listAdapter?.notifyDataSetChanged()
    }

    override fun onResume() {
        super.onResume()

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
            bluetoothAdapter?.safeEnable()
            startScan()
        } else {
            ToastUtils.errorToast(context, context.getString(app.aaps.core.ui.R.string.need_connect_permission))
        }
    }

    override fun onPause() {
        super.onPause()
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
            stopScan()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        binding.bleScannerListview.adapter = null
    }

    @SuppressLint("MissingPermission")
    private fun startScan() =
        try {
            val filters: MutableList<ScanFilter> = ArrayList()
            val scanFilter = ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(serviceUUID))
                .build()
            filters.add(scanFilter)

            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()

            bluetoothLeScanner?.startScan(filters, settings, mBleScanCallback)
        } catch (_: IllegalStateException) {
        } // ignore BT not on

    @SuppressLint("MissingPermission")
    private fun stopScan() =
        try {
            bluetoothLeScanner?.stopScan(mBleScanCallback)
        } catch (_: IllegalStateException) {
        } // ignore BT not on

    private fun addBleDevice(device: BluetoothDevice?) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            ToastUtils.errorToast(context, context.getString(app.aaps.core.ui.R.string.need_connect_permission))
            return
        }
        if (device == null || device.name == null || device.name == "") {
            return
        }
        val item = BluetoothDeviceItem(device)
        if (devices.contains(item)) {
            return
        }
        devices.add(item)
        runOnUiThread { listAdapter?.notifyDataSetChanged() }
    }

    private val mBleScanCallback: ScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            addBleDevice(result.device)
        }
    }

    internal inner class ListAdapter : BaseAdapter() {

        override fun getCount(): Int = devices.size
        override fun getItem(i: Int): BluetoothDeviceItem = devices[i]
        override fun getItemId(i: Int): Long = 0

        override fun getView(i: Int, convertView: View?, parent: ViewGroup?): View {
            var v = convertView
            val holder: ViewHolder
            if (v == null) {
                v = View.inflate(applicationContext, R.layout.diaconn_g8_blescanner_item, null)
                holder = ViewHolder(v)
                v.tag = holder
            } else {
                // reuse view if already exists
                holder = v.tag as ViewHolder
            }
            val item = getItem(i)
            holder.setData(item)
            return v!!
        }

        private inner class ViewHolder(v: View) : View.OnClickListener {

            private lateinit var item: BluetoothDeviceItem
            private val name: TextView = v.findViewById(R.id.ble_name)
            private val address: TextView = v.findViewById(R.id.ble_address)

            init {
                v.setOnClickListener(this@ViewHolder)
            }

            @SuppressLint("MissingPermission")
            override fun onClick(v: View) {
                preferences.put(DiaconnStringNonKey.Address, item.device.address)
                preferences.put(DiaconnStringNonKey.Name, name.text.toString())
                item.device.createBond()
                rxBus.send(EventDiaconnG8DeviceChange())
                finish()
            }

            @SuppressLint("MissingPermission")
            fun setData(data: BluetoothDeviceItem) {
                var tTitle = data.device.name
                if (tTitle == null || tTitle == "") {
                    tTitle = "(unknown)"
                }
                name.text = tTitle
                address.text = data.device.address
                item = data
            }

        }
    }

    //
    inner class BluetoothDeviceItem internal constructor(val device: BluetoothDevice) {

        override fun equals(other: Any?): Boolean {
            if (other !is BluetoothDeviceItem) {
                return false
            }
            return stringEquals(device.address, other.device.address)
        }

        private fun stringEquals(arg1: String, arg2: String): Boolean {
            return try {
                arg1 == arg2
            } catch (_: Exception) {
                false
            }
        }

        override fun hashCode(): Int = device.hashCode()
    }
}