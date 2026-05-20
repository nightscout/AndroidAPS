package app.aaps.pump.carelevo.compose.smoketest

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.aaps.pump.carelevo.ble.BleClientImpl
import app.aaps.pump.carelevo.ble.commands.MacAddressCommand
import app.aaps.pump.carelevo.ble.commands.MacAddressResponse
import app.aaps.pump.carelevo.ble.gatt.AndroidGattConnection
import app.aaps.pump.carelevo.ble.gatt.GattConnState
import app.aaps.pump.carelevo.ble.gatt.GattEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.util.UUID
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds

/**
 * End-to-end BLE smoke test that exercises the new protocol stack against real hardware.
 *
 * Flow:
 *   1. Look up the [targetMacAddress] via [BluetoothManager]
 *   2. Open an [AndroidGattConnection], wait for `CONNECTED`
 *   3. `discoverServices()` → `enableNotifications(txUuid)`
 *   4. Issue a [MacAddressCommand] via [BleClientImpl]
 *   5. Close the connection and return the decoded response
 *
 * Every phase has a generous timeout — a hang on any of them indicates a bug in the
 * corresponding layer (Android wrapper, protocol correlation, or pump responsiveness).
 *
 * Intended for **unpaired** CareLevo peripherals — `MacAddressCommand` is only valid
 * pre-authentication. Running it against an already-bonded pump will likely time out
 * at step 4. If the pump was paired with this phone previously, unpair via Android
 * Bluetooth settings before running the test.
 *
 * @param rxUuid the characteristic the client WRITES to (CareLevo's `characterRx`).
 * @param txUuid the characteristic the client receives NOTIFICATIONS on (`characterTx`).
 */
@SuppressLint("MissingPermission") // caller ensures BLUETOOTH_CONNECT is granted
suspend fun runMacAddressSmokeTest(
    context: Context,
    targetMacAddress: String,
    rxUuid: UUID,
    txUuid: UUID,
    scope: CoroutineScope
): Result<MacAddressResponse> = runCatching {
    val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    val adapter = manager.adapter ?: error("Bluetooth adapter unavailable")
    require(adapter.isEnabled) { "Bluetooth is disabled" }

    val device = adapter.getRemoteDevice(targetMacAddress.uppercase())
        ?: error("Could not resolve device for MAC $targetMacAddress")

    val gatt = AndroidGattConnection.connect(context, device, scope)
    try {
        withTimeout(CONNECT_TIMEOUT) {
            gatt.events
                .filterIsInstance<GattEvent.ConnectionStateChanged>()
                .first { it.state == GattConnState.CONNECTED }
        }

        withTimeout(DISCOVERY_TIMEOUT) { gatt.discoverServices() }
        withTimeout(NOTIFY_TIMEOUT) { gatt.enableNotifications(txUuid) }

        val client = BleClientImpl(gatt, rxUuid, txUuid, scope)
        withTimeout(REQUEST_TIMEOUT) {
            client.request(MacAddressCommand(key = Random.nextInt(0, 256).toByte()))
        }
    } finally {
        gatt.close()
    }
}

private val CONNECT_TIMEOUT = 10.seconds
private val DISCOVERY_TIMEOUT = 10.seconds
private val NOTIFY_TIMEOUT = 5.seconds
private val REQUEST_TIMEOUT = 10.seconds

/**
 * Self-contained debug dialog that wraps [runMacAddressSmokeTest] with a simple UI.
 *
 * Caller wires visibility via [onDismiss]. DI-free — takes the two UUIDs explicitly so
 * it can be dropped into any screen without adding to the Dagger graph.
 */
@Composable
fun CarelevoBleSmokeTestDialog(
    rxUuid: UUID,
    txUuid: UUID,
    initialMacAddress: String = "",
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var mac by remember { mutableStateOf(initialMacAddress) }
    var running by remember { mutableStateOf(false) }
    var resultLine by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!running) onDismiss() },
        title = { Text("BLE smoke test (dev)") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = mac,
                    onValueChange = { mac = it.uppercase() },
                    label = { Text("Pump MAC (AA:BB:CC:DD:EE:FF)") },
                    enabled = !running,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                resultLine?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !running && mac.isNotBlank(),
                onClick = {
                    running = true
                    resultLine = "Connecting…"
                    scope.launch {
                        val outcome = runMacAddressSmokeTest(
                            context = context,
                            targetMacAddress = mac.trim(),
                            rxUuid = rxUuid,
                            txUuid = txUuid,
                            scope = this
                        )
                        resultLine = outcome.fold(
                            onSuccess = { r -> "OK  mac=${r.macAddress}  checksum=${r.checkSum}" },
                            onFailure = { e -> "FAIL  ${e::class.simpleName}: ${e.message}" }
                        )
                        running = false
                    }
                }
            ) {
                Text(if (running) "Running…" else "Run")
            }
        },
        dismissButton = {
            TextButton(enabled = !running, onClick = onDismiss) {
                Text("Close")
            }
        }
    )

    // Safety: if the caller recomposes with a different onDismiss while a test is running,
    // we keep running to avoid dropping the in-flight connection silently.
    LaunchedEffect(Unit) { /* no-op, placeholder for future lifecycle hooks */ }
}
