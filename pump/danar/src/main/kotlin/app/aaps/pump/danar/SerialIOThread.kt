package app.aaps.pump.danar

import android.bluetooth.BluetoothSocket
import android.os.SystemClock
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.utils.notifyAll
import app.aaps.core.utils.waitMillis
import app.aaps.pump.dana.DanaPump
import app.aaps.pump.danar.comm.MessageBase
import app.aaps.pump.danar.comm.MessageHashTableBase
import app.aaps.pump.utils.CRC
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import kotlin.math.max

/**
 * Created by mike on 17.07.2016.
 */
class SerialIOThread(
    private val aapsLogger: AAPSLogger,
    private val rfCommSocket: BluetoothSocket,
    private val hashTable: MessageHashTableBase,
    private val danaPump: DanaPump
) : Thread() {

    private var mInputStream: InputStream = rfCommSocket.inputStream
    private var mOutputStream: OutputStream = rfCommSocket.outputStream
    private var mKeepRunning = true
    private var mReadBuff = ByteArray(0)
    private var processedMessage: MessageBase? = null

    init {
        start()
    }

    override fun run() {
        try {
            while (mKeepRunning) {
                val availableBytes = mInputStream.available()
                // Ask for 1024 byte (or more if available)
                val newData = ByteArray(max(1024, availableBytes))
                val gotBytes = try {
                    mInputStream.read(newData)
                } catch (_: IOException) {
                    break
                }
                // When we are here there is some new data available
                appendToBuffer(newData, gotBytes)

                // process all messages we already got
                while (mReadBuff.size > 3) { // 3rd byte is packet size. continue only if we an determine packet size
                    val extractedBuff = cutMessageFromBuffer() ?: break
                    // message is not complete in buffer (wrong packet calls disconnection)
                    val command = extractedBuff[5].toInt() and 0xFF or (extractedBuff[4].toInt() shl 8 and 0xFF00)
                    val message: MessageBase = if (processedMessage?.command == command) {
                        processedMessage!!
                    } else {
                        // get it from hash table
                        hashTable.findMessage(command)
                    }
                    aapsLogger.debug(LTag.PUMPBTCOMM, "<<<<< ${message.messageName} ${MessageBase.toHexString(extractedBuff)}")

                    // process the message content
                    message.isReceived = true
                    message.handleMessage(extractedBuff)
                    synchronized(message) { message.notifyAll() }
                }
            }
        } catch (e: Exception) {
            if (e.message?.contains("bt socket closed") == true) aapsLogger.error("Thread exception: ", e)
            mKeepRunning = false
        }
        disconnect("EndOfLoop")
    }

    private fun appendToBuffer(newData: ByteArray, gotBytes: Int) {
        // add newData to mReadBuff
        val newReadBuff = ByteArray(mReadBuff.size + gotBytes)
        System.arraycopy(mReadBuff, 0, newReadBuff, 0, mReadBuff.size)
        System.arraycopy(newData, 0, newReadBuff, mReadBuff.size, gotBytes)
        mReadBuff = newReadBuff
    }

    private fun cutMessageFromBuffer(): ByteArray? {
        return if (mReadBuff[0] == 0x7E.toByte() && mReadBuff[1] == 0x7E.toByte()) {
            val length = (mReadBuff[2].toInt() and 0xFF) + 7
            // Check if we have enough data
            if (mReadBuff.size < length) {
                return null
            }
            if (mReadBuff[length - 2] != 0x2E.toByte() || mReadBuff[length - 1] != 0x2E.toByte()) {
                aapsLogger.error("wrong packet length=" + length + " data " + MessageBase.toHexString(mReadBuff))
                disconnect("wrong packet")
                return null
            }
            val crc = CRC.getCrc16(mReadBuff, 3, length - 7)
            val crcByte0 = (crc.toInt() shr 8 and 0xFF).toByte()
            val crcByte1 = (crc.toInt() and 0xFF).toByte()
            val crcByte0received = mReadBuff[length - 4]
            val crcByte1received = mReadBuff[length - 3]
            if (crcByte0 != crcByte0received || crcByte1 != crcByte1received) {
                aapsLogger.error(
                    "CRC Error" + String.format("%02x ", crcByte0) + String.format("%02x ", crcByte1) + String.format(
                        "%02x ",
                        crcByte0received
                    ) + String.format("%02x ", crcByte1received)
                )
                disconnect("crc error")
                return null
            }
            // Packet is verified here. extract data
            val extractedBuff = ByteArray(length)
            System.arraycopy(mReadBuff, 0, extractedBuff, 0, length)
            // remove extracted data from read buffer
            val unprocessedData = ByteArray(mReadBuff.size - length)
            System.arraycopy(mReadBuff, length, unprocessedData, 0, unprocessedData.size)
            mReadBuff = unprocessedData
            extractedBuff
        } else {
            aapsLogger.error("Wrong beginning of packet len=" + mReadBuff.size + "    " + MessageBase.toHexString(mReadBuff))
            disconnect("Wrong beginning of packet")
            null
        }
    }

    @Synchronized fun sendMessage(message: MessageBase) {
        if (!rfCommSocket.isConnected) {
            aapsLogger.error("Socket not connected on sendMessage")
            return
        }
        processedMessage = message
        val messageBytes: ByteArray = message.rawMessageBytes
        aapsLogger.debug(LTag.PUMPBTCOMM, ">>>>> ${message.messageName} ${MessageBase.toHexString(messageBytes)}")
        try {
            mOutputStream.write(messageBytes)
        } catch (e: Exception) {
            aapsLogger.error("sendMessage write exception: ", e)
        }
        synchronized(message) {
            try {
                message.waitMillis(5000)
            } catch (e: InterruptedException) {
                aapsLogger.error("sendMessage InterruptedException", e)
            }
        }
        SystemClock.sleep(200)
        if (!message.isReceived) {
            message.handleMessageNotReceived()
            aapsLogger.error(LTag.PUMPBTCOMM, "Reply not received " + message.messageName)
            if (message.command == 0xF0F1) {
                danaPump.isNewPump = false
                danaPump.reset()
                aapsLogger.debug(LTag.PUMPBTCOMM, "Old firmware detected")
            }
        }
    }

    fun disconnect(reason: String) {
        mKeepRunning = false
        try {
            mInputStream.close()
        } catch (e: Exception) {
            aapsLogger.error(LTag.PUMPBTCOMM, "Error: ${e.localizedMessage}")
        }
        try {
            mOutputStream.close()
        } catch (e: Exception) {
            aapsLogger.error(LTag.PUMPBTCOMM, "Error: ${e.localizedMessage}")
        }
        try {
            rfCommSocket.close()
        } catch (e: Exception) {
            aapsLogger.error(LTag.PUMPBTCOMM, "Error: ${e.localizedMessage}")
        }
        aapsLogger.debug(LTag.PUMPBTCOMM, "Disconnected: $reason")
    }
}
