package app.aaps.pump.diaconn.service

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/** Which pump log records a history read asks for. The pump numbers its log 0..9999, then 0 again with a higher wrap. */
class DiaconnLogRangesTest {

    @Test
    fun theNewRecordsOfTheSameWrapAreRead() {
        // A real G8: AAPS had record 4053, the pump answered 4055 (one past its newest).
        assertThat(DiaconnG8Service.logRanges(4053, 0, 4055, 0)).containsExactly(4054 to 4055)
    }

    @Test
    fun nothingIsReadWhenNothingIsNew() {
        assertThat(DiaconnG8Service.logRanges(4054, 0, 4055, 0)).isEmpty()
    }

    // It read to 9999 and then never the new wrap, and AAPS records boluses only from the pump log.
    @Test
    fun afterAWrapTheOldEndAndTheNewStartAreRead() {
        assertThat(DiaconnG8Service.logRanges(9995, 0, 3, 1)).containsExactly(9996 to 10000, 0 to 3).inOrder()
    }

    @Test
    fun withTheOldWrapReadToItsEndOnlyTheNewOneIsRead() {
        assertThat(DiaconnG8Service.logRanges(9999, 0, 3, 1)).containsExactly(0 to 3)
    }

    // The whole log is 910 pages of 2 s each: it starts at the newest records, as after a first pairing.
    @Test
    fun moreThanAWholeLogBehindStartsAtTheNewestRecords() {
        assertThat(DiaconnG8Service.logRanges(500, 0, 4055, 2)).containsExactly(4053 to 4055)
    }

    @Test
    fun aResetPumpWithALowerWrapReadsNothingOld() {
        assertThat(DiaconnG8Service.logRanges(4054, 3, 4060, 2)).containsExactly(4055 to 4060)
    }
}
