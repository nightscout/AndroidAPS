package app.aaps.pump.equil.ui.pair

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
import android.os.SystemClock
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.navigation.fragment.findNavController
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.queue.Callback
import app.aaps.core.ui.extensions.runOnUiThread
import app.aaps.core.ui.toast.ToastUtils
import app.aaps.core.utils.extensions.safeEnable
import app.aaps.pump.equil.EquilConst
import app.aaps.pump.equil.R
import app.aaps.pump.equil.ble.GattAttributes
import app.aaps.pump.equil.driver.definition.ActivationProgress
import app.aaps.pump.equil.driver.definition.BluetoothConnectionState
import app.aaps.pump.equil.keys.EquilStringKey
import app.aaps.pump.equil.manager.Utils
import app.aaps.pump.equil.manager.command.CmdDevicesOldGet
import app.aaps.pump.equil.manager.command.CmdPair
import app.aaps.pump.equil.manager.command.CmdSettingSet
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.util.regex.Pattern

class EquilPairSerialNumberFragment : EquilPairFragmentBase() {

    private val bluetoothAdapter: BluetoothAdapter? get() = (context?.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?)?.adapter
    private var settings: ScanSettings? = null
    private var filters: List<ScanFilter>? = null
    private var bleScanner: BluetoothLeScanner? = null
    private var scanning = false
    private val handler = Handler(HandlerThread(this::class.simpleName + "Handler").also { it.start() }.looper)

    @Suppress("PrivatePropertyName")
    private val SCAN_PERIOD_MILLIS: Long = 15000
    private var serialNumber = ""

    private val stopScanAfterTimeoutRunnable = Runnable {
        if (scanning) {
            // equilPumpPlugin.showToast(rh.gs(R.string.equil_error))
            dismissLoading()
            stopLeDeviceScan()
            runOnUiThread {
                progressPair?.visibility = View.INVISIBLE
                textTips?.visibility = View.VISIBLE
                buttonPair?.text = rh.gs(R.string.equil_retry)
                buttonPair?.isClickable = true
                buttonPair?.alpha = 1f

            }
        }
    }

    override fun getLayoutId(): Int = R.layout.equil_pair_serial_number_fragment
    override fun getNextPageActionId(): Int = R.id.action_startEquilActivationFragment_to_startEquilPairFillFragment
    override fun getIndex(): Int = 2

    private var textTips: TextView? = null
    private var buttonPair: Button? = null
    private var progressPair: ProgressBar? = null
    private var equilPasswordText: TextInputEditText? = null
    private var equilTextInputLayout: TextInputLayout? = null
    private var devicesNameText: TextInputEditText? = null
    private var devicesNameInputLayout: TextInputLayout? = null
    private var password: String = ""
    private var passwordTextWatcher: TextWatcher? = null
    private var deviceNameTextWatcher: TextWatcher? = null
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val buttonNext = view.findViewById<Button>(R.id.button_next)
        buttonPair = view.findViewById(R.id.button_pair)
        textTips = view.findViewById(R.id.text_tips)
        progressPair = view.findViewById(R.id.progress_pair)
        equilPasswordText = view.findViewById(R.id.devicesPwd)
        equilTextInputLayout = view.findViewById(R.id.devicesPwdLayout)
        devicesNameText = view.findViewById(R.id.devicesName)
        devicesNameInputLayout = view.findViewById(R.id.devicesNameLayout)
        buttonNext.setOnClickListener { findNavController().navigate(getNextPageActionId()) }

        equilPasswordText?.setText(preferences.get(EquilStringKey.PairPassword))
        passwordTextWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun afterTextChanged(s: Editable?) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                s?.let { validatePassword(s.toString()) }
            }
        }
        equilPasswordText?.addTextChangedListener(passwordTextWatcher)
        deviceNameTextWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun afterTextChanged(s: Editable?) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                s?.let { validateDeviceName(s.toString()) }
            }
        }
        devicesNameText?.addTextChangedListener(deviceNameTextWatcher)
        buttonPair?.setOnClickListener {
            equilManager.setAddress("")
            equilManager.setSerialNumber("")
            password = equilPasswordText?.text?.toString()?.trim() ?: ""
            serialNumber = devicesNameText?.text?.toString()?.trim() ?: ""
            if (validateDeviceName(serialNumber) && validatePassword(password)) {
                val imm = activity?.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(equilPasswordText?.windowToken, 0)
                preferences.put(EquilStringKey.PairPassword, password)
                buttonPair?.isClickable = false
                buttonPair?.alpha = 0.3f
                textTips?.visibility = View.INVISIBLE
                buttonPair?.text = rh.gs(R.string.equil_pair)
                startLeDeviceScan()
            }
        }
        buttonNext.alpha = 0.3f
        buttonNext.isClickable = false
    }

    private fun validateDeviceName(email: String): Boolean {
        val emailPattern = rh.gs(R.string.sixhexanumber)
        val pattern = Pattern.compile(emailPattern)
        val matcher = pattern.matcher(email)
        if (matcher.matches()) {
            devicesNameText?.error = null
            devicesNameInputLayout?.isErrorEnabled = false
            return true
        } else {
            devicesNameText?.error = rh.gs(R.string.error_mustbe6hexadidits)
            devicesNameInputLayout?.isErrorEnabled = true
            return false
        }
    }

    private fun validatePassword(email: String): Boolean {
        val emailPattern = rh.gs(app.aaps.core.validators.R.string.fourhexanumber)
        val pattern = Pattern.compile(emailPattern)
        val matcher = pattern.matcher(email)
        if (matcher.matches()) {
            equilPasswordText?.error = null
            equilTextInputLayout?.isErrorEnabled = false
            return true
        } else {
            equilPasswordText?.error = rh.gs(app.aaps.core.validators.R.string.error_mustbe4hexadidits)
            equilTextInputLayout?.isErrorEnabled = true
            return false
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
        } else {
            ToastUtils.errorToast(activity, getString(app.aaps.core.ui.R.string.need_connect_permission))
            activity?.finish()
        }
    }

    @SuppressLint("MissingPermission") // activity should be finished without permissions
    private fun startLeDeviceScan() {
        if (bleScanner == null) {
            aapsLogger.error(LTag.PUMPBTCOMM, "startLeDeviceScan failed: bleScanner is null")
            return
        }
        scanning = true
        progressPair?.visibility = View.VISIBLE
        handler.postDelayed(stopScanAfterTimeoutRunnable, SCAN_PERIOD_MILLIS)
        if (bluetoothAdapter?.isEnabled != true) bluetoothAdapter?.safeEnable(waitMilliseconds = 3000)
        bleScanner?.startScan(filters, settings, bleScanCallback)
        aapsLogger.debug(LTag.PUMPCOMM, "startLeDeviceScan: Scanning Start")
    }

    @SuppressLint("MissingPermission") // activity should be finished without permissions
    private val bleScanCallback: ScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, scanRecord: ScanResult) {
            aapsLogger.debug(LTag.PUMPBTCOMM, scanRecord.toString())
            var name = scanRecord.device.name
            if (!TextUtils.isEmpty(name) && name.contains(serialNumber)) {
                scanRecord.scanRecord?.bytes.let {
                    if (it != null) {
                        val historyIndex = Utils.bytesToInt(it[24], it[23])
                        equilManager.setStartHistoryIndex(historyIndex)
                        aapsLogger.debug(LTag.PUMPCOMM, "historyIndex  $historyIndex")
                    }
                }
                handler.removeCallbacks(stopScanAfterTimeoutRunnable)
                stopLeDeviceScan()
                getVersion(scanRecord.device)
            }
        }

        override fun onBatchScanResults(results: List<ScanResult>) {}
    }

    private fun stopLeDeviceScan() {
        if (scanning) {
            scanning = false
            if (bluetoothAdapter?.isEnabled == true && bluetoothAdapter?.state == BluetoothAdapter.STATE_ON)
                if (context?.let { ActivityCompat.checkSelfPermission(it, Manifest.permission.BLUETOOTH_SCAN) } == PackageManager.PERMISSION_GRANTED) {
                    bleScanner?.stopScan(bleScanCallback)
                }
            aapsLogger.debug(LTag.PUMPBTCOMM, "stopLeDeviceScan: Scanning Stop")
        }
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacksAndMessages(null)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Remove listeners first (breaks View → Fragment reference)
        equilPasswordText?.removeTextChangedListener(passwordTextWatcher)
        devicesNameText?.removeTextChangedListener(deviceNameTextWatcher)
        buttonPair?.setOnClickListener(null)
        // Then null references (breaks Fragment → View reference)
        passwordTextWatcher = null
        deviceNameTextWatcher = null
        textTips = null
        buttonPair = null
        progressPair = null
        equilPasswordText = null
        equilTextInputLayout = null
        devicesNameText = null
        devicesNameInputLayout = null
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        handler.looper.quitSafely()
        equilPumpPlugin.tempActivationProgress = ActivationProgress.NONE
    }

    fun getVersion(scanResult: BluetoothDevice) {
        // CmdDevicesOldGet
        val cmdDevicesOldGet = CmdDevicesOldGet(scanResult.address.toString(), aapsLogger, preferences, equilManager)
        commandQueue.customCommand(cmdDevicesOldGet, object : Callback() {
            override fun run() {
                if (activity == null) return
                aapsLogger.debug(LTag.PUMPCOMM, "result====" + result.success + "===" + result.enacted)
                if (result.success) {
                    if (cmdDevicesOldGet.isSupport()) {
                        SystemClock.sleep(EquilConst.EQUIL_BLE_NEXT_CMD)
                        pair(scanResult)
                    } else {
                        dismissLoading()
                        runOnUiThread {
                            progressPair?.visibility = View.INVISIBLE
                            buttonPair?.isClickable = true
                            textTips?.text = rh.gs(R.string.equil_support_error)
                            textTips?.visibility = View.VISIBLE
                            buttonPair?.text = rh.gs(R.string.equil_retry)
                            buttonPair?.alpha = 1f
                        }
                    }
                } else {
                    dismissLoading()
                    runOnUiThread {
                        progressPair?.visibility = View.INVISIBLE
                        buttonPair?.isClickable = true
                        textTips?.visibility = View.VISIBLE
                        buttonPair?.text = rh.gs(R.string.equil_retry)
                        textTips?.text = rh.gs(R.string.equil_pair_error)
                        buttonPair?.alpha = 1f
                    }
                    equilManager.setAddress("")
                    equilManager.setSerialNumber("")
                }
            }
        })
    }

    @SuppressLint("MissingPermission")
    private fun pair(scanResult: BluetoothDevice) {
        equilManager.setActivationProgress(ActivationProgress.PRIMING)
        equilManager.setBluetoothConnectionState(BluetoothConnectionState.CONNECTED)
        aapsLogger.debug(LTag.PUMPCOMM, "result====${scanResult.name}===${scanResult.address}")
        commandQueue.customCommand(CmdPair(scanResult.name.toString(), scanResult.address.toString(), password, aapsLogger, preferences, equilManager), object : Callback() {
            override fun run() {
                if (activity == null) return
                aapsLogger.debug(LTag.PUMPCOMM, "result====" + result.success + "===" + result.enacted)
                if (result.success) {
                    if (result.enacted) {
                        SystemClock.sleep(EquilConst.EQUIL_BLE_NEXT_CMD)
                        pumpSettings(scanResult.address.toString(), scanResult.name.toString())
                    } else {
                        dismissLoading()
                        runOnUiThread {
                            progressPair?.visibility = View.INVISIBLE
                            buttonPair?.isClickable = true
                            textTips?.text = rh.gs(R.string.equil_password_error)
                            textTips?.visibility = View.VISIBLE
                            buttonPair?.text = rh.gs(R.string.equil_retry)
                            buttonPair?.alpha = 1f
                        }
                    }
                } else {
                    dismissLoading()
                    runOnUiThread {
                        progressPair?.visibility = View.INVISIBLE
                        buttonPair?.isClickable = true
                        textTips?.visibility = View.VISIBLE
                        buttonPair?.text = rh.gs(R.string.equil_retry)
                        textTips?.text = rh.gs(R.string.equil_pair_error)
                        buttonPair?.alpha = 1f
                    }

                    equilManager.setAddress("")
                    equilManager.setSerialNumber("")
                }
            }
        })
    }

    private fun pumpSettings(address: String, serialNumber: String) {
        val profile = pumpSync.expectedPumpState().profile ?: return
        commandQueue.customCommand(CmdSettingSet(constraintsChecker.getMaxBolusAllowed().value(), constraintsChecker.getMaxBasalAllowed(profile).value(), aapsLogger, preferences, equilManager), object : Callback() {
            override fun run() {
                if (activity == null) return
                if (result.success) {
                    dismissLoading()
                    equilManager.setAddress(address)
                    equilManager.setSerialNumber(serialNumber)
                    ToastUtils.okToast(requireContext(), rh.gs(R.string.equil_success))
                    runOnUiThread {
                        val nextPage = getNextPageActionId()
                        equilManager.setActivationProgress(ActivationProgress.CANNULA_CHANGE)
                        findNavController().navigate(nextPage)
                    }
                } else {
                    dismissLoading()
                    runOnUiThread {
                        progressPair?.visibility = View.INVISIBLE
                        buttonPair?.isClickable = true
                        textTips?.visibility = View.VISIBLE
                        buttonPair?.text = rh.gs(R.string.equil_retry)
                        buttonPair?.alpha = 1f
                    }
                    equilManager.setAddress("")
                    equilManager.setSerialNumber("")
                }
            }
        })
    }
}
