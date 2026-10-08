package app.aaps.plugins.sync.xdrip.extensions

import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.IDs
import app.aaps.core.data.model.TE
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/** Covers the xdrip [TE] toJson: base fields, announcement flag, and the optional duration/note/glucose/meta branches. */
class TherapyEventExtensionTest : TestBase() {

    private val dateUtil: DateUtil = mock()

    @BeforeEach
    fun setup() {
        whenever(dateUtil.toISOString(any())).thenReturn("2023-01-01T00:00:00Z")
    }

    private fun te(
        type: TE.Type = TE.Type.NOTE,
        duration: Long = 0L,
        note: String? = null,
        glucose: Double? = null,
        glucoseType: TE.MeterType? = null,
        location: TE.Location? = null,
        arrow: TE.Arrow? = null,
        ids: IDs = IDs()
    ) = TE(
        timestamp = 1000L, type = type, glucoseUnit = GlucoseUnit.MGDL, duration = duration,
        note = note, glucose = glucose, glucoseType = glucoseType, location = location, arrow = arrow, ids = ids
    )

    @Test
    fun basicNoteMapsTypeAndUnits() {
        val json = te().toJson(isAdd = true, dateUtil = dateUtil)
        assertThat(json.getString("eventType")).isEqualTo(TE.Type.NOTE.text)
        assertThat(json.getString("units")).isEqualTo(GlucoseUnit.MGDL.asText)
        assertThat(json.has("duration")).isFalse()
    }

    @Test
    fun announcementSetsFlag() {
        val json = te(type = TE.Type.ANNOUNCEMENT).toJson(isAdd = true, dateUtil = dateUtil)
        assertThat(json.getBoolean("isAnnouncement")).isTrue()
    }

    @Test
    fun optionalFieldsIncludedWhenPresent() {
        val json = te(
            duration = 600_000L, note = "hi", glucose = 100.0,
            glucoseType = TE.MeterType.FINGER, location = TE.Location.FRONT_RIGHT_UPPER_CHEST,
            arrow = TE.Arrow.UP, ids = IDs(nightscoutId = "N")
        ).toJson(isAdd = true, dateUtil = dateUtil)
        assertThat(json.getLong("durationInMilliseconds")).isEqualTo(600_000L)
        assertThat(json.getString("notes")).isEqualTo("hi")
        assertThat(json.getDouble("glucose")).isEqualTo(100.0)
        assertThat(json.getString("glucoseType")).isEqualTo(TE.MeterType.FINGER.text)
        assertThat(json.getString("location")).isEqualTo(TE.Location.FRONT_RIGHT_UPPER_CHEST.text)
        assertThat(json.getString("arrow")).isEqualTo(TE.Arrow.UP.text)
        assertThat(json.getString("_id")).isEqualTo("N")
    }
}
