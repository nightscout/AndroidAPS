package app.aaps.pump.aaruy.ui

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.pump.BlePreCheck
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.ui.toast.ToastUtils
import app.aaps.core.utils.extensions.safeEnable
import app.aaps.pump.aaruy.R
import app.aaps.pump.aaruy.code.ConnectionState
import app.aaps.pump.aaruy.databinding.ActivityAaruyBlescannerBinding
import app.aaps.pump.aaruy.services.AaruyBLE
import app.aaps.pump.aaruy.services.BLECommCallback
import app.aaps.pump.aaruy.ui.dialog.AaruyAlertDialog
import app.aaps.pump.aaruy.ui.dialog.YUtils
import app.aaps.pump.aaruy.ui.event.EventAaruyDeviceChange
import app.aaps.pump.aaruy.ui.viewmodel.AaruyViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import no.nordicsemi.android.support.v18.scanner.ScanResult
import javax.inject.Inject
import android.content.res.Configuration
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.aaruy.keys.AaruyStringKey

class AaruyBLEScanActivity : AaruyBaseActivity<ActivityAaruyBlescannerBinding>(), BLECommCallback {

    @Inject lateinit var preferences: Preferences
    @Inject lateinit var blePreCheck: BlePreCheck
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var aaruyBle: AaruyBLE

    private var listAdapter: ListAdapter? = null
    private val bluetoothAdapter: BluetoothAdapter? get() = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?)?.adapter
    private var clickToConnect = false
    private var waitHistoryCount = 0
    var alertDialog: AaruyAlertDialog? = null

    override fun getLayoutId(): Int = R.layout.activity_aaruy_blescanner

    @SuppressLint("HandlerLeak")
    private inner class MyHandler : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            super.handleMessage(msg)
            if (msg.what == 1) {
                myHandler!!.sendEmptyMessageDelayed(1, 100)
            }  else if (msg.what == 5) {
                myHandler!!.postDelayed(Runnable {
                    binding.viewModel?.apply {
                        if (aaruyPump.canSaveHistory || waitHistoryCount >= 7) {
                            waitHistoryCount = 0
                            aaruyBle.receivingHistory = false
                            if (activity != null) {
                                if (YUtils.loadingIsShowing()) {
                                    YUtils.hideLoading()
                                }
                            }
                        }
                        else {
                            waitHistoryCount++
                            myHandler!!.sendEmptyMessage(5)
                        }
                    }
                }, 2000)
            } else if (msg.what == 8) {
                myHandler!!.postDelayed(Runnable {
                    lifecycleScope.launch {
                        bluetoothAdapter?.getRemoteDevice(preferences.get(AaruyStringKey.MacAddress))?.let { connectDevice(it) }
                        myHandler!!.sendEmptyMessage(12)
                    }
                }, 2000)
            } else if (msg.what == 12) {
                myHandler!!.postDelayed(Runnable {
                    if (YUtils.loadingIsShowing()) {
                        lifecycleScope.launch {
                            delay(2000)
                            if (clickToConnect)
                                YUtils.hideLoading()
                        }
                    } else {
                        aapsLogger.error(LTag.PUMP, "10 sec is up")
                    }
                }, 10000)
            }
        }
    }

    private var myHandler: MyHandler? = null

    @SuppressLint("SourceLockedOrientationActivity")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activity = this
        binding = ActivityAaruyBlescannerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        title = getString(R.string.aaruy_pairing)
        aaruyBle.setCallback(this)

        blePreCheck.prerequisitesCheck(this)
        listAdapter = ListAdapter()
        binding.bleScannerListview.emptyView = binding.bleScannerNoDevice
        binding.bleScannerListview.adapter = listAdapter
        listAdapter?.notifyDataSetChanged()

        binding.apply {
            viewModel = ViewModelProvider(this@AaruyBLEScanActivity, viewModelFactory)[AaruyViewModel::class.java]
            viewModel?.apply {
                btnUnbind.setOnClickListener {
                    if (aaruyPump.connectionState == ConnectionState.CONNECTED) {
                        unbindDialogConnectTip()
                    }
                    else {
                        unbindDialogDisconnectTip()
                    }
                }

                canDoUnbind.observe(this@AaruyBLEScanActivity) {
                    aapsLogger.debug(LTag.PUMP, "canDoUnbind = $it")
                    if (it) {
                        binding.bleScannerListview.visibility = View.GONE
                        binding.btnUnbind.visibility = View.VISIBLE
                        binding.bleScannerListview.adapter = null

                        if (YUtils.loadingIsShowing()) {
                            YUtils.hideLoading()
                        }
                        if (clickToConnect) {
                            clickToConnect = false
                            // 获取历史记录
                            lifecycleScope.launch {
                                delay(500)
                                YUtils.showLoading(activity as AaruyBLEScanActivity, getString(R.string.is_loading_data))
                                requestHistoryData()
                                waitHistoryCount = 0
                                myHandler!!.sendEmptyMessage(5)
                                // delay(15000)
                                // aapsLogger.error(LTag.PUMP, "15 sec is up")
                                // aaruyBle.receivingHistory = false
                                // if (activity != null) {
                                //     if (YUtils.loadingIsShowing()) {
                                //         YUtils.hideLoading()
                                //     }
                                // }
                            }
                        }
                    } else {
                        binding.bleScannerListview.visibility = View.VISIBLE
                        binding.btnUnbind.visibility = View.GONE
                        binding.bleScannerListview.adapter = listAdapter
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (myHandler != null) {
            myHandler!!.removeCallbacksAndMessages(null)
            myHandler = null
        }
        if (alertDialog != null) {
            alertDialog!!.dismiss()
            alertDialog = null
        }
        YUtils.hideLoading()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (aaruyBle.isConnecting) {
            ToastUtils.errorToast(context, context.getString(R.string.is_connecting))
            if (!YUtils.loadingIsShowing()) {
                YUtils.showLoading(activity as AaruyBLEScanActivity, getString(R.string.is_connecting))
            }
            return true
        }
        if (aaruyBle.receivingHistory || (clickToConnect && aaruyBle.isConnected)) {
            ToastUtils.errorToast(context, context.getString(R.string.is_loading_data))
            if (!YUtils.loadingIsShowing()) {
                YUtils.showLoading(activity as AaruyBLEScanActivity, getString(R.string.is_loading_data))
            }
            return true
        }
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            this@AaruyBLEScanActivity.finish()
        }
        return true
    }

    override fun onResume() {
        super.onResume()

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
            bluetoothAdapter?.safeEnable()
            // start scan
            startScan()
        } else {
            ToastUtils.errorToast(context, context.getString(app.aaps.core.ui.R.string.need_connect_permission))
        }
    }

    override fun onPause() {
        super.onPause()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
            // stop scan
            stopScan()
        }
    }

    override fun onBLEScan() {
        Handler(Looper.getMainLooper()).post { listAdapter?.notifyDataSetChanged() }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // 处理屏幕方向改变的逻辑
    }

    @SuppressLint("MissingPermission")
    private fun startScan() {
        aaruyBle.scanDevice()
    }

    private fun stopScan() {
        aaruyBle.stopScan()
    }

    @SuppressLint("MissingPermission")
    private fun connectDevice(device: BluetoothDevice) {
        aaruyBle.connectDevice(device)
    }

    private fun unbindDialogConnectTip() {
        val tipString = String.format(
            getString(R.string.actions_pump_disconnect_prompt),
            aaruyBle.getShortDeviceName()
        )
        alertDialog = AaruyAlertDialog(
            activity,
            getString(R.string.notifyTitle),
            tipString,
            true,
            1
        ) { _, isPositive ->
            if (isPositive) {
                alertDialog!!.dismiss()
                binding.viewModel?.onClickUnbind()
            } else {
                alertDialog!!.dismiss()
            }
        }
        alertDialog!!.show()
    }

    private fun unbindDialogDisconnectTip() {
        val tipString = String.format(
            getString(R.string.actions_pump_disconnect),
            aaruyBle.getShortDeviceName()
        )
        val tipStringAgain = String.format(
            getString(R.string.actions_pump_disconnect_again),
            aaruyBle.getShortDeviceName()
        )
        alertDialog = AaruyAlertDialog(
            activity,
            getString(R.string.notifyTitle),
            tipString,
            true,
            1
        ) { _, isPositive ->
            if (isPositive) {

                alertDialog = AaruyAlertDialog(activity,
                                          getString(R.string.notifyTitle),
                                          tipStringAgain,
                                          true,
                                          1,
                                          AaruyAlertDialog.OnDialogButtonClickListener { requestCode, isPositive ->
                                              if (isPositive) {
                                                  alertDialog!!.dismiss()
                                                  binding.viewModel?.onClickUnbind()
                                              } else {
                                                  alertDialog!!.dismiss()
                                              }
                                          })
                alertDialog?.show()

            } else {
                alertDialog!!.dismiss()
            }
        }
        alertDialog!!.show()
    }

    internal inner class ListAdapter : BaseAdapter() {

        override fun getCount(): Int = aaruyBle.scanResultList.size
        override fun getItem(i: Int): ScanResult = aaruyBle.scanResultList[i]
        override fun getItemId(i: Int): Long = 0

        override fun getView(i: Int, convertView: View?, parent: ViewGroup?): View {
            var v = convertView
            val holder: ViewHolder
            if (v == null) {
                v = View.inflate(applicationContext, R.layout.aaruy_blescanner_item, null)
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

            private lateinit var item: ScanResult
            private val name: TextView = v.findViewById(R.id.ble_name)
            private val address: TextView = v.findViewById(R.id.ble_address)

            init {
                v.setOnClickListener(this@ViewHolder)
            }

            @SuppressLint("MissingPermission")
            override fun onClick(v: View) {
                if (aaruyBle.isConnected)
                    return;

                preferences.put(AaruyStringKey.MacAddress, item.device.address)
                preferences.put(AaruyStringKey.PumpName, name.text.toString())
                connectDevice(item.device)
                binding.viewModel?.apply {
                    aaruyPump.canSaveHistory = false
                    aaruyPump.lastHistoryTime = 0
                    aaruyPump.lastHistoryDailyTime = System.currentTimeMillis()
                }
                rxBus.send(EventAaruyDeviceChange())
                clickToConnect = true
                YUtils.showLoading(activity as AaruyBLEScanActivity, getString(R.string.is_connecting))
                if (myHandler == null) {
                    myHandler = MyHandler()
                }
                myHandler!!.sendEmptyMessage(8)
                // CoroutineScope(Dispatchers.IO).launch{
                //     delay(3000)
                //     finish()
                // }
            }

            @SuppressLint("MissingPermission")
            fun setData(data: ScanResult) {
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
}