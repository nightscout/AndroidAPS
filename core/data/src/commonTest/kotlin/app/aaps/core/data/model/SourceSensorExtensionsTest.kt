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
        assertTrue(SourceSensor.DEXCOM_UNKNOWN.advancedFilteringSupported())
        assertTrue(SourceSensor.DEXCOM_G6.advancedFilteringSupported())
        assertTrue(SourceSensor.DEXCOM_G7.advancedFilteringSupported())
        assertTrue(SourceSensor.DEXCOM_G6.advancedFilteringSupported())
        assertTrue(SourceSensor.DEXCOM_G7.advancedFilteringSupported())
        assertTrue(SourceSensor.DEXCOM_G7.advancedFilteringSupported())
    }

    @Test
    fun `libre 2 and 3 support advanced filtering`() {
        assertTrue(SourceSensor.LIBRE_2.advancedFilteringSupported())
        assertTrue(SourceSensor.LIBRE_2.advancedFilteringSupported())
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
        assertFalse(SourceSensor.LIBRE_1.advancedFilteringSupported())
        assertFalse(SourceSensor.LIBRE_1.advancedFilteringSupported())
        assertFalse(SourceSensor.LIBRE_1.advancedFilteringSupported())
    }

    @Test
    fun `unknown does not support advanced filtering`() {
        assertFalse(SourceSensor.UNKNOWN.advancedFilteringSupported())
    }

    @Test
    fun `dexcom share follow supports advanced filtering but medtronic carelink does not`() {
        assertTrue(SourceSensor.DEXCOM_UNKNOWN.advancedFilteringSupported())
        assertFalse(SourceSensor.MM_UNKNOWN.advancedFilteringSupported())
    }

    @Test
    fun `new xdrip device sources do not support advanced filtering`() {
        assertFalse(SourceSensor.MEDTRUM_UNKNOWN.advancedFilteringSupported())
        assertFalse(SourceSensor.GLUPRO.advancedFilteringSupported())
    }

    @Test
    fun `libre 1 and unknown need the flat check`() {
        assertTrue(SourceSensor.LIBRE_1.needsFlatBgCheck())
        assertTrue(SourceSensor.LIBRE_1.needsFlatBgCheck())
        assertTrue(SourceSensor.UNKNOWN.needsFlatBgCheck())
    }

    @Test
    fun `carelink follow needs the flat check because it comes over a cloud link`() {
        assertTrue(SourceSensor.MM_UNKNOWN.needsFlatBgCheck())
    }

    @Test
    fun `mq needs the flat check but the other juggluco ng sensors do not`() {
        // Juggluco sends the same name for a direct MQ sensor and for one relayed by the MQ cloud.
        assertTrue(SourceSensor.GLUTEC.needsFlatBgCheck())
        assertFalse(SourceSensor.SINOCARE.needsFlatBgCheck())
        assertFalse(SourceSensor.ANYTIME.needsFlatBgCheck())
    }

    @Test
    fun `juggluco ng sensors get no advanced filtering`() {
        assertFalse(SourceSensor.SINOCARE.advancedFilteringSupported())
        assertFalse(SourceSensor.ANYTIME.advancedFilteringSupported())
        assertFalse(SourceSensor.GLUTEC.advancedFilteringSupported())
    }

    @Test
    fun `both aidex generations need the flat check`() {
        assertTrue(SourceSensor.AIDEX.needsFlatBgCheck())
        assertTrue(SourceSensor.AIDEX_X.needsFlatBgCheck())
    }

    @Test
    fun `juggluco sensors get no advanced filtering except libre and dexcom`() {
        assertFalse(SourceSensor.SIBIONIC_GS1.advancedFilteringSupported())
        assertFalse(SourceSensor.SIBIONIC_GS3.advancedFilteringSupported())
        assertFalse(SourceSensor.ACCU_CHEK.advancedFilteringSupported())
        assertFalse(SourceSensor.CARESENS_AIR.advancedFilteringSupported())
        assertFalse(SourceSensor.AIDEX_X.advancedFilteringSupported())
    }

    @Test
    fun `named native sources do not need the flat check`() {
        assertFalse(SourceSensor.DEXCOM_G6.needsFlatBgCheck())
        assertFalse(SourceSensor.DEXCOM_UNKNOWN.needsFlatBgCheck())
        assertFalse(SourceSensor.LIBRE_2.needsFlatBgCheck())
        assertFalse(SourceSensor.MEDTRUM_UNKNOWN.needsFlatBgCheck())
        assertFalse(SourceSensor.GLUPRO.needsFlatBgCheck())
        assertFalse(SourceSensor.SIBIONIC_GS1.needsFlatBgCheck())
        assertFalse(SourceSensor.SIBIONIC_GS3.needsFlatBgCheck())
        assertFalse(SourceSensor.ACCU_CHEK.needsFlatBgCheck())
        assertFalse(SourceSensor.CARESENS_AIR.needsFlatBgCheck())
    }
}
