package app.aaps.plugins.source.notificationreader

import app.aaps.core.data.model.SourceSensor
import app.aaps.plugins.source.cgmapps.CgmApp
import app.aaps.plugins.source.cgmapps.CgmApps
import app.aaps.plugins.source.cgmapps.NotificationReading
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class PackageConfigTest {

    private fun app(id: String, sensor: String?, vararg packages: String, intervalMinutes: Int = 0) =
        CgmApp(id, id, packages.toList(), sensor?.let { NotificationReading(it, intervalMinutes) })

    private val catalog = CgmApps(
        version = 1,
        apps = listOf(
            app("dexcom-g7", "AAPS-DexcomG7", "com.dexcom.g7"),
            app("dexcom-g6", "AAPS-DexcomG6", "com.dexcom.g6", "com.dexcom.g6.region1.mmol"),
            app("guardian", "MM600Series", "com.medtronic.diabetes.guardian"),
            app("eversense", "Eversense", "com.senseonics.gen12androidapp"),
            app("future", "SomeUnknownSensor", "com.unknown.future.app"),
            // An app the reader does not read: its readings come by broadcast
            app("xdrip", null, "com.eveningoutpost.dexdrip")
        )
    )

    @Test
    fun `every package of an app with notification readings is supported, and no other`() {
        val config = PackageConfig.fromCatalog(catalog)
        assertThat(config.supportedPackages).containsExactly(
            "com.dexcom.g7",
            "com.dexcom.g6",
            "com.dexcom.g6.region1.mmol",
            "com.medtronic.diabetes.guardian",
            "com.senseonics.gen12androidapp",
            "com.unknown.future.app"
        )
    }

    @Test
    fun `known sensors are mapped, for every package of the app`() {
        val config = PackageConfig.fromCatalog(catalog)
        assertThat(config.sensorForPackage("com.dexcom.g7")).isEqualTo(SourceSensor.DEXCOM_G7)
        assertThat(config.sensorForPackage("com.dexcom.g6")).isEqualTo(SourceSensor.DEXCOM_G6)
        assertThat(config.sensorForPackage("com.dexcom.g6.region1.mmol")).isEqualTo(SourceSensor.DEXCOM_G6)
        assertThat(config.sensorForPackage("com.medtronic.diabetes.guardian")).isEqualTo(SourceSensor.MM_600_SERIES)
        assertThat(config.sensorForPackage("com.senseonics.gen12androidapp")).isEqualTo(SourceSensor.EVERSENSE)
    }

    @Test
    fun `unknown sensor text maps to UNKNOWN`() {
        assertThat(PackageConfig.fromCatalog(catalog).sensorForPackage("com.unknown.future.app")).isEqualTo(SourceSensor.UNKNOWN)
    }

    @Test
    fun `unregistered package returns UNKNOWN and is not supported`() {
        val config = PackageConfig.fromCatalog(catalog)
        assertThat(config.sensorForPackage("com.not.in.config")).isEqualTo(SourceSensor.UNKNOWN)
        assertThat(config.isSupportedPackage("com.not.in.config")).isFalse()
    }

    @Test
    fun `empty catalog`() {
        val config = PackageConfig.fromCatalog(CgmApps())
        assertThat(config.supportedPackages).isEmpty()
        assertThat(config.sensorForPackage("com.dexcom.g7")).isEqualTo(SourceSensor.UNKNOWN)
    }

    // ----- intervalMinutes -----

    @Test
    fun `intervalMinutes missing falls back to default`() {
        assertThat(PackageConfig.fromCatalog(catalog).intervalForPackage("com.dexcom.g7", default = 999L)).isEqualTo(999L)
    }

    @Test
    fun `intervalMinutes valid values are converted to milliseconds`() {
        val config = PackageConfig.fromCatalog(
            CgmApps(
                apps = listOf(
                    app("one", "Unknown", "p.one", intervalMinutes = 1),
                    app("three", "Unknown", "p.three", intervalMinutes = 3),
                    app("five", "Unknown", "p.five", intervalMinutes = 5),
                    app("fifteen", "Unknown", "p.fifteen", intervalMinutes = 15)
                )
            )
        )
        assertThat(config.intervalForPackage("p.one", default = 0L)).isEqualTo(60_000L)
        assertThat(config.intervalForPackage("p.three", default = 0L)).isEqualTo(180_000L)
        assertThat(config.intervalForPackage("p.five", default = 0L)).isEqualTo(300_000L)
        assertThat(config.intervalForPackage("p.fifteen", default = 0L)).isEqualTo(900_000L)
    }

    @Test
    fun `intervalMinutes zero or negative falls back to default`() {
        val config = PackageConfig.fromCatalog(
            CgmApps(apps = listOf(app("zero", "Unknown", "p.zero", intervalMinutes = 0), app("neg", "Unknown", "p.neg", intervalMinutes = -5)))
        )
        assertThat(config.intervalForPackage("p.zero", default = 777L)).isEqualTo(777L)
        assertThat(config.intervalForPackage("p.neg", default = 777L)).isEqualTo(777L)
    }

    @Test
    fun `intervalMinutes unknown value snaps to nearest known`() {
        val config = PackageConfig.fromCatalog(
            CgmApps(
                apps = listOf(
                    app("two", "Unknown", "p.two", intervalMinutes = 2),
                    app("seven", "Unknown", "p.seven", intervalMinutes = 7),
                    app("twenty", "Unknown", "p.twenty", intervalMinutes = 20)
                )
            )
        )
        // 2 is equidistant 1 and 3; minBy picks first → 1
        assertThat(config.intervalForPackage("p.two", default = 0L)).isEqualTo(60_000L)
        // 7 closer to 5 than to 15
        assertThat(config.intervalForPackage("p.seven", default = 0L)).isEqualTo(300_000L)
        // 20 closer to 15
        assertThat(config.intervalForPackage("p.twenty", default = 0L)).isEqualTo(900_000L)
    }
}
