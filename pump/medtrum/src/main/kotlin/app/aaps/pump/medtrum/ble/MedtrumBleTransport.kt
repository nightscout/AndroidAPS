package app.aaps.pump.medtrum.ble

import app.aaps.core.interfaces.pump.ble.BleTransport

interface MedtrumBleCallback {

    fun onConnected()
    fun onDisconnected()

    /** Pump-initiated notification from READ characteristic (state updates). */
    fun onNotification(data: ByteArray)

    /** Command response from WRITE characteristic (indication). */
    fun onIndication(data: ByteArray)
    fun onSendMessageError(reason: String, isRetryAble: Boolean)
}

/**
 * Medtrum-specific extension of [BleTransport].
 *
 * Adds methods needed by [MedtrumService] beyond the generic BLE interface:
 * - [connect] — scan-or-connect with SN matching
 * - [disconnect] — clean disconnect with logging
 * - [sendMessage] — chunked write with sequence numbers
 * - [setMedtrumCallback] — Medtrum-specific event callbacks
 * - [setCachedAddress] — pre-seed device address from wizard BLE scan
 */
interface MedtrumBleTransport : BleTransport {

    fun connect(from: String, deviceSN: Long): Boolean
    fun disconnect(from: String)
    fun sendMessage(message: ByteArray)
    fun setMedtrumCallback(callback: MedtrumBleCallback?)

    /** Pre-seed the cached device address from the wizard BLE scan step. */
    fun setCachedAddress(address: String)
}
