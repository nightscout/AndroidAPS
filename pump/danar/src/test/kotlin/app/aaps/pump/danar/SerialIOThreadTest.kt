package app.aaps.pump.danar

import android.bluetooth.BluetoothSocket
import app.aaps.pump.dana.DanaPump
import app.aaps.pump.danar.comm.MessageBase
import app.aaps.pump.danar.comm.MessageHashTableBase
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mock
import org.mockito.Mockito.`when`
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream

class SerialIOThreadTest : TestBase() {

    @Mock lateinit var rfCommSocket: BluetoothSocket
    @Mock lateinit var hashTable: MessageHashTableBase
    @Mock lateinit var danaPump: DanaPump
    @Mock lateinit var messageBase: MessageBase

    private lateinit var inputStream: InputStream
    private lateinit var outputStream: OutputStream

    @BeforeEach
    fun setup() {
        // Create mock streams
        inputStream = ByteArrayInputStream(ByteArray(0))
        outputStream = ByteArrayOutputStream()

        `when`(rfCommSocket.inputStream).thenReturn(inputStream)
        `when`(rfCommSocket.outputStream).thenReturn(outputStream)
        `when`(rfCommSocket.isConnected).thenReturn(true)
        `when`(hashTable.findMessage(anyInt())).thenReturn(messageBase)
        `when`(messageBase.messageName).thenReturn("TestMessage")
        `when`(messageBase.command).thenReturn(0x0101)
        `when`(messageBase.rawMessageBytes).thenReturn(createValidPacket())
    }

    @Test
    fun testDisconnect() {
        val thread = SerialIOThread(aapsLogger, rfCommSocket, hashTable, danaPump)
        Thread.sleep(100) // Allow thread to start

        thread.disconnect("Test disconnect")

        Thread.sleep(100) // Allow thread to finish
        // Verify no exceptions thrown
    }

    @Test
    fun testSendMessage_notConnected() {
        `when`(rfCommSocket.isConnected).thenReturn(false)

        val thread = SerialIOThread(aapsLogger, rfCommSocket, hashTable, danaPump)
        Thread.sleep(100) // Allow thread to start

        thread.sendMessage(messageBase)

        // Should not throw exception, just log error
        thread.disconnect("Test cleanup")
    }

    @Test
    fun testSendMessage_connected() {
        val outputBuffer = ByteArrayOutputStream()
        `when`(rfCommSocket.outputStream).thenReturn(outputBuffer)
        `when`(rfCommSocket.isConnected).thenReturn(true)

        val thread = SerialIOThread(aapsLogger, rfCommSocket, hashTable, danaPump)
        Thread.sleep(100) // Allow thread to start

        thread.sendMessage(messageBase)

        // Verify message was written to output stream
        assertThat(outputBuffer.size()).isGreaterThan(0)

        thread.disconnect("Test cleanup")
    }

    @Test
    fun testThreadLifecycle() {
        val thread = SerialIOThread(aapsLogger, rfCommSocket, hashTable, danaPump)

        assertThat(thread.isAlive).isTrue()

        thread.disconnect("Test lifecycle")
        Thread.sleep(200) // Wait for thread to finish

        // Thread should eventually stop
    }

    private fun createValidPacket(): ByteArray {
        // Create a minimal valid Dana packet
        // Format: 0x7E 0x7E [length] [data...] [crc] [crc] 0x2E 0x2E
        return byteArrayOf(
            0x7E.toByte(), 0x7E.toByte(), // Start markers
            0x05.toByte(), // Length (5 bytes of data)
            0x00.toByte(), 0x00.toByte(), // Padding/data
            0x01.toByte(), 0x01.toByte(), // Command
            0x00.toByte(), // Data
            0x00.toByte(), 0x00.toByte(), // CRC (simplified)
            0x2E.toByte(), 0x2E.toByte()  // End markers
        )
    }
}
