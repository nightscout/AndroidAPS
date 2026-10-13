package app.aaps.pump.common.hw.rileylink.ble.operations

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Tests for BLECommOperationResult
 */
class BLECommOperationResultTest {

    private lateinit var result: BLECommOperationResult

    @BeforeEach
    fun setup() {
        result = BLECommOperationResult()
    }

    @Test
    fun `initial state has null value`() {
        assertNull(result.value)
    }

    @Test
    fun `initial state has RESULT_NONE code`() {
        assertEquals(BLECommOperationResult.RESULT_NONE, result.resultCode)
    }

    @Test
    fun `can set value`() {
        val testValue = byteArrayOf(0x01, 0x02, 0x03)

        result.value = testValue

        assertNotNull(result.value)
        assertArrayEquals(testValue, result.value)
    }

    @Test
    fun `can set result code to SUCCESS`() {
        result.resultCode = BLECommOperationResult.RESULT_SUCCESS

        assertEquals(BLECommOperationResult.RESULT_SUCCESS, result.resultCode)
    }

    @Test
    fun `can set result code to TIMEOUT`() {
        result.resultCode = BLECommOperationResult.RESULT_TIMEOUT

        assertEquals(BLECommOperationResult.RESULT_TIMEOUT, result.resultCode)
    }

    @Test
    fun `can set result code to BUSY`() {
        result.resultCode = BLECommOperationResult.RESULT_BUSY

        assertEquals(BLECommOperationResult.RESULT_BUSY, result.resultCode)
    }

    @Test
    fun `can set result code to INTERRUPTED`() {
        result.resultCode = BLECommOperationResult.RESULT_INTERRUPTED

        assertEquals(BLECommOperationResult.RESULT_INTERRUPTED, result.resultCode)
    }

    @Test
    fun `can set result code to NOT_CONFIGURED`() {
        result.resultCode = BLECommOperationResult.RESULT_NOT_CONFIGURED

        assertEquals(BLECommOperationResult.RESULT_NOT_CONFIGURED, result.resultCode)
    }

    @Test
    fun `RESULT_NONE constant has value 0`() {
        assertEquals(0, BLECommOperationResult.RESULT_NONE)
    }

    @Test
    fun `RESULT_SUCCESS constant has value 1`() {
        assertEquals(1, BLECommOperationResult.RESULT_SUCCESS)
    }

    @Test
    fun `RESULT_TIMEOUT constant has value 2`() {
        assertEquals(2, BLECommOperationResult.RESULT_TIMEOUT)
    }

    @Test
    fun `RESULT_BUSY constant has value 3`() {
        assertEquals(3, BLECommOperationResult.RESULT_BUSY)
    }

    @Test
    fun `RESULT_INTERRUPTED constant has value 4`() {
        assertEquals(4, BLECommOperationResult.RESULT_INTERRUPTED)
    }

    @Test
    fun `RESULT_NOT_CONFIGURED constant has value 5`() {
        assertEquals(5, BLECommOperationResult.RESULT_NOT_CONFIGURED)
    }

    @Test
    fun `can create successful result with value`() {
        val testValue = byteArrayOf(0xAA.toByte(), 0xBB.toByte())

        result.resultCode = BLECommOperationResult.RESULT_SUCCESS
        result.value = testValue

        assertEquals(BLECommOperationResult.RESULT_SUCCESS, result.resultCode)
        assertArrayEquals(testValue, result.value)
    }

    @Test
    fun `can create timeout result without value`() {
        result.resultCode = BLECommOperationResult.RESULT_TIMEOUT

        assertEquals(BLECommOperationResult.RESULT_TIMEOUT, result.resultCode)
        assertNull(result.value)
    }

    @Test
    fun `value can be empty array`() {
        result.value = byteArrayOf()

        assertNotNull(result.value)
        assertEquals(0, result.value?.size)
    }

    @Test
    fun `value can be large array`() {
        val largeValue = ByteArray(256) { i -> i.toByte() }

        result.value = largeValue

        assertNotNull(result.value)
        assertEquals(256, result.value?.size)
    }

    @Test
    fun `can overwrite existing value`() {
        val firstValue = byteArrayOf(0x01, 0x02)
        val secondValue = byteArrayOf(0x03, 0x04, 0x05)

        result.value = firstValue
        result.value = secondValue

        assertArrayEquals(secondValue, result.value)
    }

    @Test
    fun `can overwrite existing result code`() {
        result.resultCode = BLECommOperationResult.RESULT_SUCCESS
        result.resultCode = BLECommOperationResult.RESULT_TIMEOUT

        assertEquals(BLECommOperationResult.RESULT_TIMEOUT, result.resultCode)
    }

    @Test
    fun `multiple instances are independent`() {
        val result1 = BLECommOperationResult()
        val result2 = BLECommOperationResult()

        result1.resultCode = BLECommOperationResult.RESULT_SUCCESS
        result1.value = byteArrayOf(0x01)

        result2.resultCode = BLECommOperationResult.RESULT_TIMEOUT
        result2.value = byteArrayOf(0x02)

        assertEquals(BLECommOperationResult.RESULT_SUCCESS, result1.resultCode)
        assertEquals(BLECommOperationResult.RESULT_TIMEOUT, result2.resultCode)
        assertArrayEquals(byteArrayOf(0x01), result1.value)
        assertArrayEquals(byteArrayOf(0x02), result2.value)
    }

    @Test
    fun `realistic success scenario`() {
        // Simulate successful read operation
        val responseData = byteArrayOf(0xA7.toByte(), 0x12, 0x34, 0x56)

        result.resultCode = BLECommOperationResult.RESULT_SUCCESS
        result.value = responseData

        assertEquals(BLECommOperationResult.RESULT_SUCCESS, result.resultCode)
        assertNotNull(result.value)
        assertEquals(4, result.value?.size)
    }

    @Test
    fun `realistic timeout scenario`() {
        // Simulate operation that timed out
        result.resultCode = BLECommOperationResult.RESULT_TIMEOUT
        result.value = null

        assertEquals(BLECommOperationResult.RESULT_TIMEOUT, result.resultCode)
        assertNull(result.value)
    }

    @Test
    fun `realistic busy scenario`() {
        // Simulate operation when device is busy
        result.resultCode = BLECommOperationResult.RESULT_BUSY

        assertEquals(BLECommOperationResult.RESULT_BUSY, result.resultCode)
    }

    @Test
    fun `realistic interrupted scenario`() {
        // Simulate interrupted operation
        result.resultCode = BLECommOperationResult.RESULT_INTERRUPTED

        assertEquals(BLECommOperationResult.RESULT_INTERRUPTED, result.resultCode)
    }

    @Test
    fun `realistic not configured scenario`() {
        // Simulate operation when device not configured
        result.resultCode = BLECommOperationResult.RESULT_NOT_CONFIGURED

        assertEquals(BLECommOperationResult.RESULT_NOT_CONFIGURED, result.resultCode)
    }
}
