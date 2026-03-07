package app.aaps.plugins.source.notificationreader

import app.aaps.core.data.model.SourceSensor
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class PackageConfigTest {

    private val testJson = """
        {
          "version": 1,
          "packages": [
            { "package": "com.dexcom.g7", "sensor": "AAPS-DexcomG7" },
            { "package": "com.dexcom.g6", "sensor": "AAPS-DexcomG6" },
            { "package": "com.medtronic.diabetes.guardian", "sensor": "MM600Series" },
            { "package": "com.senseonics.gen12androidapp", "sensor": "Eversense" },
            { "package": "com.unknown.future.app", "sensor": "SomeUnknownSensor" }
          ]
        }
    """.trimIndent()

    @Test
    fun `parse JSON creates correct supported packages`() {
        val config = PackageConfig.fromJson(testJson)
        assertThat(config.supportedPackages).containsExactly(
            "com.dexcom.g7",
            "com.dexcom.g6",
            "com.medtronic.diabetes.guardian",
            "com.senseonics.gen12androidapp",
            "com.unknown.future.app"
        )
    }

    @Test
    fun `parse JSON maps known sensors correctly`() {
        val config = PackageConfig.fromJson(testJson)
        assertThat(config.sensorForPackage("com.dexcom.g7")).isEqualTo(SourceSensor.DEXCOM_G7_NATIVE)
        assertThat(config.sensorForPackage("com.dexcom.g6")).isEqualTo(SourceSensor.DEXCOM_G6_NATIVE)
        assertThat(config.sensorForPackage("com.medtronic.diabetes.guardian")).isEqualTo(SourceSensor.MM_600_SERIES)
        assertThat(config.sensorForPackage("com.senseonics.gen12androidapp")).isEqualTo(SourceSensor.EVERSENSE)
    }

    @Test
    fun `unknown sensor text maps to UNKNOWN`() {
        val config = PackageConfig.fromJson(testJson)
        assertThat(config.sensorForPackage("com.unknown.future.app")).isEqualTo(SourceSensor.UNKNOWN)
    }

    @Test
    fun `unregistered package returns UNKNOWN`() {
        val config = PackageConfig.fromJson(testJson)
        assertThat(config.sensorForPackage("com.not.in.config")).isEqualTo(SourceSensor.UNKNOWN)
    }

    @Test
    fun `isSupportedPackage returns true for known`() {
        val config = PackageConfig.fromJson(testJson)
        assertThat(config.isSupportedPackage("com.dexcom.g7")).isTrue()
    }

    @Test
    fun `isSupportedPackage returns false for unknown`() {
        val config = PackageConfig.fromJson(testJson)
        assertThat(config.isSupportedPackage("com.not.in.config")).isFalse()
    }

    @Test
    fun `empty packages array`() {
        val json = """{ "version": 1, "packages": [] }"""
        val config = PackageConfig.fromJson(json)
        assertThat(config.supportedPackages).isEmpty()
        assertThat(config.sensorForPackage("com.dexcom.g7")).isEqualTo(SourceSensor.UNKNOWN)
    }
}
