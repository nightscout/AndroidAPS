package app.aaps.core.data.model

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class SourceSensorExtensionsTest {

    @Test
    fun `dexcom native sensors support advanced filtering`() {
        assertThat(SourceSensor.DEXCOM_NATIVE_UNKNOWN.advancedFilteringSupported()).isTrue()
        assertThat(SourceSensor.DEXCOM_G6_NATIVE.advancedFilteringSupported()).isTrue()
        assertThat(SourceSensor.DEXCOM_G7_NATIVE.advancedFilteringSupported()).isTrue()
        assertThat(SourceSensor.DEXCOM_G6_NATIVE_XDRIP.advancedFilteringSupported()).isTrue()
        assertThat(SourceSensor.DEXCOM_G7_NATIVE_XDRIP.advancedFilteringSupported()).isTrue()
        assertThat(SourceSensor.DEXCOM_G7_XDRIP.advancedFilteringSupported()).isTrue()
    }

    @Test
    fun `libre 2 and 3 support advanced filtering`() {
        assertThat(SourceSensor.LIBRE_2.advancedFilteringSupported()).isTrue()
        assertThat(SourceSensor.LIBRE_2_NATIVE.advancedFilteringSupported()).isTrue()
        assertThat(SourceSensor.LIBRE_3.advancedFilteringSupported()).isTrue()
    }

    @Test
    fun `syai and random support advanced filtering`() {
        assertThat(SourceSensor.SYAI_TAG.advancedFilteringSupported()).isTrue()
        assertThat(SourceSensor.RANDOM.advancedFilteringSupported()).isTrue()
    }

    @Test
    fun `medtronic does not support advanced filtering`() {
        assertThat(SourceSensor.MM_600_SERIES.advancedFilteringSupported()).isFalse()
        assertThat(SourceSensor.MM_SIMPLERA.advancedFilteringSupported()).isFalse()
    }

    @Test
    fun `eversense does not support advanced filtering`() {
        assertThat(SourceSensor.EVERSENSE.advancedFilteringSupported()).isFalse()
    }

    @Test
    fun `libre 1 sensors do not support advanced filtering`() {
        assertThat(SourceSensor.LIBRE_1_OTHER.advancedFilteringSupported()).isFalse()
        assertThat(SourceSensor.LIBRE_1_NET.advancedFilteringSupported()).isFalse()
        assertThat(SourceSensor.LIBRE_1_BUBBLE.advancedFilteringSupported()).isFalse()
    }

    @Test
    fun `unknown does not support advanced filtering`() {
        assertThat(SourceSensor.UNKNOWN.advancedFilteringSupported()).isFalse()
    }
}
