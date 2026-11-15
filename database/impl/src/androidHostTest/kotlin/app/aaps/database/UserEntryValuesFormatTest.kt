package app.aaps.database

import app.aaps.database.entities.RunningMode
import app.aaps.database.entities.TemporaryTarget
import app.aaps.database.entities.TherapyEvent
import app.aaps.database.entities.ValueWithUnit
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Pins the JSON that `userEntry.values` is stored as.
 *
 * The column holds whatever `Converters.fromListOfValueWithUnit` produced when the row was written,
 * and rows live for years. The shape below is not a design choice anybody made - it falls out of Gson
 * reflecting over `ValueWithUnitWrapper` and `SealedClassTypeAdapter`, which writes the **Kotlin simple
 * name** of the subtype as the key. Two things therefore silently break every existing row:
 *
 *  - renaming a `ValueWithUnit` subtype, and
 *  - renaming the `wrapped` property of `Converters.ValueWithUnitWrapper` or a subtype's `value`.
 *
 * Neither is a compile error and neither shows up in a round-trip test, because a round trip writes and
 * reads with the same code. Only a literal like the ones here notices. `SealedClassTypeAdapter` turns an
 * unreadable entry into `UNKNOWN` rather than throwing, so the damage would be silent at runtime too:
 * old user entries would quietly lose their values.
 *
 * This also states the exact target for any replacement of Gson here - which is what the module needs
 * before `Converters` and `AppDatabase` can follow the entities into commonMain.
 */
internal class UserEntryValuesFormatTest {

    private val converters = Converters()

    private fun json(value: ValueWithUnit) = converters.fromListOfValueWithUnit(listOf(value))

    @Test
    fun `every subtype serialises to its pinned shape`() {
        assertThat(json(ValueWithUnit.UNKNOWN)).isEqualTo("""[{"wrapped":{"UNKNOWN":{}}}]""")
        assertThat(json(ValueWithUnit.SimpleString("text"))).isEqualTo("""[{"wrapped":{"SimpleString":{"value":"text"}}}]""")
        assertThat(json(ValueWithUnit.SimpleInt(7))).isEqualTo("""[{"wrapped":{"SimpleInt":{"value":7}}}]""")
        assertThat(json(ValueWithUnit.Mgdl(5.5))).isEqualTo("""[{"wrapped":{"Mgdl":{"value":5.5}}}]""")
        assertThat(json(ValueWithUnit.Mmoll(6.5))).isEqualTo("""[{"wrapped":{"Mmoll":{"value":6.5}}}]""")
        assertThat(json(ValueWithUnit.Timestamp(1_700_000_000_000L))).isEqualTo("""[{"wrapped":{"Timestamp":{"value":1700000000000}}}]""")
        assertThat(json(ValueWithUnit.Insulin(1.25))).isEqualTo("""[{"wrapped":{"Insulin":{"value":1.25}}}]""")
        assertThat(json(ValueWithUnit.InsulinConcentration(200))).isEqualTo("""[{"wrapped":{"InsulinConcentration":{"value":200}}}]""")
        assertThat(json(ValueWithUnit.UnitPerHour(0.75))).isEqualTo("""[{"wrapped":{"UnitPerHour":{"value":0.75}}}]""")
        assertThat(json(ValueWithUnit.Gram(30))).isEqualTo("""[{"wrapped":{"Gram":{"value":30}}}]""")
        assertThat(json(ValueWithUnit.Minute(45))).isEqualTo("""[{"wrapped":{"Minute":{"value":45}}}]""")
        assertThat(json(ValueWithUnit.Hour(3))).isEqualTo("""[{"wrapped":{"Hour":{"value":3}}}]""")
        assertThat(json(ValueWithUnit.Percent(120))).isEqualTo("""[{"wrapped":{"Percent":{"value":120}}}]""")
        // Enums travel by name, so renaming a constant is the same hazard as renaming a subtype.
        assertThat(json(ValueWithUnit.TherapyEventType(TherapyEvent.Type.BOLUS_WIZARD)))
            .isEqualTo("""[{"wrapped":{"TherapyEventType":{"value":"BOLUS_WIZARD"}}}]""")
        assertThat(json(ValueWithUnit.TherapyEventMeterType(TherapyEvent.MeterType.FINGER)))
            .isEqualTo("""[{"wrapped":{"TherapyEventMeterType":{"value":"FINGER"}}}]""")
        assertThat(json(ValueWithUnit.TherapyEventArrow(TherapyEvent.Arrow.RIGHT)))
            .isEqualTo("""[{"wrapped":{"TherapyEventArrow":{"value":"RIGHT"}}}]""")
        assertThat(json(ValueWithUnit.TherapyEventLocation(TherapyEvent.Location.SIDE_LEFT_UPPER_ARM)))
            .isEqualTo("""[{"wrapped":{"TherapyEventLocation":{"value":"SIDE_LEFT_UPPER_ARM"}}}]""")
        assertThat(json(ValueWithUnit.TherapyEventTTReason(TemporaryTarget.Reason.ACTIVITY)))
            .isEqualTo("""[{"wrapped":{"TherapyEventTTReason":{"value":"ACTIVITY"}}}]""")
        assertThat(json(ValueWithUnit.RunningModeMode(RunningMode.Mode.OPEN_LOOP)))
            .isEqualTo("""[{"wrapped":{"RunningModeMode":{"value":"OPEN_LOOP"}}}]""")
    }

    @Test
    fun `a stored list reads back as it was written`() {
        val values = listOf(
            ValueWithUnit.Insulin(1.25),
            ValueWithUnit.Gram(30),
            ValueWithUnit.TherapyEventType(TherapyEvent.Type.BOLUS_WIZARD),
            ValueWithUnit.UNKNOWN
        )
        assertThat(converters.toMutableListOfValueWithUnit(converters.fromListOfValueWithUnit(values)))
            .isEqualTo(values)
    }

    @Test
    fun `a subtype the running build does not know becomes UNKNOWN rather than throwing`() {
        // What protects a downgrade, and the reason a rename is silent rather than loud.
        assertThat(converters.toMutableListOfValueWithUnit("""[{"wrapped":{"SomethingFromTheFuture":{"value":1}}}]"""))
            .isEqualTo(listOf(ValueWithUnit.UNKNOWN))
    }
}
