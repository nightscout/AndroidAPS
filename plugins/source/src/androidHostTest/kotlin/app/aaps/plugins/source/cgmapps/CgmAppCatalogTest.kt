package app.aaps.plugins.source.cgmapps

import android.content.res.AssetManager
import app.aaps.core.interfaces.source.CalibrationMode
import app.aaps.core.keys.StringNonKey
import app.aaps.plugins.source.notificationreader.PackageConfig
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.File

class CgmAppCatalogTest : TestBaseWithProfile() {

    private val bundled = """{"version":2,"apps":[{"id":"xdrip","name":"xDrip+","packages":["com.eveningoutpost.dexdrip"]}]}"""

    @BeforeEach
    fun setUp() {
        val assets = mock<AssetManager>()
        whenever(context.assets).thenReturn(assets)
        whenever(assets.open("cgm_apps.json")).thenAnswer { bundled.byteInputStream() }
    }

    private fun stored(text: String) = whenever(preferences.get(StringNonKey.CgmAppCatalog)).thenReturn(text)

    private fun catalog() = CgmAppCatalog(context, preferences, aapsLogger)

    /** A fetched catalog with every app the code asks for, and no others. */
    private fun complete(version: Int, without: String? = null): String =
        CgmApps.USED_IDS.filter { it != without }
            .joinToString(",", prefix = "{\"version\":$version,\"apps\":[", postfix = "]}") { "{\"id\":\"$it\",\"name\":\"$it\",\"packages\":[\"$it\"]}" }

    @Test
    fun `the bundled catalog is used when nothing was fetched`() {
        stored("")
        assertThat(catalog().apps.version).isEqualTo(2)
    }

    /** A catalog fetched from the versions branch is newer than the one in the app, until the next release. */
    @Test
    fun `a newer fetched catalog is kept`() {
        stored(complete(version = 3))
        assertThat(catalog().apps.version).isEqualTo(3)
        verify(preferences, never()).remove(StringNonKey.CgmAppCatalog)
    }

    /** After an app update the bundled one can be newer: the old fetched one is dropped. */
    @Test
    fun `an older fetched catalog gives way to the bundled one and is dropped`() {
        stored(complete(version = 1))
        assertThat(catalog().apps.version).isEqualTo(2)
        verify(preferences).remove(StringNonKey.CgmAppCatalog)
    }

    /**
     * A newer catalog that lost an app the code asks for is not used: the app would no longer be found, and with it
     * where its sensor is calibrated. A broken file on the versions branch must not reach every phone.
     */
    @Test
    fun `a fetched catalog without an app the code asks for is not used`() {
        stored(complete(version = 3, without = CgmApps.DEXCOM_G6))
        assertThat(catalog().apps.version).isEqualTo(2)
    }

    @Test
    fun `a fetched catalog that cannot be read gives way to the bundled one`() {
        stored("not json")
        assertThat(catalog().apps.version).isEqualTo(2)
    }

    /** Older AAPS reads the same remote file, so a field added later must not break it. */
    @Test
    fun `unknown fields are ignored`() {
        val apps = CgmApps.fromJson("""{"version":5,"future":1,"apps":[{"id":"x","name":"X","packages":["p"],"calibrates":true}]}""")
        assertThat(apps.byPackage("p")?.id).isEqualTo("x")
    }

    @Test
    fun `where a sensor is calibrated decides the calibration mode`() {
        val apps = CgmApps.fromJson(
            """{"version":5,"apps":[
              {"id":"a","name":"A","packages":["a"],"calibration":"app"},
              {"id":"p","name":"P","packages":["p"],"calibration":"pump"},
              {"id":"n","name":"N","packages":["n"]}]}"""
        )
        assertThat(apps.byId("a").calibrationMode()).isEqualTo(CalibrationMode.OWN_APP)
        assertThat(apps.byId("p").calibrationMode()).isEqualTo(CalibrationMode.PUMP)
        assertThat(apps.byId("n").calibrationMode()).isEqualTo(CalibrationMode.AAPS)
        assertThat(apps.byId("unknown").calibrationMode()).isEqualTo(CalibrationMode.AAPS)
    }

    /**
     * A newer file may name a place this version does not know. The catalog still reads, and the sensor still counts
     * as calibrated somewhere: read as "no calibration", AAPS would correct values that are calibrated already.
     */
    @Test
    fun `an unknown calibration place does not break the catalog and keeps AAPS from calibrating`() {
        val apps = CgmApps.fromJson("""{"version":5,"apps":[{"id":"x","name":"X","packages":["p"],"calibration":"watch"}]}""")
        assertThat(apps.byId("x")?.calibration).isEqualTo(Calibration.OTHER)
        assertThat(apps.byId("x").calibrationMode()).isNotEqualTo(CalibrationMode.AAPS)
    }

    @Test
    fun `packages are found by app and by package`() {
        val apps = CgmApps.fromJson(bundled)
        assertThat(apps.packagesOf(CgmApps.XDRIP, "unknown")).containsExactly("com.eveningoutpost.dexdrip")
        assertThat(apps.byPackage("com.eveningoutpost.dexdrip")?.name).isEqualTo("xDrip+")
        assertThat(apps.byPackage("com.other")).isNull()
    }

    /** The catalog the app ships: readable, each id and package once, and the apps the code asks for by id. */
    @Test
    fun `the shipped catalog is sound`() {
        val apps = CgmApps.fromJson(File("src/androidMain/assets/cgm_apps.json").readText())

        assertThat(apps.apps.map { it.id }).containsNoDuplicates()
        assertThat(apps.apps.flatMap { it.packages }).containsNoDuplicates()
        assertThat(apps.missingIds()).isEmpty()
        // The old DexcomPlugin knew this one; the catalog had lost it once
        assertThat(apps.byPackage("com.dexcom.g6.region3.mmol")?.id).isEqualTo(CgmApps.DEXCOM_G6)
        // xDrip broadcasts its readings; the notification reader must not read them a second time
        assertThat(PackageConfig.fromCatalog(apps).isSupportedPackage("com.eveningoutpost.dexdrip")).isFalse()
        assertThat(PackageConfig.fromCatalog(apps).isSupportedPackage("com.dexcom.g7")).isTrue()
    }
}
