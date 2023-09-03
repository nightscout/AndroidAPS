package info.nightscout.core.extensions

import info.nightscout.database.entities.TemporaryTarget
import info.nightscout.interfaces.GlucoseUnit
import info.nightscout.sharedtests.TestBaseWithProfile
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class TemporaryTargetExtensionKtTest : TestBaseWithProfile() {

    private val temporaryTarget = TemporaryTarget(
        id = 0,
        version = 0,
        dateCreated = -1,
        isValid = true,
        referenceId = null,
        interfaceIDs_backing = null,
        timestamp = 0,
        utcOffset = 0,
        reason = TemporaryTarget.Reason.AUTOMATION,
        highTarget = 120.0,
        lowTarget = 110.0,
        duration = 1800000
    )
    @Test
    fun lowValueToUnitsToString() {
        Assertions.assertEquals("110", temporaryTarget.lowValueToUnitsToString(GlucoseUnit.MGDL))
        Assertions.assertEquals("6.1", temporaryTarget.lowValueToUnitsToString(GlucoseUnit.MMOL))
    }
    @Test
    fun highValueToUnitsToString() {
        Assertions.assertEquals("120", temporaryTarget.highValueToUnitsToString(GlucoseUnit.MGDL))
        Assertions.assertEquals("6.7", temporaryTarget.highValueToUnitsToString(GlucoseUnit.MMOL))
    }
    @Test
    fun target() {
        Assertions.assertEquals(115.0, temporaryTarget.target())
    }
}