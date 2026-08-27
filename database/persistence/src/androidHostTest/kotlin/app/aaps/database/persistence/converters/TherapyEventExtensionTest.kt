package app.aaps.database.persistence.converters

import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.IDs
import app.aaps.core.data.model.TE
import app.aaps.database.entities.TherapyEvent
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

internal class TherapyEventExtensionTest {

    private fun template(): TE = TE(
        id = 1L,
        version = 2,
        dateCreated = 3L,
        isValid = false,                       // non-default (default = true)
        referenceId = 4L,
        ids = IDs(
            nightscoutSystemId = "sys-1",
            nightscoutId = "ns-123",
            pumpType = null,                   // keep null to avoid PumpType coupling
            pumpSerial = "serial-9",
            temporaryId = 66L,
            pumpId = 55L,
            startId = 77L,
            endId = 88L
        ),
        timestamp = 1_000L,
        utcOffset = 3_600_000L,
        duration = 5_000L,
        type = TE.Type.BOLUS_WIZARD,
        note = "test note",
        enteredBy = "tester",
        glucose = 123.4,
        glucoseType = TE.MeterType.SENSOR,
        glucoseUnit = GlucoseUnit.MMOL,
        location = TE.Location.SIDE_LEFT_UPPER_ARM,
        arrow = TE.Arrow.UP_RIGHT
    )

    @Test
    fun `TE round trips through toDb and fromDb preserving all fields`() {
        val original = template()

        val back = original.toDb().fromDb()

        assertThat(back.id).isEqualTo(1L)
        assertThat(back.version).isEqualTo(2)
        assertThat(back.dateCreated).isEqualTo(3L)
        assertThat(back.isValid).isFalse()
        assertThat(back.referenceId).isEqualTo(4L)
        assertThat(back.timestamp).isEqualTo(1_000L)
        assertThat(back.utcOffset).isEqualTo(3_600_000L)
        assertThat(back.duration).isEqualTo(5_000L)
        assertThat(back.type).isEqualTo(TE.Type.BOLUS_WIZARD)
        assertThat(back.note).isEqualTo("test note")
        assertThat(back.enteredBy).isEqualTo("tester")
        assertThat(back.glucose).isEqualTo(123.4)
        assertThat(back.glucoseType).isEqualTo(TE.MeterType.SENSOR)
        assertThat(back.glucoseUnit).isEqualTo(GlucoseUnit.MMOL)
        assertThat(back.location).isEqualTo(TE.Location.SIDE_LEFT_UPPER_ARM)
        assertThat(back.arrow).isEqualTo(TE.Arrow.UP_RIGHT)

        // nested IDs preserved
        assertThat(back.ids.nightscoutSystemId).isEqualTo("sys-1")
        assertThat(back.ids.nightscoutId).isEqualTo("ns-123")
        assertThat(back.ids.pumpSerial).isEqualTo("serial-9")
        assertThat(back.ids.temporaryId).isEqualTo(66L)
        assertThat(back.ids.pumpId).isEqualTo(55L)
        assertThat(back.ids.startId).isEqualTo(77L)
        assertThat(back.ids.endId).isEqualTo(88L)

        // mapping is lossless -> full value equality holds
        assertThat(back).isEqualTo(original)
    }

    @Test
    fun `TE round trips with nullable fields left null`() {
        val original = template().copy(
            note = null,
            enteredBy = null,
            glucose = null,
            glucoseType = null,
            location = null,
            arrow = null
        )

        val back = original.toDb().fromDb()

        assertThat(back.note).isNull()
        assertThat(back.enteredBy).isNull()
        assertThat(back.glucose).isNull()
        assertThat(back.glucoseType).isNull()
        assertThat(back.location).isNull()
        assertThat(back.arrow).isNull()
        assertThat(back).isEqualTo(original)
    }

    @Test
    fun `Type enum maps both directions for every value`() {
        TE.Type.entries.forEach { assertThat(it.toDb().fromDb()).isEqualTo(it) }
        TherapyEvent.Type.entries.forEach { assertThat(it.fromDb().toDb()).isEqualTo(it) }
        assertThat(TE.Type.entries).hasSize(TherapyEvent.Type.entries.size)
        // representative explicit mapping
        assertThat(TE.Type.CANNULA_CHANGE.toDb()).isEqualTo(TherapyEvent.Type.CANNULA_CHANGE)
        assertThat(TherapyEvent.Type.NONE.fromDb()).isEqualTo(TE.Type.NONE)
    }

    @Test
    fun `MeterType enum maps both directions for every value`() {
        TE.MeterType.entries.forEach { assertThat(it.toDb().fromDb()).isEqualTo(it) }
        TherapyEvent.MeterType.entries.forEach { assertThat(it.fromDb().toDb()).isEqualTo(it) }
        assertThat(TE.MeterType.entries).hasSize(TherapyEvent.MeterType.entries.size)
        assertThat(TE.MeterType.FINGER.toDb()).isEqualTo(TherapyEvent.MeterType.FINGER)
    }

    @Test
    fun `Location enum maps both directions for every value`() {
        TE.Location.entries.forEach { assertThat(it.toDb().fromDb()).isEqualTo(it) }
        TherapyEvent.Location.entries.forEach { assertThat(it.fromDb().toDb()).isEqualTo(it) }
        assertThat(TE.Location.entries).hasSize(TherapyEvent.Location.entries.size)
        assertThat(TE.Location.NONE.toDb()).isEqualTo(TherapyEvent.Location.NONE)
    }

    @Test
    fun `Arrow enum maps both directions for every value`() {
        TE.Arrow.entries.forEach { assertThat(it.toDb().fromDb()).isEqualTo(it) }
        TherapyEvent.Arrow.entries.forEach { assertThat(it.fromDb().toDb()).isEqualTo(it) }
        assertThat(TE.Arrow.entries).hasSize(TherapyEvent.Arrow.entries.size)
        assertThat(TE.Arrow.CENTER.toDb()).isEqualTo(TherapyEvent.Arrow.CENTER)
    }
}
