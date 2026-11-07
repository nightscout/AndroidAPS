package app.aaps.database

import app.aaps.database.di.MIGRATION_35_TO_36_PAIRS
import app.aaps.database.entities.GlucoseValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The `sourceSensor` column holds the entry name. Entries that were merged into one no longer exist
 * under their old name, so reading an old row depends on [LEGACY_SOURCE_SENSOR_NAMES]. Without it
 * those rows would come back as `UNKNOWN`, losing the sensor and also pulling old readings into the
 * flat check.
 *
 * In `commonTest`, so this runs through Kotlin/Native as well as the JVM.
 */
class ConvertersSourceSensorTest {

    private val converters = Converters()

    @Test
    fun `current names read back unchanged`() {
        GlucoseValue.SourceSensor.entries.forEach { entry ->
            assertEquals(entry, converters.toSourceSensor(entry.name), entry.name)
        }
    }

    @Test
    fun `names written before the dexcom entries were merged keep their sensor`() {
        assertEquals(GlucoseValue.SourceSensor.DEXCOM_UNKNOWN, converters.toSourceSensor("DEXCOM_NATIVE_UNKNOWN"))
        assertEquals(GlucoseValue.SourceSensor.DEXCOM_G6, converters.toSourceSensor("DEXCOM_G6_NATIVE"))
        assertEquals(GlucoseValue.SourceSensor.DEXCOM_G6, converters.toSourceSensor("DEXCOM_G6_NATIVE_XDRIP"))
        assertEquals(GlucoseValue.SourceSensor.DEXCOM_G7, converters.toSourceSensor("DEXCOM_G7_NATIVE"))
        assertEquals(GlucoseValue.SourceSensor.DEXCOM_G7, converters.toSourceSensor("DEXCOM_G7_NATIVE_XDRIP"))
        assertEquals(GlucoseValue.SourceSensor.DEXCOM_G7, converters.toSourceSensor("DEXCOM_G7_XDRIP"))
    }

    @Test
    fun `every libre 1 bridge name keeps the libre 1 sensor`() {
        listOf(
            "LIBRE_1_OTHER", "LIBRE_1_NET", "LIBRE_1_BLUE", "LIBRE_1_PL", "LIBRE_1_BLUCON", "LIBRE_1_TOMATO",
            "LIBRE_1_RF", "LIBRE_1_LIMITTER", "LIBRE_1_BUBBLE", "LIBRE_1_ATOM", "LIBRE_1_GLIMP"
        ).forEach { assertEquals(GlucoseValue.SourceSensor.LIBRE_1, converters.toSourceSensor(it), it) }
    }

    @Test
    fun `remaining merged names keep their sensor`() {
        assertEquals(GlucoseValue.SourceSensor.LIBRE_2, converters.toSourceSensor("LIBRE_2_NATIVE"))
        assertEquals(GlucoseValue.SourceSensor.SIBIONIC_UNKNOWN, converters.toSourceSensor("SIBIONIC"))
        assertEquals(GlucoseValue.SourceSensor.SINOCARE, converters.toSourceSensor("SINO"))
    }

    @Test
    fun `no legacy name is also a current name`() {
        val current = GlucoseValue.SourceSensor.entries.map { it.name }.toSet()
        LEGACY_SOURCE_SENSOR_NAMES.keys.forEach { assertTrue(it !in current, it) }
    }

    /**
     * `migration35to36` rewrites the column so that the queries comparing it as text keep matching.
     * It spells its pairs out, because a migration has to keep doing the same thing forever, so this
     * checks the two agree today rather than letting them drift apart unnoticed.
     */
    @Test
    fun `the migration rewrites every legacy name this map knows`() {
        val migrated = MIGRATION_35_TO_36_PAIRS
        assertEquals(LEGACY_SOURCE_SENSOR_NAMES.size, migrated.size)
        LEGACY_SOURCE_SENSOR_NAMES.forEach { (legacy, entry) ->
            assertEquals(entry.name, migrated[legacy], legacy)
        }
    }

    @Test
    fun `an unknown name does not throw`() {
        assertEquals(GlucoseValue.SourceSensor.UNKNOWN, converters.toSourceSensor("SOMETHING_REMOVED_LATER"))
        assertNull(converters.toSourceSensor(null))
    }
}
