package com.microtechmd.equil.ui.pair

import android.Manifest
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
import android.os.*
import android.text.TextUtils
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.navigation.fragment.findNavController
import com.microtechmd.equil.EquilConst
import com.microtechmd.equil.R
import com.microtechmd.equil.ble.GattAttributes
import com.microtechmd.equil.data.database.EquilHistoryRecord
import com.microtechmd.equil.manager.command.CmdPair
import info.nightscout.androidaps.extensions.runOnUiThread
import info.nightscout.androidaps.queue.Callback
import info.nightscout.shared.logging.LTag

// IMPORTANT: This activity needs to be called from RileyLinkSelectPreference (see pref_medtronic.xml as example)
class EquilPairSerialNumberFragment : EquilPairFragmentBase() {

    private val bluetoothAdapter: BluetoothAdapter? get() = (context?.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?)?.adapter
    private var settings: ScanSettings? = null
    private var filters: List<ScanFilter>? = null
    private var bleScanner: BluetoothLeScanner? = null
    private var scanning = false
    private val handler = Handler(HandlerThread(this::class.simpleName + "Handler").also { it.start() }.looper)
    private val SCAN_PERIOD_MILLIS: Long = 15000
    private var serialNumber = ""

    private val stopScanAfterTimeoutRunnable = Runnable {
        if (scanning) {
            // equilPumpPlugin.showToast(rh.gs(R.string.equil_error))
            dismissLoading()
            stopLeDeviceScan()

            runOnUiThread {
                progressPair.visibility = View.INVISIBLE
                textTips.visibility = View.VISIBLE
                buttonPair.text = rh.gs(R.string.equil_retry)
                buttonPair.isClickable = true
                buttonPair.alpha = 1f

            }
        }
    }

    override fun getLayoutId(): Int {
        return R.layout.equil_pair_serial_number_fragment
    }

    override fun getNextPageActionId(): Int? {
        return R.id.action_startEquilActivationFragment_to_startEquilPairFillFragment;
    }

    override fun getIndex(): Int {
        return 2;
    }

    lateinit var buttonNext: Button
    lateinit var textTips: TextView
    lateinit var buttonPair: Button
    lateinit var progressPair: ProgressBar

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        buttonNext = view.findViewById<Button>(R.id.button_next);
        buttonPair = view.findViewById<Button>(R.id.button_pair);
        textTips = view.findViewById<TextView>(R.id.text_tips);
        progressPair = view.findViewById<ProgressBar>(R.id.progress_pair);
        buttonNext.setOnClickListener {
            context?.let {
                // serialNumber = view.findViewById<TextView>(R.id.devicesName).text.toString().trim();
                // aapsLogger.error(LTag.PUMPBTCOMM, "serialNumber ====" + serialNumber)
                // sp.putString(EquilConst.Prefs.addrss, "")
                // sp.putString(EquilConst.Prefs.name, "")
                // if (!TextUtils.isEmpty(serialNumber)) {
                //     startLeDeviceScan()
                // }
                val nextPage = getNextPageActionId()
                if (nextPage != null) {
                    findNavController().navigate(nextPage)
                }

            }
        }
        buttonNext.alpha = 0.3f
        buttonNext.isClickable = false
        buttonPair.setOnClickListener {
            context?.let {
                buttonPair.isClickable = false
                buttonPair.alpha = 0.3f
                textTips.visibility = View.INVISIBLE
                buttonPair.text = rh.gs(R.string.equil_pair)
                serialNumber = view.findViewById<TextView>(R.id.devicesName).text.toString().trim();
                aapsLogger.error(LTag.PUMPBTCOMM, "serialNumber ====" + serialNumber)
                equilPumpPlugin.equilManager.address = ""
                equilPumpPlugin.equilManager.serialNumber = ""
                if (!TextUtils.isEmpty(serialNumber)) {
                    startLeDeviceScan()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        prepareForScanning()
    }

    private fun prepareForScanning() {
        val checkOK = activity?.let { blePreCheck.prerequisitesCheck(it as AppCompatActivity) }
        if (checkOK == true) {
            bleScanner = bluetoothAdapter?.bluetoothLeScanner
            settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
            filters = listOf(
                ScanFilter.Builder().setServiceUuid(
                    ParcelUuid.fromString(GattAttributes.SERVICE_RADIO)
                ).build()
            )
        }
    }

    private fun startLeDeviceScan() {
        if (bleScanner == null) {
            aapsLogger.error(LTag.PUMPBTCOMM, "startLeDeviceScan failed: bleScanner is null")
            return
        }
        scanning = true
        progressPair.visibility = View.VISIBLE
        handler.postDelayed(stopScanAfterTimeoutRunnable, SCAN_PERIOD_MILLIS)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || activity?.let { ActivityCompat.checkSelfPermission(it, Manifest.permission.BLUETOOTH_SCAN) } == PackageManager.PERMISSION_GRANTED) {
            bleScanner?.startScan(filters, settings, bleScanCallback)
            aapsLogger.error(LTag.PUMPBTCOMM, "startLeDeviceScan: Scanning Start")
        }
    }

    private val bleScanCallback: ScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, scanRecord: ScanResult) {
            aapsLogger.debug(LTag.PUMPBTCOMM, scanRecord.toString())
            var name = scanRecord.device.name;
            if (!TextUtils.isEmpty(name) && name.contains(serialNumber)) {
                handler.removeCallbacks(stopScanAfterTimeoutRunnable)
                stopLeDeviceScan()
                pair(scanRecord.device)
            }
        }

        override fun onBatchScanResults(results: List<ScanResult>) {
        }
    }

    private fun stopLeDeviceScan() {
        if (scanning) {
            scanning = false
            if (bluetoothAdapter?.isEnabled == true && bluetoothAdapter?.state == BluetoothAdapter.STATE_ON)
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || context?.let { ActivityCompat.checkSelfPermission(it, Manifest.permission.BLUETOOTH_SCAN) } == PackageManager.PERMISSION_GRANTED) {
                    bleScanner?.stopScan(bleScanCallback)
                }
            aapsLogger.debug(LTag.PUMPBTCOMM, "stopLeDeviceScan: Scanning Stop")
        }
    }

    private fun pair(scanResult: BluetoothDevice) {
        aapsLogger.error(LTag.EQUILBLE, "result====" + scanResult.name.toString()+"==="+scanResult.address.toString())
        // sp.putString(EquilConst.Prefs.addrss, scanResult.address.toString())
        // equilPumpPlugin.equilManager.address = scanResult.address.toString()

        commandQueue.customCommand(CmdPair(scanResult.name.toString(), scanResult.address.toString()), object : Callback() {
            override fun run() {
                aapsLogger.error(LTag.EQUILBLE, "result====" + result.success)
                if (result.success) {
                    equilPumpPlugin.equilManager.closeBle();
                    SystemClock.sleep(100)
                    dismissLoading();
                    equilPumpPlugin.equilManager.address = scanResult.address.toString()
                    equilPumpPlugin.equilManager.serialNumber = scanResult.name.toString()
                    equilPumpPlugin.showToast(rh.gs(R.string.equil_success))

                    var time = System.currentTimeMillis();

                    val equilHistoryRecord = EquilHistoryRecord(
                        time,
                        null,
                        null,
                        EquilHistoryRecord.EventType.INITIALIZE_EQUIL,
                        time,
                        equilPumpPlugin.serialNumber()
                    )
                    equilPumpPlugin.equilHistoryRecordDao.insert(equilHistoryRecord)
                    runOnUiThread {
                        val nextPage = getNextPageActionId()
                        if (nextPage != null) {
                            findNavController().navigate(nextPage)
                        }
                    }
                } else {
                    dismissLoading();
                    runOnUiThread {
                        progressPair.visibility = View.INVISIBLE
                        buttonPair.isClickable = true
                        textTips.visibility = View.VISIBLE
                        buttonPair.text = rh.gs(R.string.equil_retry)
                        buttonPair.alpha = 1f
                    }

                    equilPumpPlugin.equilManager.address = ""
                    equilPumpPlugin.equilManager.serialNumber = ""
                }
            }
        })
    }

}
