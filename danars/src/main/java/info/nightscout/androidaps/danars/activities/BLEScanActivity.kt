package info.nightscout.androidaps.danars.activities

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.os.Handler
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView
import info.nightscout.androidaps.activities.NoSplashAppCompatActivity
import info.nightscout.androidaps.danars.R
import info.nightscout.androidaps.danars.databinding.DanarsBlescannerActivityBinding
import info.nightscout.androidaps.danars.events.EventDanaRSDeviceChange
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.pump.common.ble.BlePreCheck
import info.nightscout.androidaps.utils.sharedPreferences.SP
import java.util.*
import java.util.regex.Pattern
import javax.inject.Inject

class BLEScanActivity : NoSplashAppCompatActivity() {

    @Inject lateinit var sp: SP
    @Inject lateinit var rxBus: RxBusWrapper
    @Inject lateinit var blePreCheck: BlePreCheck

    private var listAdapter: ListAdapter? = null
    private val devices = ArrayList<BluetoothDeviceItem>()
    private var bluetoothLeScanner: BluetoothLeScanner? = null

    private lateinit var binding: DanarsBlescannerActivityBinding

    @SuppressLint("SourceLockedOrientationActivity")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DanarsBlescannerActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        blePreCheck.prerequisitesCheck(this)

        listAdapter = ListAdapter()
        binding.blescannerListview.emptyView = binding.blescannerNodevice
        binding.blescannerListview.adapter = listAdapter
        listAdapter?.notifyDataSetChanged()
    }

    override fun onResume() {
        super.onResume()

        BluetoothAdapter.getDefaultAdapter()?.let { bluetoothAdapter ->
            if (!bluetoothAdapter.isEnabled) bluetoothAdapter.enable()
            bluetoothLeScanner = bluetoothAdapter.bluetoothLeScanner
            startScan()
        }
    }

    override fun onPause() {
        super.onPause()
        stopScan()
    }

    private fun startScan() =
        try {
            bluetoothLeScanner?.startScan(mBleScanCallback)
        } catch (e: IllegalStateException) {
        } // ignore BT not on

    private fun stopScan() =
        try {
            bluetoothLeScanner?.stopScan(mBleScanCallback)
        } catch (e: IllegalStateException) {
        } // ignore BT not on

    private fun addBleDevice(device: BluetoothDevice?) {
        if (device == null || device.name == null || device.name == "") {
            return
        }
        val item = BluetoothDeviceItem(device)
        if (!isSNCheck(device.name) || devices.contains(item)) {
            return
        }
        devices.add(item)
        Handler().post { listAdapter!!.notifyDataSetChanged() }
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
                sp.putString(R.string.key_danars_address, item.device.address)
                sp.putString(R.string.key_danars_name, name.text.toString())
                item.device.createBond()
                rxBus.send(EventDanaRSDeviceChange())
                finish()
            }

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
            return stringEquals(device.address, other.device.address)
        }

        private fun stringEquals(arg1: String, arg2: String): Boolean {
            return try {
                arg1 == arg2
            } catch (e: Exception) {
                false
            }
        }

        override fun hashCode(): Int = device.hashCode()
    }

    private fun isSNCheck(sn: String?): Boolean {
        val regex = "^([a-zA-Z]{3})([0-9]{5})([a-zA-Z]{2})$"
        val p = Pattern.compile(regex)
        val m = p.matcher(sn)
        return m.matches()
    }
}