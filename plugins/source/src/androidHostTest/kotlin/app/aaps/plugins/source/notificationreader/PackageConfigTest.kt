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

    // ----- intervalMinutes parsing -----

    @Test
    fun `intervalMinutes missing falls back to default`() {
        val config = PackageConfig.fromJson(testJson)
        assertThat(config.intervalForPackage("com.dexcom.g7", default = 999L)).isEqualTo(999L)
    }

    @Test
    fun `intervalMinutes valid values are parsed to milliseconds`() {
        val json = """
            {
              "version": 2,
              "packages": [
                { "package": "p.one", "sensor": "Unknown", "intervalMinutes": 1 },
                { "package": "p.three", "sensor": "Unknown", "intervalMinutes": 3 },
                { "package": "p.five", "sensor": "Unknown", "intervalMinutes": 5 },
                { "package": "p.fifteen", "sensor": "Unknown", "intervalMinutes": 15 }
              ]
            }
        """.trimIndent()
        val config = PackageConfig.fromJson(json)
        assertThat(config.intervalForPackage("p.one", default = 0L)).isEqualTo(60_000L)
        assertThat(config.intervalForPackage("p.three", default = 0L)).isEqualTo(180_000L)
        assertThat(config.intervalForPackage("p.five", default = 0L)).isEqualTo(300_000L)
        assertThat(config.intervalForPackage("p.fifteen", default = 0L)).isEqualTo(900_000L)
    }

    @Test
    fun `intervalMinutes zero or negative falls back to default`() {
        val json = """
            {
              "version": 2,
              "packages": [
                { "package": "p.zero", "sensor": "Unknown", "intervalMinutes": 0 },
                { "package": "p.neg", "sensor": "Unknown", "intervalMinutes": -5 }
              ]
            }
        """.trimIndent()
        val config = PackageConfig.fromJson(json)
        assertThat(config.intervalForPackage("p.zero", default = 777L)).isEqualTo(777L)
        assertThat(config.intervalForPackage("p.neg", default = 777L)).isEqualTo(777L)
    }

    @Test
    fun `intervalMinutes unknown value snaps to nearest known`() {
        val json = """
            {
              "version": 2,
              "packages": [
                { "package": "p.two", "sensor": "Unknown", "intervalMinutes": 2 },
                { "package": "p.seven", "sensor": "Unknown", "intervalMinutes": 7 },
                { "package": "p.twenty", "sensor": "Unknown", "intervalMinutes": 20 }
              ]
            }
        """.trimIndent()
        val config = PackageConfig.fromJson(json)
        // 2 is equidistant 1 and 3; minBy picks first → 1
        assertThat(config.intervalForPackage("p.two", default = 0L)).isEqualTo(60_000L)
        // 7 closer to 5 than to 15
        assertThat(config.intervalForPackage("p.seven", default = 0L)).isEqualTo(300_000L)
        // 20 closer to 15
        assertThat(config.intervalForPackage("p.twenty", default = 0L)).isEqualTo(900_000L)
    }

    @Test
    fun `intervalMinutes wrong type does not crash and falls back`() {
        val json = """
            {
              "version": 2,
              "packages": [
                { "package": "p.str", "sensor": "Unknown", "intervalMinutes": "five" }
              ]
            }
        """.trimIndent()
        val config = PackageConfig.fromJson(json)
        assertThat(config.intervalForPackage("p.str", default = 555L)).isEqualTo(555L)
    }

    @Test
    fun `old client schema parsing still works for new schema`() {
        // Sanity: old code path (sensor + package) ignores extra intervalMinutes — verified by
        // round-tripping through fromJson which only reads what it needs.
        val json = """
            {
              "version": 2,
              "packages": [
                { "package": "com.dexcom.g7", "sensor": "AAPS-DexcomG7", "intervalMinutes": 5 }
              ]
            }
        """.trimIndent()
        val config = PackageConfig.fromJson(json)
        assertThat(config.isSupportedPackage("com.dexcom.g7")).isTrue()
        assertThat(config.sensorForPackage("com.dexcom.g7")).isEqualTo(SourceSensor.DEXCOM_G7_NATIVE)
    }
}
