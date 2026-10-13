package info.nightscout.comboctl.base

import app.aaps.shared.tests.TestBase
import kotlin.test.Test
import kotlin.test.assertEquals

class CRCTest : TestBase() {

    @Test
    fun verifyChecksum() {
        val inputData = "0123456789abcdef".toByteArray(Charsets.UTF_8).toList()

        val expectedChecksum = 0x02A2
        val actualChecksum = calculateCRC16MCRF4XX(inputData)
        assertEquals(expectedChecksum, actualChecksum)
    }
}
