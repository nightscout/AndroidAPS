package app.aaps.plugins.sync.xdrip.extensions

import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.IDs
import app.aaps.core.data.model.TE
import app.aaps.core.data.model.TT
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/** Covers the xdrip [TT] toJson: duration/target mapping when lowTarget is set vs skipped when zero. */
class TemporaryTargetExtensionTest : TestBase() {

    private val dateUtil: DateUtil = mock()
    private val profileUtil: ProfileUtil = mock()

    @BeforeEach
    fun setup() {
        whenever(dateUtil.toISOString(any())).thenReturn("2023-01-01T00:00:00Z")
        whenever(profileUtil.units).thenReturn(GlucoseUnit.MMOL)
        whenever(profileUtil.fromMgdlToUnits(any(), any())).thenReturn(5.5)
    }

    private fun tt(low: Double, high: Double, ids: IDs = IDs()) =
        TT(timestamp = 1000L, reason = TT.Reason.CUSTOM, highTarget = high, lowTarget = low, duration = T.mins(30).msecs(), ids = ids)

    @Test
    fun mapsTargetsWhenLowTargetPositive() {
        val json = tt(low = 80.0, high = 120.0).toJson(isAdd = true, dateUtil = dateUtil, profileUtil = profileUtil)
        assertThat(json.getString("eventType")).isEqualTo(TE.Type.TEMPORARY_TARGET.text)
        assertThat(json.getLong("durationInMilliseconds")).isEqualTo(T.mins(30).msecs())
        assertThat(json.getLong("duration")).isEqualTo(30L)
        assertThat(json.getDouble("targetBottom")).isEqualTo(5.5)
        assertThat(json.getDouble("targetTop")).isEqualTo(5.5)
        assertThat(json.getString("units")).isEqualTo(GlucoseUnit.MMOL.asText)
        assertThat(json.has("reason")).isTrue()
    }

    @Test
    fun skipsTargetsWhenLowTargetZero() {
        val json = tt(low = 0.0, high = 0.0).toJson(isAdd = true, dateUtil = dateUtil, profileUtil = profileUtil)
        assertThat(json.has("targetBottom")).isFalse()
        assertThat(json.has("reason")).isFalse()
    }

    @Test
    fun includesNightscoutIdOnAdd() {
        val json = tt(low = 80.0, high = 120.0, ids = IDs(nightscoutId = "N"))
            .toJson(isAdd = true, dateUtil = dateUtil, profileUtil = profileUtil)
        assertThat(json.getString("_id")).isEqualTo("N")
    }
}
