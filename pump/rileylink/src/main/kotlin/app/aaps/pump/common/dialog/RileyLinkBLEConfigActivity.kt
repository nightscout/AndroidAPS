package app.aaps.pump.common.dialog

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
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.ParcelUuid
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.AdapterView.OnItemClickListener
import android.widget.BaseAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.pump.BlePreCheck
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.activities.TranslatedDaggerAppCompatActivity
import app.aaps.core.ui.dialogs.OKDialog
import app.aaps.pump.common.hw.rileylink.R
import app.aaps.pump.common.hw.rileylink.RileyLinkConst
import app.aaps.pump.common.hw.rileylink.RileyLinkUtil
import app.aaps.pump.common.hw.rileylink.ble.data.GattAttributes
import app.aaps.pump.common.hw.rileylink.databinding.RileyLinkBleConfigActivityBinding
import app.aaps.pump.common.hw.rileylink.defs.RileyLinkPumpDevice
import app.aaps.pump.common.hw.rileylink.keys.RileyLinkStringKey
import app.aaps.pump.common.hw.rileylink.keys.RileyLinkStringPreferenceKey
import org.apache.commons.lang3.StringUtils
import java.util.Locale
import javax.inject.Inject

// IMPORTANT: This activity needs to be called from RileyLinkSelectPreference (see pref_medtronic.xml as example)
class RileyLinkBLEConfigActivity : TranslatedDaggerAppCompatActivity() {

    @Inject lateinit var preferences: Preferences
    @Inject lateinit var blePreCheck: BlePreCheck
    @Inject lateinit var rileyLinkUtil: RileyLinkUtil
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var context: Context
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var aapsLogger: AAPSLogger

    private val handler = Handler(HandlerThread(this::class.simpleName + "Handler").also { it.start() }.looper)
    private val bluetoothAdapter: BluetoothAdapter? get() = (context.getSystemService(BLUETOOTH_SERVICE) as BluetoothManager?)?.adapter
    private var deviceListAdapter = LeDeviceListAdapter()
    private var settings: ScanSettings? = null
    private var filters: List<ScanFilter>? = null
    private var bleScanner: BluetoothLeScanner? = null
    private var scanning = false

    private lateinit var binding: RileyLinkBleConfigActivityBinding
    private val stopScanAfterTimeoutRunnable = Runnable {
        if (scanning) {
            stopLeDeviceScan()
            rileyLinkUtil.sendBroadcastMessage(RileyLinkConst.Intents.RileyLinkNewAddressSet) // Reconnect current RL
        }
    }

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = RileyLinkBleConfigActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        title = rh.gs(R.string.rileylink_configuration)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        // Initializes Bluetooth adapter.
        binding.rileyLinkBleConfigScanDeviceList.adapter = deviceListAdapter
        binding.rileyLinkBleConfigScanDeviceList.onItemClickListener = OnItemClickListener { _: AdapterView<*>?, view: View, _: Int, _: Long ->
            // stop scanning if still active
            if (scanning) stopLeDeviceScan()

            val bleAddress = view.findViewById<TextView>(R.id.riley_link_ble_config_scan_item_device_address)?.text.toString()
            val deviceName = view.findViewById<TextView>(R.id.riley_link_ble_config_scan_item_device_name)?.text.toString()
            preferences.put(RileyLinkStringPreferenceKey.MacAddress, bleAddress)
            preferences.put(RileyLinkStringKey.Name, deviceName)
            val rileyLinkPump = activePlugin.activePump as RileyLinkPumpDevice
            rileyLinkPump.rileyLinkService?.verifyConfiguration(true) // force reloading of address to assure that the RL gets reconnected (even if the address didn't change)
            rileyLinkPump.triggerPumpConfigurationChangedEvent()
            finish()
        }
        binding.rileyLinkBleConfigScanStart.setOnClickListener {
            // disable currently selected RL, so that we can discover it
            rileyLinkUtil.sendBroadcastMessage(RileyLinkConst.Intents.RileyLinkDisconnect)
            startLeDeviceScan()
        }
        binding.rileyLinkBleConfigButtonScanStop.setOnClickListener {
            if (scanning) {
                stopLeDeviceScan()
                rileyLinkUtil.sendBroadcastMessage(RileyLinkConst.Intents.RileyLinkNewAddressSet) // Reconnect current RL
            }
        }
        binding.rileyLinkBleConfigButtonRemoveRileyLink.setOnClickListener {
            OKDialog.showConfirmation(
                this@RileyLinkBLEConfigActivity,
                rh.gs(R.string.riley_link_ble_config_remove_riley_link_confirmation_title),
                rh.gs(R.string.riley_link_ble_config_remove_riley_link_confirmation),
                {
                    rileyLinkUtil.sendBroadcastMessage(RileyLinkConst.Intents.RileyLinkDisconnect)
                    preferences.remove(RileyLinkStringPreferenceKey.MacAddress)
                    preferences.remove(RileyLinkStringKey.Name)
                    updateCurrentlySelectedRileyLink()
                })
        }
    }

    private fun updateCurrentlySelectedRileyLink() {
        val address = preferences.get(RileyLinkStringPreferenceKey.MacAddress)
        if (StringUtils.isEmpty(address)) {
            binding.rileyLinkBleConfigCurrentlySelectedRileyLinkName.setText(R.string.riley_link_ble_config_no_riley_link_selected)
            binding.rileyLinkBleConfigCurrentlySelectedRileyLinkAddress.visibility = View.GONE
            binding.rileyLinkBleConfigButtonRemoveRileyLink.visibility = View.GONE
        } else {
            binding.rileyLinkBleConfigCurrentlySelectedRileyLinkAddress.visibility = View.VISIBLE
            binding.rileyLinkBleConfigButtonRemoveRileyLink.visibility = View.VISIBLE
            binding.rileyLinkBleConfigCurrentlySelectedRileyLinkName.text = preferences.get(RileyLinkStringKey.Name)
            binding.rileyLinkBleConfigCurrentlySelectedRileyLinkAddress.text = address
        }
    }

    override fun onResume() {
        super.onResume()
        prepareForScanning()
        updateCurrentlySelectedRileyLink()
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacksAndMessages(null)
    }

    override fun onDestroy() {
        super.onDestroy()
        binding.rileyLinkBleConfigScanDeviceList.adapter = null
        binding.rileyLinkBleConfigScanDeviceList.onItemClickListener = null
        binding.rileyLinkBleConfigScanStart.setOnClickListener(null)
        binding.rileyLinkBleConfigButtonScanStop.setOnClickListener(null)
        binding.rileyLinkBleConfigButtonRemoveRileyLink.setOnClickListener(null)
        if (scanning) {
            stopLeDeviceScan()
            rileyLinkUtil.sendBroadcastMessage(RileyLinkConst.Intents.RileyLinkNewAddressSet) // Reconnect current RL
        }
        handler.removeCallbacksAndMessages(null)
        handler.looper.quitSafely()
    }

    private fun prepareForScanning() {
        val checkOK = blePreCheck.prerequisitesCheck(this)
        if (checkOK) {
            bleScanner = bluetoothAdapter?.bluetoothLeScanner
            settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
            filters = listOf(
                ScanFilter.Builder().setServiceUuid(
                    ParcelUuid.fromString(GattAttributes.SERVICE_RADIO)
                ).build()
            )
        }
    }

    private val bleScanCallback: ScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, scanRecord: ScanResult) {
            aapsLogger.debug(LTag.PUMPBTCOMM, scanRecord.toString())
            runOnUiThread { if (addDevice(scanRecord)) deviceListAdapter.notifyDataSetChanged() }
        }

        override fun onBatchScanResults(results: List<ScanResult>) {
            runOnUiThread {
                var added = false
                for (result in results) {
                    if (addDevice(result)) added = true
                }
                if (added) deviceListAdapter.notifyDataSetChanged()
            }
        }

        private fun addDevice(result: ScanResult): Boolean {
            val device = result.device
            val serviceUuids = result.scanRecord?.serviceUuids
            if (serviceUuids == null || serviceUuids.isEmpty()) {
                aapsLogger.debug(LTag.PUMPBTCOMM, "Device " + device.address + " has no serviceUuids (Not RileyLink).")
            } else if (serviceUuids.size > 1) {
                aapsLogger.debug(LTag.PUMPBTCOMM, "Device " + device.address + " has too many serviceUuids (Not RileyLink).")
            } else {
                val uuid = serviceUuids[0].uuid.toString().lowercase(Locale.getDefault())
                if (uuid == GattAttributes.SERVICE_RADIO) {
                    aapsLogger.debug(LTag.PUMPBTCOMM, "Found RileyLink with address: " + device.address)
                    deviceListAdapter.addDevice(result)
                    return true
                } else {
                    aapsLogger.debug(LTag.PUMPBTCOMM, "Device " + device.address + " has incorrect uuid (Not RileyLink).")
                }
            }
            return false
        }

        override fun onScanFailed(errorCode: Int) {
            aapsLogger.error(LTag.PUMPBTCOMM, "Scan Failed", "Error Code: $errorCode")
            Toast.makeText(
                this@RileyLinkBLEConfigActivity, rh.gs(R.string.riley_link_ble_config_scan_error, errorCode),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun startLeDeviceScan() {
        if (bleScanner == null) {
            aapsLogger.error(LTag.PUMPBTCOMM, "startLeDeviceScan failed: bleScanner is null")
            return
        }
        deviceListAdapter.clear()
        deviceListAdapter.notifyDataSetChanged()
        handler.postDelayed(stopScanAfterTimeoutRunnable, SCAN_PERIOD_MILLIS)
        runOnUiThread {
            binding.rileyLinkBleConfigScanStart.isEnabled = false
            binding.rileyLinkBleConfigButtonScanStop.visibility = View.VISIBLE
        }
        scanning = true
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
            if (bluetoothAdapter?.isEnabled == true && bluetoothAdapter?.state == BluetoothAdapter.STATE_ON) {
                bleScanner?.startScan(filters, settings, bleScanCallback)
                aapsLogger.debug(LTag.PUMPBTCOMM, "startLeDeviceScan: Scanning Start")
                Toast.makeText(this@RileyLinkBLEConfigActivity, R.string.riley_link_ble_config_scan_scanning, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun stopLeDeviceScan() {
        if (scanning) {
            scanning = false
            if (bluetoothAdapter?.isEnabled == true && bluetoothAdapter?.state == BluetoothAdapter.STATE_ON)
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
                    bleScanner?.stopScan(bleScanCallback)
                }
            aapsLogger.debug(LTag.PUMPBTCOMM, "stopLeDeviceScan: Scanning Stop")
            Toast.makeText(this, R.string.riley_link_ble_config_scan_finished, Toast.LENGTH_SHORT).show()
            handler.removeCallbacks(stopScanAfterTimeoutRunnable)
        }
        runOnUiThread {
            binding.rileyLinkBleConfigScanStart.isEnabled = true
            binding.rileyLinkBleConfigButtonScanStop.visibility = View.GONE
        }
    }

    private inner class LeDeviceListAdapter : BaseAdapter() {

        private val leDevices: ArrayList<BluetoothDevice> = ArrayList()
        private val rileyLinkDevices: MutableMap<BluetoothDevice, Int> = HashMap()

        fun addDevice(result: ScanResult) {
            if (!leDevices.contains(result.device)) {
                leDevices.add(result.device)
            }
            rileyLinkDevices[result.device] = result.rssi
            notifyDataSetChanged()
        }

        fun clear() {
            leDevices.clear()
            rileyLinkDevices.clear()
            notifyDataSetChanged()
        }

        override fun getCount(): Int = leDevices.size
        override fun getItem(i: Int): Any = leDevices[i]
        override fun getItemId(i: Int): Long = i.toLong()

        @SuppressLint("InflateParams", "MissingPermission")
        override fun getView(i: Int, v: View?, viewGroup: ViewGroup): View {
            var view = v
            val viewHolder: ViewHolder
            // General ListView optimization code.
            if (view == null) {
                view = View.inflate(applicationContext, R.layout.riley_link_ble_config_scan_item, null)
                viewHolder = ViewHolder(view)
                view.tag = viewHolder
            } else viewHolder = view.tag as ViewHolder

            val device = leDevices[i]
            var deviceName = device.name
            if (StringUtils.isBlank(deviceName)) deviceName = "RileyLink (?)"
            deviceName += " [" + rileyLinkDevices[device] + "]"
            val currentlySelectedAddress = preferences.get(RileyLinkStringPreferenceKey.MacAddress)
            if (currentlySelectedAddress == device.address) {
                deviceName += " (" + resources.getString(R.string.riley_link_ble_config_scan_selected) + ")"
            }
            viewHolder.deviceName.text = deviceName
            viewHolder.deviceAddress.text = device.address
            return view as View
        }
    }

    internal class ViewHolder(view: View) {

        val deviceName: TextView = view.findViewById(R.id.riley_link_ble_config_scan_item_device_name)
        val deviceAddress: TextView = view.findViewById(R.id.riley_link_ble_config_scan_item_device_address)
    }

    companion object {

        private const val SCAN_PERIOD_MILLIS: Long = 15000
    }

}
