package app.aaps.core.data.model

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * In `commonTest`, so this runs through Kotlin/Native as well as the JVM. `kotlin.test` rather than
 * Truth and JUnit 5, because neither of those exists off the JVM.
 */
class SourceSensorExtensionsTest {

    @Test
    fun `dexcom native sensors support advanced filtering`() {
        assertTrue(SourceSensor.DEXCOM_NATIVE_UNKNOWN.advancedFilteringSupported())
        assertTrue(SourceSensor.DEXCOM_G6_NATIVE.advancedFilteringSupported())
        assertTrue(SourceSensor.DEXCOM_G7_NATIVE.advancedFilteringSupported())
        assertTrue(SourceSensor.DEXCOM_G6_NATIVE_XDRIP.advancedFilteringSupported())
        assertTrue(SourceSensor.DEXCOM_G7_NATIVE_XDRIP.advancedFilteringSupported())
        assertTrue(SourceSensor.DEXCOM_G7_XDRIP.advancedFilteringSupported())
    }

    @Test
    fun `libre 2 and 3 support advanced filtering`() {
        assertTrue(SourceSensor.LIBRE_2.advancedFilteringSupported())
        assertTrue(SourceSensor.LIBRE_2_NATIVE.advancedFilteringSupported())
        assertTrue(SourceSensor.LIBRE_3.advancedFilteringSupported())
    }

    @Test
    fun `syai and random support advanced filtering`() {
        assertTrue(SourceSensor.SYAI_TAG.advancedFilteringSupported())
        assertTrue(SourceSensor.RANDOM.advancedFilteringSupported())
    }

    @Test
    fun `medtronic does not support advanced filtering`() {
        assertFalse(SourceSensor.MM_600_SERIES.advancedFilteringSupported())
        assertFalse(SourceSensor.MM_SIMPLERA.advancedFilteringSupported())
    }

    @Test
    fun `eversense does not support advanced filtering`() {
        assertFalse(SourceSensor.EVERSENSE.advancedFilteringSupported())
    }

    @Test
    fun `libre 1 sensors do not support advanced filtering`() {
        assertFalse(SourceSensor.LIBRE_1_OTHER.advancedFilteringSupported())
        assertFalse(SourceSensor.LIBRE_1_NET.advancedFilteringSupported())
        assertFalse(SourceSensor.LIBRE_1_BUBBLE.advancedFilteringSupported())
    }

    @Test
    fun `unknown does not support advanced filtering`() {
        assertFalse(SourceSensor.UNKNOWN.advancedFilteringSupported())
    }
}
