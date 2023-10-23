package info.nightscout.androidaps.plugins.pump.medtronic.comm.history.pump

import info.nightscout.androidaps.plugins.pump.medtronic.MedtronicTestBase
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Created by andy on 4/9/19.
 *
 */
class PumpHistoryEntryUTest : MedtronicTestBase() {

    @Test
    fun checkIsAfter() {
        val dateObject = 20191010000000L
        val queryObject = 20191009000000L
        val phe = PumpHistoryEntry()
        phe.atechDateTime = dateObject
        assertThat(phe.isAfter(queryObject)).isTrue()
    }
}
