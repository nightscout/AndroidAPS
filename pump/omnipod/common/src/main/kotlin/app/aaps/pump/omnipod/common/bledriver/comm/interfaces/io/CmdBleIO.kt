package app.aaps.pump.omnipod.common.bledriver.comm.interfaces.io

import app.aaps.pump.omnipod.common.bledriver.comm.command.BleCommand

/**
 * CMD characteristic I/O with protocol helpers for RTS/CTS flow control.
 * Used by MessageIO for control channel.
 */
interface CmdBleIO : BleCharacteristicIO {

    /** Peek at the next incoming command without consuming it. */
    fun peekCommand(): ByteArray?

    /** Send hello packet for connection handshake. @return Result of the send */
    fun hello(): BleSendResult

    /**
     * Receive a packet and verify it matches expected command type.
     * @return Success, incorrect data (with payload), or error
     */
    fun expectCommandType(expected: BleCommand, timeoutMs: Long = BleCharacteristicIO.DEFAULT_IO_TIMEOUT_MS): BleConfirmResult
}
