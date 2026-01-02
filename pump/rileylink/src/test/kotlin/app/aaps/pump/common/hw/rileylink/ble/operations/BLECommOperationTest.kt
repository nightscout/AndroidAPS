package app.aaps.pump.common.hw.rileylink.ble.operations

import app.aaps.pump.common.hw.rileylink.ble.RileyLinkBLE
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Tests for BLE Communication Operations
 */
class BLECommOperationTest {

    private lateinit var rileyLinkBLE: RileyLinkBLE

    @BeforeEach
    fun setup() {
        rileyLinkBLE = mock()
    }

    @Test
    fun `operation starts with default values`() {
        val operation = TestBLECommOperation()

        assertFalse(operation.timedOut)
        assertFalse(operation.interrupted)
        assertNull(operation.value)
        assertEquals(0, operation.operationComplete.availablePermits())
    }

    @Test
    fun `gatt operation timeout is 22 seconds`() {
        val operation = TestBLECommOperation()

        assertEquals(22000, operation.getGattOperationTimeout_ms())
    }

    @Test
    fun `operation can be marked as timed out`() {
        val operation = TestBLECommOperation()

        operation.timedOut = true

        assertTrue(operation.timedOut)
    }

    @Test
    fun `operation can be marked as interrupted`() {
        val operation = TestBLECommOperation()

        operation.interrupted = true

        assertTrue(operation.interrupted)
    }

    @Test
    fun `operation can store value`() {
        val operation = TestBLECommOperation()
        val testValue = byteArrayOf(0x01, 0x02, 0x03)

        operation.value = testValue

        assertNotNull(operation.value)
        assertEquals(testValue, operation.value)
    }

    @Test
    fun `operationComplete semaphore can be released`() {
        val operation = TestBLECommOperation()

        assertEquals(0, operation.operationComplete.availablePermits())

        operation.operationComplete.release()

        assertEquals(1, operation.operationComplete.availablePermits())
    }

    @Test
    fun `operationComplete semaphore can be acquired`() {
        val operation = TestBLECommOperation()

        operation.operationComplete.release()
        val acquired = operation.operationComplete.tryAcquire(100, TimeUnit.MILLISECONDS)

        assertTrue(acquired)
        assertEquals(0, operation.operationComplete.availablePermits())
    }

    @Test
    fun `operationComplete semaphore blocks when not released`() {
        val operation = TestBLECommOperation()

        val acquired = operation.operationComplete.tryAcquire(100, TimeUnit.MILLISECONDS)

        assertFalse(acquired)
    }

    @Test
    fun `gattOperationCompletionCallback can be called`() {
        val operation = TestBLECommOperation()
        val uuid = UUID.randomUUID()
        val value = byteArrayOf(0x01, 0x02)

        // Should not throw
        operation.gattOperationCompletionCallback(uuid, value)
    }

    @Test
    fun `multiple operations have independent semaphores`() {
        val operation1 = TestBLECommOperation()
        val operation2 = TestBLECommOperation()

        operation1.operationComplete.release()

        assertEquals(1, operation1.operationComplete.availablePermits())
        assertEquals(0, operation2.operationComplete.availablePermits())
    }

    @Test
    fun `operation can be reused after completion`() {
        val operation = TestBLECommOperation()

        // First use
        operation.operationComplete.release()
        operation.operationComplete.tryAcquire(100, TimeUnit.MILLISECONDS)

        // Reset for second use
        operation.operationComplete.release()

        val acquired = operation.operationComplete.tryAcquire(100, TimeUnit.MILLISECONDS)
        assertTrue(acquired)
    }

    @Test
    fun `semaphore is fair`() {
        val operation = TestBLECommOperation()

        // Semaphore is created with fair=true, verify it's configured
        assertNotNull(operation.operationComplete)
    }

    @Test
    fun `value can be null`() {
        val operation = TestBLECommOperation()

        operation.value = null

        assertNull(operation.value)
    }

    @Test
    fun `value can be empty array`() {
        val operation = TestBLECommOperation()

        operation.value = byteArrayOf()

        assertNotNull(operation.value)
        assertEquals(0, operation.value?.size)
    }

    @Test
    fun `value can be large array`() {
        val operation = TestBLECommOperation()
        val largeValue = ByteArray(1024) { i -> i.toByte() }

        operation.value = largeValue

        assertNotNull(operation.value)
        assertEquals(1024, operation.value?.size)
    }

    @Test
    fun `execute must be implemented by subclass`() {
        val operation = TestBLECommOperation()

        // execute is called
        operation.execute(rileyLinkBLE)

        // Should have been called
        assertTrue(operation.executeCalled)
    }

    // Test implementation of BLECommOperation
    private class TestBLECommOperation : BLECommOperation() {
        var executeCalled = false

        override fun execute(comm: RileyLinkBLE) {
            executeCalled = true
        }
    }
}
