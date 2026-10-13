package app.aaps.pump.danars.activities

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView
import androidx.core.app.ActivityCompat
import app.aaps.core.interfaces.pump.BlePreCheck
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.activities.TranslatedDaggerAppCompatActivity
import app.aaps.core.ui.toast.ToastUtils
import app.aaps.core.utils.extensions.safeEnable
import app.aaps.pump.dana.keys.DanaStringKey
import app.aaps.pump.danars.R
import app.aaps.pump.danars.databinding.DanarsBlescannerActivityBinding
import app.aaps.pump.danars.events.EventDanaRSDeviceChange
import java.util.regex.Pattern
import javax.inject.Inject

class BLEScanActivity : TranslatedDaggerAppCompatActivity() {

    @Inject lateinit var preferences: Preferences
    @Inject lateinit var blePreCheck: BlePreCheck
    @Inject lateinit var context: Context
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var rh: ResourceHelper

    private var listAdapter: ListAdapter? = null
    private val devices = ArrayList<BluetoothDeviceItem>()
    private val bluetoothAdapter: BluetoothAdapter? get() = (context.getSystemService(BLUETOOTH_SERVICE) as BluetoothManager?)?.adapter
    private val bluetoothLeScanner: BluetoothLeScanner? get() = bluetoothAdapter?.bluetoothLeScanner

    private lateinit var binding: DanarsBlescannerActivityBinding

    @SuppressLint("SourceLockedOrientationActivity")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DanarsBlescannerActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        title = rh.gs(app.aaps.pump.dana.R.string.danars_pairing)
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

        bluetoothAdapter?.safeEnable()
        startScan()
    }

    override fun onPause() {
        super.onPause()
        stopScan()
    }

    override fun onDestroy() {
        super.onDestroy()
        binding.bleScannerListview.adapter = null
    }

    @SuppressLint("MissingPermission")
    private fun startScan() =
        try {
            bluetoothLeScanner?.startScan(mBleScanCallback)
        } catch (_: IllegalStateException) {
        } // ignore BT not on

    @SuppressLint("MissingPermission")
    private fun stopScan() =
        try {
            bluetoothLeScanner?.stopScan(mBleScanCallback)
        } catch (_: IllegalStateException) {
        } // ignore BT not on

    @SuppressLint("MissingPermission")
    private fun addBleDevice(device: BluetoothDevice?) {
        if (device == null || device.name == null || device.name == "") {
            return
        }
        val item = BluetoothDeviceItem(device)
        if (!isSNCheck(device.name) || devices.contains(item)) {
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
                v = View.inflate(applicationContext, R.layout.danars_blescanner_item, null)
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

            override fun onClick(v: View) {
                preferences.put(DanaStringKey.MacAddress, item.device.address)
                preferences.put(DanaStringKey.RsName, name.text.toString())
                if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                    item.device.createBond()
                    rxBus.send(EventDanaRSDeviceChange())
                } else {
                    ToastUtils.errorToast(context, context.getString(app.aaps.core.ui.R.string.need_connect_permission))
                }
                finish()
            }

            @SuppressLint("MissingPermission")
            fun setData(data: BluetoothDeviceItem) {
                var tTitle = data.device.name
                if (tTitle == null || tTitle == "") {
                    tTitle = "(unknown)"
                } else if (tTitle.length > 10) {
                    tTitle = tTitle.substring(0, 10)
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
            return device.address == other.device.address
        }

        override fun hashCode(): Int = device.hashCode()
    }

    @Suppress("RegExpSimplifiable")
    private fun isSNCheck(sn: String): Boolean {
        val regex = "^([a-zA-Z]{3})([0-9]{5})([a-zA-Z]{2})$"
        val p = Pattern.compile(regex)
        val m = p.matcher(sn)
        return m.matches()
    }
}