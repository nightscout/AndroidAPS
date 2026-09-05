package app.aaps.pump.danar.emulator

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.pump.rfcomm.RfcommDevice
import app.aaps.core.interfaces.pump.rfcomm.RfcommSocket
import app.aaps.core.interfaces.pump.rfcomm.RfcommTransport
import app.aaps.pump.utils.CRC
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.LinkedBlockingQueue

/**
 * Emulated RFCOMM transport for DanaR pump testing.
 * Replaces real Bluetooth with an in-process [DanaRPumpEmulator].
 *
 * Adds realistic BT 2.0 delays to match real hardware timing.
 * This is important because the DanaR protocol relies on timing
 * for operations like auto-switching between DanaR and Korean DanaR plugins.
 *
 * Data flow:
 *   App writes to [EmulatorOutputStream] → parses DanaR packet → routes to emulator →
 *   builds response packet → enqueues to [EmulatorInputStream] → app reads response
 */
class EmulatorRfcommTransport(
    val emulator: DanaRPumpEmulator = DanaRPumpEmulator(),
    private val aapsLogger: AAPSLogger? = null,
    private val deviceName: String = "DAN12345AB"
) : RfcommTransport {

    override fun getSocketForDevice(deviceName: String): RfcommSocket {
        aapsLogger?.debug(LTag.PUMPEMULATOR, "Emulator: creating socket for '$deviceName'")
        return EmulatorRfcommSocket()
    }

    override fun getBondedDevices(): List<RfcommDevice> =
        listOf(RfcommDevice(name = deviceName, address = "EM:UL:AT:OR:00:01"))

    /**
     * Emulated RFCOMM socket using blocking queues for I/O.
     */
    inner class EmulatorRfcommSocket : RfcommSocket {

        private val responseQueue = LinkedBlockingQueue<Byte>()
        @Volatile private var connected = false

        override val inputStream: InputStream = EmulatorInputStream()
        override val outputStream: OutputStream = EmulatorOutputStream()
        override val isConnected: Boolean get() = connected

        override fun connect() {
            // Simulate BT 2.0 connection time (~2 seconds for pairing + RFCOMM channel setup)
            @Suppress("SleepInsteadOfDelay")
            Thread.sleep(2000)
            connected = true
            // Wire up callback for multi-packet responses (e.g., history events)
            emulator.onAdditionalResponse = { command, data ->
                val packet = buildResponsePacket(command, data)
                for (byte in packet) responseQueue.put(byte)
            }
            aapsLogger?.debug(LTag.PUMPEMULATOR, "Emulator: socket connected, sending init sequence for ${emulator.state.variant}")

            // DanaR handshake is pump-initiated: the pump sends init messages after connection.
            // The sequence differs by variant:
            //   Korean: 0x0301 → 0x0303 → 0x0302 (finishHandshaking in 0x0302)
            //   DanaR/v2: 0x0301 → 0x0302 → 0x0303 → 0x0304 (finishHandshaking in 0x0304)
            Thread {
                @Suppress("SleepInsteadOfDelay")
                Thread.sleep(500) // pump needs time to prepare init data after connection
                sendInitMessage(0x0301, emulator.processCommand(0x0301, ByteArray(0)))
                Thread.sleep(200)
                when (emulator.state.variant) {
                    DanaRVariant.DANA_R_KOREAN -> {
                        sendInitMessage(0x0303, emulator.processCommand(0x0303, ByteArray(0)))
                        Thread.sleep(200)
                        sendInitMessage(0x0302, emulator.processCommand(0x0302, ByteArray(0)))
                    }

                    else                       -> {
                        sendInitMessage(0x0302, emulator.processCommand(0x0302, ByteArray(0)))
                        Thread.sleep(200)
                        sendInitMessage(0x0303, emulator.processCommand(0x0303, ByteArray(0)))
                        Thread.sleep(200)
                        sendInitMessage(0x0304, emulator.processCommand(0x0304, ByteArray(0)))
                    }
                }
                aapsLogger?.debug(LTag.PUMPEMULATOR, "Emulator: init sequence sent")
            }.start()
        }

        private fun sendInitMessage(command: Int, data: ByteArray) {
            val packet = buildResponsePacket(command, data)
            aapsLogger?.debug(LTag.PUMPEMULATOR, "Emulator TX init: cmd=${String.format("%04X", command)} packetLen=${packet.size}")
            for (byte in packet) responseQueue.put(byte)
        }

        private var readerThread: Thread? = null

        override fun close() {
            connected = false
            // Unblock any blocking reads by interrupting the reader thread
            readerThread?.interrupt()
            responseQueue.clear()
            aapsLogger?.debug(LTag.PUMPEMULATOR, "Emulator: socket closed")
        }

        /**
         * Build a DanaR response packet with proper framing and CRC.
         *
         * Packet layout: [7E][7E][len][F1][CMD_HI][CMD_LO][data...][CRC_HI][CRC_LO][2E][2E]
         * Where len = 1(F1) + 2(cmd) + data.size = data.size + 3
         * Total packet size = 2(start) + 1(len) + len + 2(CRC) + 2(end) = len + 7
         */
        fun buildResponsePacket(command: Int, data: ByteArray): ByteArray {
            val len = data.size + 3 // F1 type byte + 2 command bytes + data
            val packet = ByteArray(len + 7) // + 2 start + 1 len + 2 CRC + 2 end

            // Start marker
            packet[0] = 0x7E; packet[1] = 0x7E
            // Length
            packet[2] = len.toByte()
            // Type
            packet[3] = 0xF1.toByte()
            // Command echo
            packet[4] = (command shr 8 and 0xFF).toByte()
            packet[5] = (command and 0xFF).toByte()
            // Data
            System.arraycopy(data, 0, packet, 6, data.size)
            // CRC (over F1 + command + data = len bytes starting at offset 3)
            val crc = CRC.getCrc16(packet, 3, len)
            packet[packet.size - 4] = (crc.toInt() shr 8 and 0xFF).toByte()
            packet[packet.size - 3] = (crc.toInt() and 0xFF).toByte()
            // End marker
            packet[packet.size - 2] = 0x2E; packet[packet.size - 1] = 0x2E

            return packet
        }

        inner class EmulatorInputStream : InputStream() {

            override fun read(): Int {
                readerThread = Thread.currentThread()
                if (!connected) throw IOException("Socket closed")
                return try {
                    responseQueue.take().toInt() and 0xFF
                } catch (_: InterruptedException) {
                    throw IOException("Socket closed (interrupted)")
                }
            }

            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (!connected) throw IOException("Socket closed")
                val first = read()
                b[off] = first.toByte()
                var count = 1
                // Drain available bytes without blocking
                while (count < len && connected) {
                    val next = responseQueue.poll() ?: break
                    b[off + count] = next
                    count++
                }
                return count
            }

            override fun available(): Int = responseQueue.size

            override fun close() {
                // Socket close() handles cleanup
            }
        }

        /**
         * Output stream that parses DanaR packets and routes to emulator.
         */
        inner class EmulatorOutputStream : OutputStream() {

            private val writeBuffer = mutableListOf<Byte>()

            override fun write(b: Int) {
                writeBuffer.add(b.toByte())
                tryProcessPacket()
            }

            override fun write(b: ByteArray, off: Int, len: Int) {
                aapsLogger?.debug(LTag.PUMPEMULATOR, "Emulator write: $len bytes, buffer was ${writeBuffer.size}")
                for (i in off until off + len) {
                    writeBuffer.add(b[i])
                }
                tryProcessPacket()
            }

            /**
             * Try to extract a complete DanaR packet from the write buffer.
             * Packet format: 7E 7E [len] F1 [CMD_HI CMD_LO] [params...] [CRC16] 2E 2E
             */
            private fun tryProcessPacket() {
                if (writeBuffer.size < 7) return // minimum packet size

                // Check start marker
                if (writeBuffer[0] != 0x7E.toByte() || writeBuffer[1] != 0x7E.toByte()) {
                    aapsLogger?.error(LTag.PUMPEMULATOR, "Emulator RX: wrong start marker, clearing ${writeBuffer.size} bytes")
                    writeBuffer.clear()
                    return
                }

                val len = writeBuffer[2].toInt() and 0xFF // F1 + cmd(2) + params
                val totalLen = len + 7 // 2 start + 1 len + len + 2 CRC + 2 end

                if (writeBuffer.size < totalLen) {
                    aapsLogger?.debug(LTag.PUMPEMULATOR, "Emulator RX: incomplete packet, have ${writeBuffer.size}/$totalLen bytes")
                    return
                }

                // Check end marker
                if (writeBuffer[totalLen - 2] != 0x2E.toByte() || writeBuffer[totalLen - 1] != 0x2E.toByte()) {
                    aapsLogger?.error(LTag.PUMPEMULATOR, "Emulator RX: wrong end marker at len=$len totalLen=$totalLen")
                    writeBuffer.clear()
                    return
                }

                // Extract packet
                val packet = ByteArray(totalLen) { writeBuffer[it] }
                writeBuffer.subList(0, totalLen).clear()

                // Extract command (bytes 4-5, which is offset 3-4 in packet: type byte at 3, then cmd)
                val command = (packet[4].toInt() and 0xFF shl 8) or (packet[5].toInt() and 0xFF)

                // Extract params: after F1(1)+cmd(2) = offset 6, length = len - 3
                val paramsLen = len - 3
                val params = if (paramsLen > 0) {
                    packet.copyOfRange(6, 6 + paramsLen)
                } else {
                    ByteArray(0)
                }

                aapsLogger?.debug(LTag.PUMPEMULATOR, "Emulator RX: cmd=${String.format("%04X", command)} paramsLen=$paramsLen")

                // Process command
                val responseData = emulator.processCommand(command, params)

                // Build response packet
                val responsePacket = buildResponsePacket(command, responseData)

                aapsLogger?.debug(LTag.PUMPEMULATOR, "Emulator TX: cmd=${String.format("%04X", command)} responseLen=${responseData.size} packetLen=${responsePacket.size}")

                // Enqueue response bytes for the input stream
                for (byte in responsePacket) {
                    responseQueue.put(byte)
                }
            }

        }
    }
}
