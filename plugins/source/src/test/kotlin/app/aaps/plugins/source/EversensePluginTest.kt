package app.aaps.plugins.source

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Bundle
import app.aaps.core.data.model.SourceSensor
import app.aaps.core.data.model.TrendArrow
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class EversensePluginTest : TestBaseWithProfile() {

    private lateinit var eversensePlugin: EversensePlugin

    @BeforeEach
    fun setup() {
        eversensePlugin = EversensePlugin(rh, aapsLogger, context, preferences)
    }

    @Test
    fun `requiredPermissions includes BYOESA permission when app is installed`() {
        val mockPm = mock<PackageManager> {
            @Suppress("DEPRECATION")
            on { getPackageInfo(eq(EversensePlugin.PACKAGE_NAME), any<Int>()) } doReturn PackageInfo()
        }
        whenever(context.packageManager).thenReturn(mockPm)

        val allPermissions = eversensePlugin.requiredPermissions().flatMap { it.permissions }

        assertThat(allPermissions).contains(EversensePlugin.PERMISSION)
    }

    @Test
    fun `requiredPermissions is empty when BYOESA is not installed`() {
        val allPermissions = eversensePlugin.requiredPermissions().flatMap { it.permissions }

        assertThat(allPermissions).doesNotContain(EversensePlugin.PERMISSION)
    }

    @Test
    fun `parser converts contract reading to Eversense glucose value`() {
        val now = 1_800_000_000_000L
        val reading = mock<Bundle> {
            on { getLong("timestamp", 0L) } doReturn 1_700_000_000L
            on { getInt("glucoseValue", 0) } doReturn 123
            on { getString("trendArrow") } doReturn "NONE"
        }
        val readings = mock<Bundle> {
            on { size() } doReturn 1
            on { getBundle("0") } doReturn reading
        }
        val bundle = validEnvelope(readings)

        val result = EversensePlugin.parseGlucoseValues(bundle, now)

        assertThat(result).hasSize(1)
        assertThat(result.single().timestamp).isEqualTo(1_700_000_000_000L)
        assertThat(result.single().value).isEqualTo(123.0)
        assertThat(result.single().trendArrow).isEqualTo(TrendArrow.NONE)
        assertThat(result.single().sourceSensor).isEqualTo(SourceSensor.EVERSENSE)
    }

    @Test
    fun `parser rejects unsupported contract version`() {
        val bundle = mock<Bundle> {
            on { getInt("byoesaContractVersion", 0) } doReturn 2
        }

        assertThat(EversensePlugin.parseGlucoseValues(bundle, 1_800_000_000_000L)).isEmpty()
    }

    @Test
    fun `parser rejects wrong sensor type and source package`() {
        val wrongSensor = mock<Bundle> {
            on { getInt("byoesaContractVersion", 0) } doReturn EversensePlugin.CONTRACT_VERSION
            on { getString("sensorType") } doReturn "G7"
        }
        val wrongPackage = mock<Bundle> {
            on { getInt("byoesaContractVersion", 0) } doReturn EversensePlugin.CONTRACT_VERSION
            on { getString("sensorType") } doReturn EversensePlugin.SENSOR_TYPE
            on { getString("sourcePackage") } doReturn "com.example.spoof"
        }

        assertThat(EversensePlugin.parseGlucoseValues(wrongSensor, 1_800_000_000_000L)).isEmpty()
        assertThat(EversensePlugin.parseGlucoseValues(wrongPackage, 1_800_000_000_000L)).isEmpty()
    }

    @Test
    fun `parser skips future and out of range readings`() {
        val future = mock<Bundle> {
            on { getLong("timestamp", 0L) } doReturn 1_900_000_000L
            on { getInt("glucoseValue", 0) } doReturn 100
        }
        val invalidValue = mock<Bundle> {
            on { getLong("timestamp", 0L) } doReturn 1_700_000_000L
            on { getInt("glucoseValue", 0) } doReturn 0
        }
        val readings = mock<Bundle> {
            on { size() } doReturn 2
            on { getBundle("0") } doReturn future
            on { getBundle("1") } doReturn invalidValue
        }

        assertThat(EversensePlugin.parseGlucoseValues(validEnvelope(readings), 1_800_000_000_000L)).isEmpty()
    }

    private fun validEnvelope(readings: Bundle): Bundle = mock {
        on { getInt("byoesaContractVersion", 0) } doReturn EversensePlugin.CONTRACT_VERSION
        on { getString("sensorType") } doReturn EversensePlugin.SENSOR_TYPE
        on { getString("sourcePackage") } doReturn EversensePlugin.PACKAGE_NAME
        on { getBundle("glucoseValues") } doReturn readings
    }
}
