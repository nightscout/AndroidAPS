package app.aaps.plugins.sync.xdrip.extensions

import app.aaps.core.data.model.IDs
import app.aaps.core.data.model.TB
import app.aaps.core.data.model.TE
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/** Covers the xdrip [TB] toJson: null-profile short-circuit, absolute vs percent rate, and pump ids. */
class TemporaryBasalExtensionTest : TestBase() {

    private val dateUtil: DateUtil = mock()
    private val profile: Profile = mock()

    @BeforeEach
    fun setup() {
        whenever(dateUtil.toISOString(any())).thenReturn("2023-01-01T00:00:00Z")
        whenever(profile.getBasal(anyLong())).thenReturn(1.0)
    }

    private fun tb(isAbsolute: Boolean, rate: Double, ids: IDs = IDs()) =
        TB(timestamp = 1000L, type = TB.Type.NORMAL, isAbsolute = isAbsolute, rate = rate, duration = T.mins(30).msecs(), ids = ids)

    @Test
    fun nullProfileReturnsNull() {
        assertThat(tb(isAbsolute = true, rate = 1.5).toJson(isAdd = true, profile = null, dateUtil = dateUtil)).isNull()
    }

    @Test
    fun absoluteRate() {
        val json = tb(isAbsolute = true, rate = 1.5).toJson(isAdd = true, profile = profile, dateUtil = dateUtil)!!
        assertThat(json.getString("eventType")).isEqualTo(TE.Type.TEMPORARY_BASAL.text)
        assertThat(json.getDouble("absolute")).isEqualTo(1.5)
        assertThat(json.getDouble("rate")).isEqualTo(1.5) // convertedToAbsolute == rate when absolute
        assertThat(json.getLong("durationInMilliseconds")).isEqualTo(T.mins(30).msecs())
        assertThat(json.getString("type")).isEqualTo("NORMAL")
    }

    @Test
    fun percentRate() {
        val json = tb(isAbsolute = false, rate = 150.0).toJson(isAdd = true, profile = profile, dateUtil = dateUtil)!!
        assertThat(json.getDouble("percent")).isEqualTo(50.0)  // rate - 100
        assertThat(json.getDouble("rate")).isEqualTo(1.5)      // basal(1.0) * 150 / 100
    }

    @Test
    fun includesPumpIds() {
        val ids = IDs(pumpId = 7L, endId = 8L, pumpType = PumpType.USER, pumpSerial = "S", nightscoutId = "N")
        val json = tb(isAbsolute = true, rate = 1.5, ids = ids).toJson(isAdd = true, profile = profile, dateUtil = dateUtil)!!
        assertThat(json.getLong("pumpId")).isEqualTo(7L)
        assertThat(json.getLong("endId")).isEqualTo(8L)
        assertThat(json.getString("pumpType")).isEqualTo("USER")
        assertThat(json.getString("_id")).isEqualTo("N")
    }
}
