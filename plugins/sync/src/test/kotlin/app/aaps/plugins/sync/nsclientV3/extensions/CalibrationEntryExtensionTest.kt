package app.aaps.plugins.sync.nsclientV3.extensions

import app.aaps.core.data.model.CAL
import app.aaps.core.data.model.IDs
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class CalibrationEntryExtensionTest {

    @Test
    fun roundTripPreservesFields() {
        val cal = CAL(
            timestamp = 1_700_000_000_000L,
            fingerstickMgdl = 120.0,
            sensorMgdlAtPairing = 110.0,
            ids = IDs(nightscoutId = "abc")
        )
        val back = cal.toNSMbgV3().toCAL()
        assertThat(back.timestamp).isEqualTo(cal.timestamp)
        assertThat(back.fingerstickMgdl).isEqualTo(120.0)
        assertThat(back.sensorMgdlAtPairing).isEqualTo(110.0)
        assertThat(back.ids.nightscoutId).isEqualTo("abc")
    }

    @Test
    fun toNSMbgV3SetsCalibrationMarker() {
        val cal = CAL(timestamp = 1L, fingerstickMgdl = 100.0, sensorMgdlAtPairing = 95.0)
        assertThat(cal.toNSMbgV3().isCalibration).isTrue()
    }

    @Test
    fun onlyNsIdAddedDetectsIdOnlyChange() {
        val previous = CAL(id = 1, timestamp = 1L, fingerstickMgdl = 100.0, sensorMgdlAtPairing = 95.0, ids = IDs(nightscoutId = null))
        val current = CAL(id = 2, timestamp = 1L, fingerstickMgdl = 100.0, sensorMgdlAtPairing = 95.0, ids = IDs(nightscoutId = "abc"))
        assertThat(current.onlyNsIdAdded(previous)).isTrue()
    }

    @Test
    fun onlyNsIdAddedFalseWhenContentChanged() {
        val previous = CAL(id = 1, timestamp = 1L, fingerstickMgdl = 100.0, sensorMgdlAtPairing = 95.0, ids = IDs(nightscoutId = null))
        val current = CAL(id = 2, timestamp = 1L, fingerstickMgdl = 105.0, sensorMgdlAtPairing = 95.0, ids = IDs(nightscoutId = "abc"))
        assertThat(current.onlyNsIdAdded(previous)).isFalse()
    }
}
