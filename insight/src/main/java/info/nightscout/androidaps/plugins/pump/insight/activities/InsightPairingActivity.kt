package info.nightscout.androidaps.plugins.pump.insight.activities

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.*
import android.os.Bundle
import android.os.IBinder
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import info.nightscout.androidaps.activities.NoSplashAppCompatActivity
import info.nightscout.androidaps.insight.R
import info.nightscout.androidaps.insight.databinding.ActivityInsightPairingBinding
import info.nightscout.androidaps.plugins.pump.insight.connection_service.InsightConnectionService
import info.nightscout.androidaps.plugins.pump.insight.connection_service.InsightConnectionService.ExceptionCallback
import info.nightscout.androidaps.plugins.pump.insight.descriptors.InsightState
import info.nightscout.androidaps.plugins.pump.insight.utils.ExceptionTranslator
import java.util.*

class InsightPairingActivity : NoSplashAppCompatActivity(), InsightConnectionService.StateCallback, View.OnClickListener, ExceptionCallback {

    private lateinit var binding: ActivityInsightPairingBinding
    private var scanning = false
    private val deviceAdapter = DeviceAdapter()
    private var service: InsightConnectionService? = null
    private val serviceConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            val newService: InsightConnectionService = (binder as InsightConnectionService.LocalBinder).service
            if (newService.isPaired()) return else {
                newService.requestConnection(this@InsightPairingActivity)
                newService.registerStateCallback(this@InsightPairingActivity)
                newService.registerExceptionCallback(this@InsightPairingActivity)
                onStateChanged(newService.getState())
            }
            service = newService
        }

        override fun onServiceDisconnected(name: ComponentName) {}
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityInsightPairingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.yes.setOnClickListener(this)
        binding.no.setOnClickListener(this)
        binding.exit.setOnClickListener(this)
        binding.deviceList.setLayoutManager(LinearLayoutManager(this))
        binding.deviceList.setAdapter(deviceAdapter)
        bindService(Intent(this, InsightConnectionService::class.java), serviceConnection, BIND_AUTO_CREATE)
    }

    override fun onDestroy() {
        if (service != null) {
            service!!.withdrawConnectionRequest(this@InsightPairingActivity)
            service!!.unregisterStateCallback(this@InsightPairingActivity)
            service!!.unregisterExceptionCallback(this@InsightPairingActivity)
        }
        unbindService(serviceConnection)
        super.onDestroy()
    }

    override fun onStart() {
        super.onStart()
        if (service != null && service!!.state === InsightState.NOT_PAIRED) startBLScan()
    }

    override fun onStop() {
        stopBLScan()
        super.onStop()
    }

    override fun onStateChanged(state: InsightState) {
        runOnUiThread {
            when (state) {
                InsightState.NOT_PAIRED                             -> {
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
                InsightState.APP_BIND_MESSAGE                       -> {
                    stopBLScan()
                    binding.deviceSearchSection.visibility = View.GONE
                    binding.pleaseWaitSection.visibility = View.VISIBLE
                    binding.codeCompareSection.visibility = View.GONE
                    binding.pairingCompletedSection.visibility = View.GONE
                }

                InsightState.AWAITING_CODE_CONFIRMATION             -> {
                    stopBLScan()
                    binding.deviceSearchSection.visibility = View.GONE
                    binding.pleaseWaitSection.visibility = View.GONE
                    binding.codeCompareSection.visibility = View.VISIBLE
                    binding.pairingCompletedSection.visibility = View.GONE
                    binding.code.text = service!!.verificationString
                }

                InsightState.DISCONNECTED, InsightState.CONNECTED   -> {
                    stopBLScan()
                    binding.deviceSearchSection.visibility = View.GONE
                    binding.pleaseWaitSection.visibility = View.GONE
                    binding.codeCompareSection.visibility = View.GONE
                    binding.pairingCompletedSection.visibility = View.VISIBLE
                }

                else                                                -> Unit
            }
        }
    }

    private fun startBLScan() {
        if (!scanning) {
            val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
            if (bluetoothAdapter != null) {
                if (!bluetoothAdapter.isEnabled) bluetoothAdapter.enable()
                val intentFilter = IntentFilter()
                intentFilter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
                intentFilter.addAction(BluetoothDevice.ACTION_FOUND)
                registerReceiver(broadcastReceiver, intentFilter)
                bluetoothAdapter.startDiscovery()
                scanning = true
            }
        }
    }

    private fun stopBLScan() {
        if (scanning) {
            unregisterReceiver(broadcastReceiver)
            val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
            bluetoothAdapter?.cancelDiscovery()
            scanning = false
        }
    }

    override fun onClick(v: View) {
        if (v === binding.exit) finish() else if (v === binding.yes) service!!.confirmVerificationString() else if (v === binding.no) service!!.rejectVerificationString()
    }

    override fun onExceptionOccur(e: Exception) {
        ExceptionTranslator.makeToast(this, e)
    }

    private fun deviceSelected(device: BluetoothDevice) {
        service!!.pair(device.address)
    }

    private val broadcastReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            if (action == BluetoothAdapter.ACTION_DISCOVERY_FINISHED) BluetoothAdapter.getDefaultAdapter().startDiscovery() else if (action == BluetoothDevice.ACTION_FOUND) {
                val bluetoothDevice = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                deviceAdapter.addDevice(bluetoothDevice)
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

        fun clear() {
            bluetoothDevices.clear()
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            return ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.bluetooth_device, parent, false))
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val bluetoothDevice = bluetoothDevices[position]
            holder.deviceName.text = if (bluetoothDevice.name == null) bluetoothDevice.address else bluetoothDevice.name
            holder.deviceName.setOnClickListener { deviceSelected(bluetoothDevice) }
        }

        override fun getItemCount(): Int {
            return bluetoothDevices.size
        }

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

            val deviceName: TextView

            init {
                deviceName = itemView as TextView
            }
        }
    }
}