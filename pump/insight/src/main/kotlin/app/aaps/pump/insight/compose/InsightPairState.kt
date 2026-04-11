package app.aaps.pump.insight.compose

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
import android.os.IBinder
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.utils.extensions.safeDisable
import app.aaps.core.utils.extensions.safeGetParcelableExtra
import app.aaps.pump.insight.connection_service.InsightConnectionService
import app.aaps.pump.insight.descriptors.InsightState
import app.aaps.pump.insight.utils.ExceptionTranslator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Replaces the deleted [app.aaps.pump.insight.app_layer.activities.InsightPairingActivity]. Holds
 * the state and service/receiver lifecycle for the Insight pair wizard sub-screen, so the wizard
 * can live as a Composable inside [InsightComposeContent] instead of a separate activity.
 *
 * [start]/[stop] must be paired via [androidx.compose.runtime.DisposableEffect].
 */
internal class InsightPairState(
    private val context: Context,
    private val pumpSync: PumpSync,
    private val scope: CoroutineScope
) : InsightConnectionService.StateCallback, InsightConnectionService.ExceptionCallback {

    private val _uiState = MutableStateFlow(InsightPairUiState())
    val uiState: StateFlow<InsightPairUiState> = _uiState

    private val _events = MutableSharedFlow<InsightPairStateEvent>(extraBufferCapacity = 2)
    val events: SharedFlow<InsightPairStateEvent> = _events

    private var service: InsightConnectionService? = null
    private var scanning = false
    private var isBound = false

    private val serviceConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            service = (binder as InsightConnectionService.LocalBinder).service
            service?.let {
                if (!it.isPaired) {
                    it.requestConnection(this@InsightPairState)
                    it.registerStateCallback(this@InsightPairState)
                    it.registerExceptionCallback(this@InsightPairState)
                    onStateChanged(it.state)
                } else {
                    // Should not normally happen — the wizard is only reachable from the "Pair"
                    // action, which only exists when the pump is unpaired. Bail out gracefully.
                    _events.tryEmit(InsightPairStateEvent.Finish)
                }
                isBound = true
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            isBound = false
        }
    }

    fun start() {
        context.bindService(
            Intent(context, InsightConnectionService::class.java),
            serviceConnection,
            Context.BIND_AUTO_CREATE
        )
    }

    fun stop() {
        stopBLScan()
        service?.run {
            withdrawConnectionRequest(this@InsightPairState)
            unregisterStateCallback(this@InsightPairState)
            unregisterExceptionCallback(this@InsightPairState)
        }
        if (isBound) {
            context.unbindService(serviceConnection)
            isBound = false
        }
    }

    fun onDeviceSelected(device: InsightPairDevice) {
        service?.pair(device.address)
    }

    fun onConfirmCode() {
        service?.confirmVerificationString()
    }

    fun onRejectCode() {
        service?.rejectVerificationString()
    }

    override fun onStateChanged(state: InsightState?) {
        scope.launch(Dispatchers.Main.immediate) {
            when (state) {
                InsightState.NOT_PAIRED                           -> {
                    startBLScan()
                    _uiState.update { it.copy(step = InsightPairStep.SEARCH) }
                }

                InsightState.CONNECTING,
                InsightState.SATL_CONNECTION_REQUEST,
                InsightState.SATL_KEY_REQUEST,
                InsightState.SATL_VERIFY_DISPLAY_REQUEST,
                InsightState.SATL_VERIFY_CONFIRM_REQUEST,
                InsightState.APP_BIND_MESSAGE                     -> {
                    stopBLScan()
                    _uiState.update { it.copy(step = InsightPairStep.CONNECTING) }
                }

                InsightState.AWAITING_CODE_CONFIRMATION           -> {
                    stopBLScan()
                    _uiState.update {
                        it.copy(
                            step = InsightPairStep.CODE_COMPARE,
                            verificationCode = service?.verificationString.orEmpty()
                        )
                    }
                }

                InsightState.DISCONNECTED, InsightState.CONNECTED -> {
                    stopBLScan()
                    _uiState.update { it.copy(step = InsightPairStep.COMPLETED) }
                }

                else                                              -> Unit
            }
        }
    }

    override fun onPumpPaired() {
        pumpSync.connectNewPump()
    }

    override fun onExceptionOccur(e: Exception?) {
        e?.let { ex ->
            scope.launch(Dispatchers.Main.immediate) {
                ExceptionTranslator.makeToast(context, ex)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startBLScan() {
        if (scanning) return
        val bluetoothAdapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter ?: return
        if (!bluetoothAdapter.isEnabled) bluetoothAdapter.safeDisable()
        val intentFilter = IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            addAction(BluetoothDevice.ACTION_FOUND)
        }
        context.registerReceiver(broadcastReceiver, intentFilter)
        bluetoothAdapter.startDiscovery()
        scanning = true
    }

    @SuppressLint("MissingPermission")
    private fun stopBLScan() {
        if (!scanning) return
        runCatching { context.unregisterReceiver(broadcastReceiver) }
        val bluetoothAdapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        bluetoothAdapter?.cancelDiscovery()
        scanning = false
    }

    private val broadcastReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.action) {
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    (ctx.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter?.startDiscovery()
                }

                BluetoothDevice.ACTION_FOUND               -> {
                    val device = intent.safeGetParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    device?.let { addDevice(it) }
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun addDevice(device: BluetoothDevice) {
        val address = device.address
        val name = device.name ?: address
        _uiState.update { state ->
            if (state.devices.any { it.address == address }) state
            else state.copy(devices = state.devices + InsightPairDevice(address = address, name = name))
        }
    }
}

internal sealed class InsightPairStateEvent {
    data object Finish : InsightPairStateEvent()
}
