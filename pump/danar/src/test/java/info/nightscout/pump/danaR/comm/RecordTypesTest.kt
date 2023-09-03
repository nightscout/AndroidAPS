package info.nightscout.pump.danaR.comm

import info.nightscout.pump.dana.comm.RecordTypes
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class RecordTypesTest {

    @Test fun runTest() {
        Assertions.assertEquals(1.toByte(), RecordTypes.RECORD_TYPE_BOLUS)
    }
}