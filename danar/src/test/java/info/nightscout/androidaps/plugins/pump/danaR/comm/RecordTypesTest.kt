package info.nightscout.androidaps.plugins.pump.danaR.comm

import org.junit.Assert
import org.junit.Test

class RecordTypesTest {

    @Test fun runTest() {
        Assert.assertEquals(1.toByte(), info.nightscout.androidaps.dana.comm.RecordTypes.RECORD_TYPE_BOLUS)
    }
}