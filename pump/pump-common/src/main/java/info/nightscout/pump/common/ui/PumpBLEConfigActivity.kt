package info.nightscout.pump.common.ui

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
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.AdapterView.OnItemClickListener
import android.widget.BaseAdapter
import android.widget.TextView
import androidx.core.view.MenuProvider
import info.nightscout.core.ui.activities.TranslatedDaggerAppCompatActivity
import info.nightscout.core.ui.dialogs.OKDialog
import info.nightscout.interfaces.plugin.ActivePlugin
import info.nightscout.interfaces.pump.BlePreCheck
import info.nightscout.pump.common.R
import info.nightscout.pump.common.databinding.PumpBleConfigActivityBinding
import info.nightscout.pump.common.driver.PumpDriverConfigurationCapable
import info.nightscout.pump.common.driver.ble.PumpBLESelector
import info.nightscout.pump.common.driver.ble.PumpBLESelectorText
import info.nightscout.rx.bus.RxBus
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.logging.LTag
import info.nightscout.shared.sharedPreferences.SP
import org.apache.commons.lang3.StringUtils
import javax.inject.Inject

@SuppressLint("MissingPermission")
class PumpBLEConfigActivity : TranslatedDaggerAppCompatActivity() {

    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var sp: SP
    @Inject lateinit var blePreCheck: BlePreCheck
    @Inject lateinit var context: Context
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var rxBus: RxBus

    private lateinit var binding: PumpBleConfigActivityBinding
    private lateinit var bleSelector: PumpBLESelector

    private var settings: ScanSettings? = null
    private var filters: List<ScanFilter>? = null
    private var bleScanner: BluetoothLeScanner? = null
    private var deviceListAdapter = LeDeviceListAdapter()
    private val handler = Handler(HandlerThread(this::class.simpleName + "Handler").also { it.start() }.looper)
    private val bluetoothAdapter: BluetoothAdapter? get() = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?)?.adapter
    var scanning = false
    private val devicesMap: MutableMap<String, BluetoothDevice> = HashMap()

    private val stopScanAfterTimeoutRunnable = Runnable {
        if (scanning) {
            stopLeDeviceScan(false)
        }
    }

    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = PumpBleConfigActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (!blePreCheck.prerequisitesCheck(this)) {
            aapsLogger.error(TAG, "prerequisitesCheck failed.")
            finish()
            return
        }

        // Configuration
        val activePump = activePlugin.activePump

        if (activePump is PumpDriverConfigurationCapable) {
            bleSelector = activePump.getPumpDriverConfiguration().getPumpBLESelector()
        } else {
            throw RuntimeException("PumpBLEConfigActivity can be used only with PumpDriverConfigurationCapable pump driver.")
        }

        binding.pumpBleConfigCurrentlySelectedText.text = bleSelector.getText(PumpBLESelectorText.SELECTED_PUMP_TITLE)
        binding.pumpBleConfigScanTitle.text = bleSelector.getText(PumpBLESelectorText.SCAN_TITLE)

        title = bleSelector.getText(PumpBLESelectorText.PUMP_CONFIGURATION)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        binding.pumpBleConfigScanDeviceList.adapter = deviceListAdapter
        binding.pumpBleConfigScanDeviceList.onItemClickListener = OnItemClickListener { _: AdapterView<*>?, view: View, _: Int, _: Long ->
            // stop scanning if still active
            if (scanning) {
                stopLeDeviceScan(true)
            }
            val bleAddress = (view.findViewById<View>(R.id.pump_ble_config_scan_item_device_address) as TextView).text.toString()
            val deviceName = (view.findViewById<View>(R.id.pump_ble_config_scan_item_device_name) as TextView).text.toString()

            if (devicesMap.containsKey(bleAddress)) {
                aapsLogger.debug(TAG, "Device FOUND in deviceMap: $bleAddress")
                val bluetoothDevice = devicesMap[bleAddress]
                bleSelector.onDeviceSelected(bluetoothDevice!!, bleAddress, deviceName)
            } else {
                aapsLogger.debug(TAG, "Device NOT found in deviceMap: $bleAddress")
            }

            finish()
        }
        binding.pumpBleConfigScanStart.setOnClickListener { startLeDeviceScan() }
        binding.pumpBleConfigButtonScanStop.setOnClickListener {
            if (scanning) {
                stopLeDeviceScan(true)
            }
        }

        binding.pumpBleConfigButtonRemove.setOnClickListener {
            OKDialog.showConfirmation(
                this@PumpBLEConfigActivity,
                bleSelector.getText(PumpBLESelectorText.REMOVE_TITLE),
                bleSelector.getText(PumpBLESelectorText.REMOVE_TEXT),
                Runnable {
                    val deviceAddress: String = binding.pumpBleConfigCurrentlySelectedPumpAddress.text.toString()
                    aapsLogger.debug(TAG, "Removing device as selected: $deviceAddress")
                    if (devicesMap.containsKey(deviceAddress)) {
                        val bluetoothDevice = devicesMap[deviceAddress]
                        aapsLogger.debug(TAG, "Device can be detected near, so trying to remove bond if possible.")
                        bleSelector.removeDevice(bluetoothDevice!!)
                    } else {
                        val remoteDevice = bluetoothAdapter?.getRemoteDevice(deviceAddress)
                        if (remoteDevice != null) {
                            bleSelector.removeDevice(remoteDevice)
                        }
                    }
                    bleSelector.cleanupAfterDeviceRemoved()
                    updateCurrentlySelectedBTDevice()
                })
        }

        // Add menu items without overriding methods in the Activity
        addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {}

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean =
                when (menuItem.itemId) {
                    android.R.id.home -> {
                        onBackPressedDispatcher.onBackPressed()
                        true
                    }

                    else              -> false
                }
        })
    }

    private fun updateCurrentlySelectedBTDevice() {
        val address = bleSelector.currentlySelectedPumpAddress()
        if (StringUtils.isEmpty(address)) {
            binding.pumpBleConfigCurrentlySelectedPumpName.text = bleSelector.getText(PumpBLESelectorText.NO_SELECTED_PUMP)
            binding.pumpBleConfigCurrentlySelectedPumpAddress.visibility = View.GONE
            binding.pumpBleConfigButtonRemove.visibility = View.GONE
        } else {
            binding.pumpBleConfigCurrentlySelectedPumpAddress.visibility = View.VISIBLE
            binding.pumpBleConfigButtonRemove.visibility = View.VISIBLE
            binding.pumpBleConfigCurrentlySelectedPumpName.text = bleSelector.currentlySelectedPumpName()
            binding.pumpBleConfigCurrentlySelectedPumpAddress.text = address
        }
    }

    override fun onResume() {
        super.onResume()
        bleSelector.onResume()
        prepareForScanning()
        updateCurrentlySelectedBTDevice()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (scanning) {
            stopLeDeviceScan(false)
        }
        bleSelector.onDestroy()
    }

    private fun prepareForScanning() {
        bleScanner = bluetoothAdapter?.bluetoothLeScanner
        settings = bleSelector.getScanSettings()
        filters = bleSelector.getScanFilters()
    }

    private val bleScanCallback: ScanCallback = object : ScanCallback() {

        override fun onScanResult(callbackType: Int, scanRecord: ScanResult) {
            aapsLogger.debug(TAG, scanRecord.toString())
            runOnUiThread { if (addDevice(scanRecord)) deviceListAdapter.notifyDataSetChanged() }
        }

        override fun onBatchScanResults(results: List<ScanResult>) {
            runOnUiThread {
                var added = false
                for (result in results) {
                    aapsLogger.debug(TAG, "SCAN: " + result.advertisingSid + " name=" + result.device.address)
                    if (addDevice(result)) added = true
                }
                if (added)
                    deviceListAdapter.notifyDataSetChanged()
            }
        }

        private fun addDevice(result: ScanResult): Boolean {
            var device = result.device

            device = bleSelector.filterDevice(device)

            if (device == null) {
                return false
            }

            deviceListAdapter.addDevice(result)
            if (!devicesMap.containsKey(device.address)) {
                devicesMap[device.address] = device
            }
            return true
        }

        override fun onScanFailed(errorCode: Int) {
            aapsLogger.error(TAG, "Scan Failed - Error Code: $errorCode")
            bleSelector.onScanFailed(this@PumpBLEConfigActivity, errorCode)
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
            binding.pumpBleConfigScanStart.isEnabled = false
            binding.pumpBleConfigButtonScanStop.visibility = View.VISIBLE
        }
        scanning = true
        bleScanner?.startScan(filters, settings, bleScanCallback)
        aapsLogger.debug(LTag.PUMPBTCOMM, "startLeDeviceScan: Scanning Start")
        bleSelector.onStartLeDeviceScan(this@PumpBLEConfigActivity)
    }

    private fun stopLeDeviceScan(manualStop: Boolean) {
        if (scanning) {
            scanning = false
            bleScanner?.stopScan(bleScanCallback)
            aapsLogger.debug(LTag.PUMPBTCOMM, "stopLeDeviceScan: Scanning Stop")
            bleSelector.onStopLeDeviceScan(this@PumpBLEConfigActivity)
            handler.removeCallbacks(stopScanAfterTimeoutRunnable)
        }
        if (manualStop) {
            bleSelector.onManualStopLeDeviceScan(this@PumpBLEConfigActivity)
        } else {
            bleSelector.onNonManualStopLeDeviceScan(this@PumpBLEConfigActivity)
        }

        runOnUiThread {
            binding.pumpBleConfigScanStart.isEnabled = true
            binding.pumpBleConfigButtonScanStop.visibility = View.GONE
        }
    }

    private inner class LeDeviceListAdapter : BaseAdapter() {

        private var devicesList: ArrayList<BluetoothDevice> = arrayListOf()
        private var devicesMap: MutableMap<BluetoothDevice, Int> = mutableMapOf()

        fun addDevice(result: ScanResult) {
            if (!devicesList.contains(result.device)) {
                devicesList.add(result.device)
            }
            devicesMap[result.device] = result.rssi
            notifyDataSetChanged()
        }

        fun clear() {
            devicesList.clear()
            devicesMap.clear()
            notifyDataSetChanged()
        }

        override fun getCount(): Int {
            val c = devicesList.size
            aapsLogger.info(TAG, "D: count=$c")
            return c
        }

        override fun getItem(i: Int): Any = devicesList[i]
        override fun getItemId(i: Int): Long = i.toLong()

        override fun getView(i: Int, convertView: View?, viewGroup: ViewGroup?): View {
            var v = convertView
            val holder: ViewHolder
            if (v == null) {
                v = View.inflate(applicationContext, R.layout.pump_ble_config_scan_item, null)
                holder = ViewHolder()
                holder.deviceAddress = v.findViewById(R.id.pump_ble_config_scan_item_device_address)
                holder.deviceName = v.findViewById(R.id.pump_ble_config_scan_item_device_name)
                v.tag = holder
            } else {
                // reuse view if already exists
                holder = v.tag as ViewHolder
            }

            val device = devicesList[i]
            var deviceName = device.name
            if (StringUtils.isBlank(deviceName)) {
                deviceName = bleSelector.getUnknownPumpName()
            }
            deviceName += " [" + devicesMap[device] + "]"
            val currentlySelectedAddress = bleSelector.currentlySelectedPumpAddress() // TODO
            if (currentlySelectedAddress == device.address) {
                deviceName += " (" + resources.getString(R.string.ble_config_scan_selected) + ")"
            }
            holder.deviceName?.text = deviceName
            holder.deviceAddress?.text = device.address
            return v!!
        }
    }

    internal class ViewHolder {
        var deviceName: TextView? = null
        var deviceAddress: TextView? = null
    }

    companion object {
        private val TAG = LTag.PUMPBTCOMM
        private const val SCAN_PERIOD_MILLIS: Long = 15000
    }
}