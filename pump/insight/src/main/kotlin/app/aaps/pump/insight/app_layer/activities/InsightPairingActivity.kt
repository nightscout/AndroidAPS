package app.aaps.pump.insight.app_layer.activities

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import app.aaps.core.interfaces.pump.BlePreCheck
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.ui.toast.ToastUtils
import app.aaps.core.utils.extensions.safeDisable
import app.aaps.core.utils.extensions.safeGetParcelableExtra
import app.aaps.pump.insight.R
import app.aaps.pump.insight.connection_service.InsightConnectionService
import app.aaps.pump.insight.connection_service.InsightConnectionService.ExceptionCallback
import app.aaps.pump.insight.databinding.ActivityInsightPairingBinding
import app.aaps.pump.insight.descriptors.InsightState
import app.aaps.pump.insight.utils.ExceptionTranslator
import dagger.android.support.DaggerAppCompatActivity
import javax.inject.Inject

class InsightPairingActivity : DaggerAppCompatActivity(), InsightConnectionService.StateCallback, View.OnClickListener, ExceptionCallback {

    @Inject lateinit var blePreCheck: BlePreCheck
    @Inject lateinit var context: Context
    @Inject lateinit var pumpSync: PumpSync

    private lateinit var binding: ActivityInsightPairingBinding
    private var scanning = false
    private val deviceAdapter = DeviceAdapter()
    private var isBound = false

    private var service: InsightConnectionService? = null
    private val serviceConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            service = (binder as InsightConnectionService.LocalBinder).service
            service?.let {
                if (!it.isPaired) {
                    it.requestConnection(this@InsightPairingActivity)
                    it.registerStateCallback(this@InsightPairingActivity)
                    it.registerExceptionCallback(this@InsightPairingActivity)
                    onStateChanged(it.state)
                    pumpSync.connectNewPump()
                }
                isBound = true
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            isBound =false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityInsightPairingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        if (!blePreCheck.prerequisitesCheck(this)) {
            ToastUtils.errorToast(this, getString(app.aaps.core.ui.R.string.need_connect_permission))
            finish()
            return
        }
        binding.yes.setOnClickListener(this)
        binding.no.setOnClickListener(this)
        binding.exit.setOnClickListener(this)
        binding.deviceList.layoutManager = LinearLayoutManager(this)
        binding.deviceList.adapter = deviceAdapter
        bindService(Intent(this, InsightConnectionService::class.java), serviceConnection, BIND_AUTO_CREATE)
    }

    override fun onDestroy() {
        binding.yes.setOnClickListener(null)
        binding.no.setOnClickListener(null)
        binding.exit.setOnClickListener(null)
        binding.deviceList.adapter = null
        service?.run {
            withdrawConnectionRequest(this@InsightPairingActivity)
            unregisterStateCallback(this@InsightPairingActivity)
            unregisterExceptionCallback(this@InsightPairingActivity)
        }
        if (isBound)
            unbindService(serviceConnection)
        super.onDestroy()
    }

    override fun onStart() {
        super.onStart()
        if (service?.state == InsightState.NOT_PAIRED) startBLScan()
    }

    override fun onStop() {
        stopBLScan()
        super.onStop()
    }

    override fun onStateChanged(state: InsightState?) {
        runOnUiThread {
            when (state) {
                InsightState.NOT_PAIRED                           -> {
                    startBLScan()
                    binding.deviceSearchSection.visibility = View.VISIBLE
                    binding.pleaseWaitSection.visibility = View.GONE
                    binding.codeCompareSection.visibility = View.GONE
                    binding.pairingCompletedSection.visibility = View.GONE
                }

                InsightState.CONNECTING,
                InsightState.SATL_CONNECTION_REQUEST,
                InsightState.SATL_KEY_REQUEST,
                InsightState.SATL_VERIFY_DISPLAY_REQUEST,
                InsightState.SATL_VERIFY_CONFIRM_REQUEST,
                InsightState.APP_BIND_MESSAGE                     -> {
                    stopBLScan()
                    binding.deviceSearchSection.visibility = View.GONE
                    binding.pleaseWaitSection.visibility = View.VISIBLE
                    binding.codeCompareSection.visibility = View.GONE
                    binding.pairingCompletedSection.visibility = View.GONE
                }

                InsightState.AWAITING_CODE_CONFIRMATION           -> {
                    stopBLScan()
                    binding.deviceSearchSection.visibility = View.GONE
                    binding.pleaseWaitSection.visibility = View.GONE
                    binding.codeCompareSection.visibility = View.VISIBLE
                    binding.pairingCompletedSection.visibility = View.GONE
                    binding.code.text = service!!.verificationString
                }

                InsightState.DISCONNECTED, InsightState.CONNECTED -> {
                    stopBLScan()
                    binding.deviceSearchSection.visibility = View.GONE
                    binding.pleaseWaitSection.visibility = View.GONE
                    binding.codeCompareSection.visibility = View.GONE
                    binding.pairingCompletedSection.visibility = View.VISIBLE
                }

                else                                              -> Unit
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startBLScan() {
        if (!scanning) {
            val bluetoothAdapter = (context.getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter
            if (bluetoothAdapter != null) {
                if (!bluetoothAdapter.isEnabled) bluetoothAdapter.safeDisable()
                val intentFilter = IntentFilter()
                intentFilter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
                intentFilter.addAction(BluetoothDevice.ACTION_FOUND)
                registerReceiver(broadcastReceiver, intentFilter)
                bluetoothAdapter.startDiscovery()
                scanning = true
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun stopBLScan() {
        if (scanning) {
            unregisterReceiver(broadcastReceiver)
            val bluetoothAdapter = (context.getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter
            bluetoothAdapter?.cancelDiscovery()
            scanning = false
        }
    }

    override fun onClick(v: View) {
        if (v === binding.exit) finish() else if (v === binding.yes) service!!.confirmVerificationString() else if (v === binding.no) service!!.rejectVerificationString()
    }

    override fun onExceptionOccur(e: Exception?) {
        e?.let { ExceptionTranslator.makeToast(this, it) }
    }

    private fun deviceSelected(device: BluetoothDevice) {
        service?.pair(device.address)
    }

    private val broadcastReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            if (action == BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
                (context.getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter.startDiscovery()
            else if (action == BluetoothDevice.ACTION_FOUND) {
                val bluetoothDevice = intent.safeGetParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                bluetoothDevice?.let { deviceAdapter.addDevice(it) }
            }
        }
    }

    private inner class DeviceAdapter : RecyclerView.Adapter<DeviceAdapter.ViewHolder>() {

        private val bluetoothDevices: MutableList<BluetoothDevice> = ArrayList()
        fun addDevice(bluetoothDevice: BluetoothDevice) {
            if (!bluetoothDevices.contains(bluetoothDevice)) {
                bluetoothDevices.add(bluetoothDevice)
                notifyDataSetChanged()
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            return ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.bluetooth_device, parent, false))
        }

        @SuppressLint("MissingPermission")
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val bluetoothDevice = bluetoothDevices[position]
            holder.deviceName.text = if (bluetoothDevice.name == null) bluetoothDevice.address else bluetoothDevice.name
            holder.deviceName.setOnClickListener { deviceSelected(bluetoothDevice) }
        }

        override fun getItemCount(): Int {
            return bluetoothDevices.size
        }

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

            val deviceName: TextView = itemView as TextView
        }
    }
}