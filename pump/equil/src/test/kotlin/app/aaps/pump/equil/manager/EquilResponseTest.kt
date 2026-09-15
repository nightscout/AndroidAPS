package app.aaps.pump.equil.manager

import app.aaps.shared.tests.TestBase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer

class EquilResponseTest : TestBase() {

    private lateinit var equilResponse: EquilResponse
    private val createTime = System.currentTimeMillis()

    @BeforeEach
    fun setUp() {
        equilResponse = EquilResponse(createTime)
    }

    @Test
    fun `constructor should set cmdCreateTime`() {
        assertEquals(createTime, equilResponse.cmdCreateTime)
    }

    @Test
    fun `send list should be empty initially`() {
        assertTrue(equilResponse.send.isEmpty())
        assertEquals(0, equilResponse.send.size)
    }

    @Test
    fun `error_message should be null initially`() {
        assertNull(equilResponse.error_message)
    }

    @Test
    fun `delay should have default value of 20`() {
        assertEquals(20L, equilResponse.delay)
    }

    @Test
    fun `hasError should return false when error_message is null`() {
        assertFalse(equilResponse.hasError())
    }

    @Test
    fun `hasError should return true when error_message is set`() {
        equilResponse.error_message = "Test error"
        assertTrue(equilResponse.hasError())
    }

    @Test
    fun `hasError should return true for empty error message`() {
        equilResponse.error_message = ""
        assertTrue(equilResponse.hasError())
    }

    @Test
    fun `add should add buffer to send list`() {
        val buffer = ByteBuffer.allocate(16)
        equilResponse.add(buffer)

        assertEquals(1, equilResponse.send.size)
        assertEquals(buffer, equilResponse.send[0])
    }

    @Test
    fun `add should maintain order of buffers`() {
        val buffer1 = ByteBuffer.allocate(16)
        val buffer2 = ByteBuffer.allocate(32)
        val buffer3 = ByteBuffer.allocate(8)

        equilResponse.add(buffer1)
        equilResponse.add(buffer2)
        equilResponse.add(buffer3)

        assertEquals(3, equilResponse.send.size)
        assertEquals(buffer1, equilResponse.send[0])
        assertEquals(buffer2, equilResponse.send[1])
        assertEquals(buffer3, equilResponse.send[2])
    }

    @Test
    fun `add should handle multiple buffers`() {
        for (i in 0 until 10) {
            equilResponse.add(ByteBuffer.allocate(16))
        }
        assertEquals(10, equilResponse.send.size)
    }

    @Test
    fun `shouldDelay should return true when delay is positive`() {
        equilResponse.delay = 1
        assertTrue(equilResponse.shouldDelay())

        equilResponse.delay = 100
        assertTrue(equilResponse.shouldDelay())

        equilResponse.delay = Long.MAX_VALUE
        assertTrue(equilResponse.shouldDelay())
    }

    @Test
    fun `shouldDelay should return false when delay is zero`() {
        equilResponse.delay = 0
        assertFalse(equilResponse.shouldDelay())
    }

    @Test
    fun `shouldDelay should return false when delay is negative`() {
        equilResponse.delay = -1
        assertFalse(equilResponse.shouldDelay())

        equilResponse.delay = -100
        assertFalse(equilResponse.shouldDelay())
    }

    @Test
    fun `error_message should be settable`() {
        val errorMessage = "Connection timeout"
        equilResponse.error_message = errorMessage
        assertEquals(errorMessage, equilResponse.error_message)
        assertTrue(equilResponse.hasError())
    }

    @Test
    fun `error_message should be clearable`() {
        equilResponse.error_message = "Error"
        assertTrue(equilResponse.hasError())

        equilResponse.error_message = null
        assertFalse(equilResponse.hasError())
    }

    @Test
    fun `delay should be modifiable`() {
        equilResponse.delay = 50
        assertEquals(50L, equilResponse.delay)
        assertTrue(equilResponse.shouldDelay())

        equilResponse.delay = 0
        assertEquals(0L, equilResponse.delay)
        assertFalse(equilResponse.shouldDelay())
    }

    @Test
    fun `send list should support LinkedList operations`() {
        val buffer1 = ByteBuffer.allocate(16)
        val buffer2 = ByteBuffer.allocate(32)

        equilResponse.send.addFirst(buffer1)
        equilResponse.send.addLast(buffer2)

        assertEquals(2, equilResponse.send.size)
        assertEquals(buffer1, equilResponse.send.first())
        assertEquals(buffer2, equilResponse.send.last())
    }

    @Test
    fun `add should work with buffers of different sizes`() {
        equilResponse.add(ByteBuffer.allocate(8))
        equilResponse.add(ByteBuffer.allocate(16))
        equilResponse.add(ByteBuffer.allocate(32))
        equilResponse.add(ByteBuffer.allocate(64))

        assertEquals(4, equilResponse.send.size)
    }

    @Test
    fun `add should work with buffers containing data`() {
        val buffer = ByteBuffer.allocate(16)
        buffer.put(byteArrayOf(0x01, 0x02, 0x03, 0x04))

        equilResponse.add(buffer)

        assertEquals(1, equilResponse.send.size)
        assertEquals(buffer, equilResponse.send[0])
    }

    @Test
    fun `multiple EquilResponse instances should be independent`() {
        val response1 = EquilResponse(1000L)
        val response2 = EquilResponse(2000L)

        response1.error_message = "Error 1"
        response1.delay = 10
        response1.add(ByteBuffer.allocate(16))

        response2.error_message = "Error 2"
        response2.delay = 20
        response2.add(ByteBuffer.allocate(32))
        response2.add(ByteBuffer.allocate(64))

        assertEquals(1000L, response1.cmdCreateTime)
        assertEquals("Error 1", response1.error_message)
        assertEquals(10L, response1.delay)
        assertEquals(1, response1.send.size)

        assertEquals(2000L, response2.cmdCreateTime)
        assertEquals("Error 2", response2.error_message)
        assertEquals(20L, response2.delay)
        assertEquals(2, response2.send.size)
    }
}
