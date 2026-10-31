package app.aaps.pump.common.hw.rileylink.ble

import app.aaps.pump.common.hw.rileylink.ble.defs.RileyLinkBLEError
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Tests for RileyLink communication exception handling
 */
class RileyLinkCommunicationExceptionTest {

    @Test
    fun `exception with CodingErrors has correct message`() {
        val exception = RileyLinkCommunicationException(RileyLinkBLEError.CodingErrors)

        assertEquals("Coding Errors encountered during decode of RileyLink packet.", exception.message)
        assertEquals(RileyLinkBLEError.CodingErrors, exception.errorCode)
    }

    @Test
    fun `exception with Timeout has correct message`() {
        val exception = RileyLinkCommunicationException(RileyLinkBLEError.Timeout)

        assertEquals("Timeout", exception.message)
        assertEquals(RileyLinkBLEError.Timeout, exception.errorCode)
    }

    @Test
    fun `exception with Interrupted has correct message`() {
        val exception = RileyLinkCommunicationException(RileyLinkBLEError.Interrupted)

        assertEquals("Interrupted", exception.message)
        assertEquals(RileyLinkBLEError.Interrupted, exception.errorCode)
    }

    @Test
    fun `exception with NoResponse has correct message`() {
        val exception = RileyLinkCommunicationException(RileyLinkBLEError.NoResponse)

        assertEquals("No response from RileyLink", exception.message)
        assertEquals(RileyLinkBLEError.NoResponse, exception.errorCode)
    }

    @Test
    fun `exception with TooShortOrNullResponse has correct message`() {
        val exception = RileyLinkCommunicationException(RileyLinkBLEError.TooShortOrNullResponse)

        assertEquals("Too short or null decoded response.", exception.message)
        assertEquals(RileyLinkBLEError.TooShortOrNullResponse, exception.errorCode)
    }

    @Test
    fun `exception without extended text has null extendedErrorText`() {
        val exception = RileyLinkCommunicationException(RileyLinkBLEError.Timeout)

        assertNull(exception.extendedErrorText)
    }

    @Test
    fun `exception with extended text stores it correctly`() {
        val extendedText = "Additional error details here"
        val exception = RileyLinkCommunicationException(RileyLinkBLEError.CodingErrors, extendedText)

        assertEquals(extendedText, exception.extendedErrorText)
    }

    @Test
    fun `exception with extended text still has correct error message`() {
        val extendedText = "Additional error details"
        val exception = RileyLinkCommunicationException(RileyLinkBLEError.Timeout, extendedText)

        assertEquals("Timeout", exception.message)
        assertEquals(extendedText, exception.extendedErrorText)
    }

    @Test
    fun `exception can be thrown and caught`() {
        try {
            throw RileyLinkCommunicationException(RileyLinkBLEError.Timeout)
        } catch (e: RileyLinkCommunicationException) {
            assertEquals(RileyLinkBLEError.Timeout, e.errorCode)
        }
    }
    @Test
    fun `different error codes create different exceptions`() {
        val exception1 = RileyLinkCommunicationException(RileyLinkBLEError.Timeout)
        val exception2 = RileyLinkCommunicationException(RileyLinkBLEError.CodingErrors)

        assert(exception1.errorCode != exception2.errorCode)
        assert(exception1.message != exception2.message)
    }

    @Test
    fun `exception with empty extended text`() {
        val exception = RileyLinkCommunicationException(RileyLinkBLEError.Timeout, "")

        assertEquals("", exception.extendedErrorText)
        assertEquals("Timeout", exception.message)
    }

    @Test
    fun `exception with long extended text`() {
        val longText = "This is a very long error message that contains detailed information about " +
            "what went wrong during the communication with the RileyLink device. It includes " +
            "technical details, hex dumps, and debugging information that might be useful for " +
            "troubleshooting the issue."

        val exception = RileyLinkCommunicationException(RileyLinkBLEError.CodingErrors, longText)

        assertEquals(longText, exception.extendedErrorText)
    }

    @Test
    fun `errorCode property is accessible`() {
        val exception = RileyLinkCommunicationException(RileyLinkBLEError.NoResponse)

        val errorCode = exception.errorCode

        assertNotNull(errorCode)
        assertEquals(RileyLinkBLEError.NoResponse, errorCode)
    }

    @Test
    fun `exception can be created with all error types`() {
        val errors = listOf(
            RileyLinkBLEError.CodingErrors,
            RileyLinkBLEError.Timeout,
            RileyLinkBLEError.Interrupted,
            RileyLinkBLEError.NoResponse,
            RileyLinkBLEError.TooShortOrNullResponse
        )

        errors.forEach { errorType ->
            val exception = RileyLinkCommunicationException(errorType)
            assertEquals(errorType, exception.errorCode)
            assertNotNull(exception.message)
        }
    }

    @Test
    fun `realistic CodingErrors scenario with extended text`() {
        val hexData = "Input data: A9 6C 72\nWarn: coding error at byte 2"
        val exception = RileyLinkCommunicationException(RileyLinkBLEError.CodingErrors, hexData)

        assertEquals(RileyLinkBLEError.CodingErrors, exception.errorCode)
        assertEquals("Coding Errors encountered during decode of RileyLink packet.", exception.message)
        assertEquals(hexData, exception.extendedErrorText)
    }

    @Test
    fun `realistic Timeout scenario`() {
        val exception = RileyLinkCommunicationException(RileyLinkBLEError.Timeout, "Operation timed out after 22000ms")

        assertEquals(RileyLinkBLEError.Timeout, exception.errorCode)
        assertEquals("Timeout", exception.message)
        assertEquals("Operation timed out after 22000ms", exception.extendedErrorText)
    }

    @Test
    fun `realistic TooShortOrNullResponse scenario`() {
        val exception = RileyLinkCommunicationException(
            RileyLinkBLEError.TooShortOrNullResponse,
            "Expected at least 3 bytes, got 1"
        )

        assertEquals(RileyLinkBLEError.TooShortOrNullResponse, exception.errorCode)
        assertEquals("Too short or null decoded response.", exception.message)
    }

    @Test
    fun `exception can be re-thrown`() {
        var caught: Boolean

        try {
            try {
                throw RileyLinkCommunicationException(RileyLinkBLEError.NoResponse)
            } catch (e: RileyLinkCommunicationException) {
                throw e
            }
        } catch (e: RileyLinkCommunicationException) {
            caught = true
            assertEquals(RileyLinkBLEError.NoResponse, e.errorCode)
        }

        assert(caught)
    }

    @Test
    fun `stack trace is preserved`() {
        val exception = RileyLinkCommunicationException(RileyLinkBLEError.Timeout)

        assertNotNull(exception.stackTrace)
        assert(exception.stackTrace.isNotEmpty())
    }
}
