package app.aaps.plugins.source

import app.aaps.core.data.model.SourceSensor
import app.aaps.core.interfaces.source.CalibrationMode
import app.aaps.core.keys.StringNonKey
import app.aaps.plugins.source.cgmapps.Calibration
import app.aaps.plugins.source.cgmapps.CgmApp
import app.aaps.plugins.source.cgmapps.CgmAppCatalog
import app.aaps.plugins.source.cgmapps.CgmApps
import app.aaps.plugins.source.cgmapps.NotificationReading
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class NotificationReaderPluginTest : TestBaseWithProfile() {

    private val catalog: CgmAppCatalog = mock()
    private lateinit var notificationReaderPlugin: NotificationReaderPlugin

    private val eversense = CgmApp(
        id = "eversense", name = "Eversense",
        packages = listOf("com.senseonics.eversense365.us", "com.senseonics.eversense365.ous"),
        notification = NotificationReading(sensor = "Eversense", intervalMinutes = 5)
    )

    @BeforeEach
    fun setup() {
        whenever(catalog.apps).thenReturn(CgmApps(version = 1, apps = listOf(eversense)))
        notificationReaderPlugin = NotificationReaderPlugin(rh, aapsLogger, preferences, config, catalog, mock())
    }

    @Test
    fun `requires the special notification listener permission`() {
        val permissions = notificationReaderPlugin.requiredPermissions()
        assertThat(permissions).isNotEmpty()
        assertThat(permissions.first().special).isTrue()
        assertThat(permissions.flatMap { it.permissions })
            .contains(NotificationReaderPlugin.PERMISSION_NOTIFICATION_LISTENER)
    }

    @Test
    fun `reads every package of a catalog app that posts readings`() {
        for (pkg in eversense.packages) {
            assertThat(notificationReaderPlugin.packageConfig.isSupportedPackage(pkg)).isTrue()
            assertThat(notificationReaderPlugin.packageConfig.sensorForPackage(pkg)).isEqualTo(SourceSensor.EVERSENSE)
            assertThat(notificationReaderPlugin.packageConfig.intervalForPackage(pkg, 0L)).isEqualTo(300_000L)
        }
    }

    /** xDrip sends its readings by broadcast: the reader must not take its notifications as readings too. */
    @Test
    fun `an app without notification readings is not read`() {
        whenever(catalog.apps).thenReturn(CgmApps(version = 2, apps = listOf(eversense, CgmApp("xdrip", "xDrip+", listOf("com.eveningoutpost.dexdrip")))))

        assertThat(notificationReaderPlugin.packageConfig.isSupportedPackage("com.eveningoutpost.dexdrip")).isFalse()
    }

    /** A catalog fetched from the versions branch takes effect without a restart. */
    @Test
    fun `a newer catalog changes what is read`() {
        assertThat(notificationReaderPlugin.packageConfig.isSupportedPackage("com.dexcom.g7")).isFalse()

        val g7 = CgmApp("dexcom-g7", "Dexcom G7", listOf("com.dexcom.g7"), NotificationReading("AAPS-DexcomG7", 5))
        whenever(catalog.apps).thenReturn(CgmApps(version = 2, apps = listOf(eversense, g7)))

        assertThat(notificationReaderPlugin.packageConfig.isSupportedPackage("com.dexcom.g7")).isTrue()
    }

    // ----- The app of the last reading -----

    private val ottai = CgmApp("ottai", "Ottai", listOf("com.ottai.seas"), NotificationReading("Ottai", 5))

    /** The deduplicator's saved state: the time of the last reading taken from each package. */
    private fun lastReadings(vararg readings: Pair<String, Long>) {
        val state = readings.joinToString(",", "[", "]") { (pkg, time) -> """{"p":"$pkg","t":$time,"i":300000}""" }
        whenever(preferences.get(StringNonKey.NotificationReaderDedupState)).thenReturn(state)
    }

    @Test
    fun `before the first reading there is no app and AAPS may calibrate`() {
        lastReadings()

        assertThat(notificationReaderPlugin.appPackages).isEmpty()
        assertThat(notificationReaderPlugin.calibrationMode).isEqualTo(CalibrationMode.AAPS)
    }

    /** Eversense needs calibrations and takes them in its own app: the buttons open it, and AAPS does not calibrate on top. */
    @Test
    fun `readings from an app that calibrates send the user to that app`() {
        whenever(catalog.apps).thenReturn(CgmApps(version = 2, apps = listOf(eversense.copy(calibration = Calibration.APP), ottai)))
        lastReadings("com.senseonics.eversense365.us" to 1_000L)

        assertThat(notificationReaderPlugin.appPackages).containsExactlyElementsIn(eversense.packages).inOrder()
        assertThat(notificationReaderPlugin.calibrationMode).isEqualTo(CalibrationMode.OWN_APP)
    }

    /** Two apps have posted readings: the one that posted last is the one in use. */
    @Test
    fun `the app of the newest reading counts`() {
        whenever(catalog.apps).thenReturn(CgmApps(version = 2, apps = listOf(eversense.copy(calibration = Calibration.APP), ottai)))
        lastReadings("com.senseonics.eversense365.us" to 1_000L, "com.ottai.seas" to 2_000L)

        assertThat(notificationReaderPlugin.appPackages).containsExactly("com.ottai.seas")
        assertThat(notificationReaderPlugin.calibrationMode).isEqualTo(CalibrationMode.AAPS)
    }
}
