package app.aaps.database.persistence.converters

import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.IDs
import app.aaps.core.data.model.TE
import app.aaps.database.entities.TherapyEvent
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.Test

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

        assertEquals(1L, back.id)
        assertEquals(2, back.version)
        assertEquals(3L, back.dateCreated)
        assertFalse(back.isValid)
        assertEquals(4L, back.referenceId)
        assertEquals(1_000L, back.timestamp)
        assertEquals(3_600_000L, back.utcOffset)
        assertEquals(5_000L, back.duration)
        assertEquals(TE.Type.BOLUS_WIZARD, back.type)
        assertEquals("test note", back.note)
        assertEquals("tester", back.enteredBy)
        assertEquals(123.4, back.glucose)
        assertEquals(TE.MeterType.SENSOR, back.glucoseType)
        assertEquals(GlucoseUnit.MMOL, back.glucoseUnit)
        assertEquals(TE.Location.SIDE_LEFT_UPPER_ARM, back.location)
        assertEquals(TE.Arrow.UP_RIGHT, back.arrow)

        // nested IDs preserved
        assertEquals("sys-1", back.ids.nightscoutSystemId)
        assertEquals("ns-123", back.ids.nightscoutId)
        assertEquals("serial-9", back.ids.pumpSerial)
        assertEquals(66L, back.ids.temporaryId)
        assertEquals(55L, back.ids.pumpId)
        assertEquals(77L, back.ids.startId)
        assertEquals(88L, back.ids.endId)

        // mapping is lossless -> full value equality holds
        assertEquals(original, back)
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

        assertNull(back.note)
        assertNull(back.enteredBy)
        assertNull(back.glucose)
        assertNull(back.glucoseType)
        assertNull(back.location)
        assertNull(back.arrow)
        assertEquals(original, back)
    }

    @Test
    fun `Type enum maps both directions for every value`() {
        TE.Type.entries.forEach { assertEquals(it, it.toDb().fromDb()) }
        TherapyEvent.Type.entries.forEach { assertEquals(it, it.fromDb().toDb()) }
        assertEquals(TherapyEvent.Type.entries.size, (TE.Type.entries).size)
        // representative explicit mapping
        assertEquals(TherapyEvent.Type.CANNULA_CHANGE, TE.Type.CANNULA_CHANGE.toDb())
        assertEquals(TE.Type.NONE, TherapyEvent.Type.NONE.fromDb())
    }

    @Test
    fun `MeterType enum maps both directions for every value`() {
        TE.MeterType.entries.forEach { assertEquals(it, it.toDb().fromDb()) }
        TherapyEvent.MeterType.entries.forEach { assertEquals(it, it.fromDb().toDb()) }
        assertEquals(TherapyEvent.MeterType.entries.size, (TE.MeterType.entries).size)
        assertEquals(TherapyEvent.MeterType.FINGER, TE.MeterType.FINGER.toDb())
    }

    @Test
    fun `Location enum maps both directions for every value`() {
        TE.Location.entries.forEach { assertEquals(it, it.toDb().fromDb()) }
        TherapyEvent.Location.entries.forEach { assertEquals(it, it.fromDb().toDb()) }
        assertEquals(TherapyEvent.Location.entries.size, (TE.Location.entries).size)
        assertEquals(TherapyEvent.Location.NONE, TE.Location.NONE.toDb())
    }

    @Test
    fun `Arrow enum maps both directions for every value`() {
        TE.Arrow.entries.forEach { assertEquals(it, it.toDb().fromDb()) }
        TherapyEvent.Arrow.entries.forEach { assertEquals(it, it.fromDb().toDb()) }
        assertEquals(TherapyEvent.Arrow.entries.size, (TE.Arrow.entries).size)
        assertEquals(TherapyEvent.Arrow.CENTER, TE.Arrow.CENTER.toDb())
    }
}
