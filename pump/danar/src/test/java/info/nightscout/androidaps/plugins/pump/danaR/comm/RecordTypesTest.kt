package info.nightscout.androidaps.plugins.pump.danaR.comm

import info.nightscout.pump.dana.comm.RecordTypes
import org.junit.Assert
import org.junit.jupiter.api.Test

class RecordTypesTest {

    @Test fun runTest() {
        Assert.assertEquals(1.toByte(), RecordTypes.RECORD_TYPE_BOLUS)
    }
}