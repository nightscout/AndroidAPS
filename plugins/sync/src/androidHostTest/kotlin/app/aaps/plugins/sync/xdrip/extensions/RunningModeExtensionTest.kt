package app.aaps.plugins.sync.xdrip.extensions

import app.aaps.core.data.model.IDs
import app.aaps.core.data.model.RM
import app.aaps.core.data.model.TE
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/** Covers the xdrip [RM] toJson: the mode→reported-duration matrix, RESUME error, optional reasons/ids. */
class RunningModeExtensionTest : TestBase() {

    private val dateUtil: DateUtil = mock()

    @BeforeEach
    fun setup() {
        whenever(dateUtil.toISOString(any())).thenReturn("2023-01-01T00:00:00Z")
    }

    private fun rm(mode: RM.Mode, duration: Long = 600_000L, reasons: String? = null, ids: IDs = IDs()) =
        RM(timestamp = 1000L, mode = mode, duration = duration, reasons = reasons, ids = ids)

    @Test
    fun loopModes_reportZeroDuration() {
        val json = rm(RM.Mode.OPEN_LOOP).toJson(isAdd = true, dateUtil = dateUtil)
        assertThat(json.getString("eventType")).isEqualTo(TE.Type.APS_OFFLINE.text)
        assertThat(json.getLong("durationInMilliseconds")).isEqualTo(0L)
        assertThat(json.getLong("originalDuration")).isEqualTo(600_000L)
        assertThat(json.getString("mode")).isEqualTo("OPEN_LOOP")
    }

    @Test
    fun suspendModes_reportRealDuration() {
        val json = rm(RM.Mode.SUSPENDED_BY_USER, duration = 600_000L).toJson(isAdd = true, dateUtil = dateUtil)
        assertThat(json.getLong("durationInMilliseconds")).isEqualTo(600_000L)
        assertThat(json.getLong("duration")).isEqualTo(10L) // minutes
    }

    @Test
    fun resumeMode_throws() {
        assertThrows<IllegalStateException> { rm(RM.Mode.RESUME).toJson(isAdd = true, dateUtil = dateUtil) }
    }

    @Test
    fun includesReasonsAndIds() {
        val json = rm(
            RM.Mode.SUSPENDED_BY_PUMP, reasons = "pump suspended",
            ids = IDs(pumpId = 5L, pumpType = PumpType.USER, pumpSerial = "S", nightscoutId = "N")
        ).toJson(isAdd = true, dateUtil = dateUtil)
        assertThat(json.getString("reasons")).isEqualTo("pump suspended")
        assertThat(json.getLong("pumpId")).isEqualTo(5L)
        assertThat(json.getString("_id")).isEqualTo("N")
    }
}
