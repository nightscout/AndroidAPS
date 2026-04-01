package app.aaps.pump.equil.ble

import app.aaps.core.interfaces.pump.ble.BleTransport

/**
 * Equil-specific extension of [BleTransport].
 *
 * Adds fields needed by [EquilBLE] that aren't part of the generic BLE interface:
 * - [scanAddress]: MAC filter for scanning
 * - [onGattError133]: callback for GATT error 133 (bond issue)
 *
 * Implemented by [EquilBleTransportImpl] (production) and
 * EquilEmulatorBleTransport in :pump:equil-emulator (testing).
 */
interface EquilBleTransport : BleTransport {

    /** MAC address filter for scanning. Set before calling scanner.startScan(). */
    var scanAddress: String?

    /** Called when GATT error 133 occurs, before onConnectionStateChanged(false). */
    var onGattError133: (() -> Unit)?
}
