package app.aaps.implementation.profile

import app.aaps.core.interfaces.db.GlucoseUnit
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.database.entities.GlucoseValue
import app.aaps.implementation.utils.DecimalFormatterImpl
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock

class ProfileUtilImplTest : TestBase() {

    @Mock lateinit var rh: ResourceHelper
    @Mock lateinit var sp: SP

    private val glucoseValue =
        GlucoseValue(raw = 0.0, noise = 0.0, value = 100.0, timestamp = 1514766900000, sourceSensor = GlucoseValue.SourceSensor.UNKNOWN, trendArrow = GlucoseValue.TrendArrow.FLAT)

    private lateinit var sut: ProfileUtilImpl

    @BeforeEach
    fun setup() {
        val decimalFormatter = DecimalFormatterImpl(rh)
        sut = ProfileUtilImpl(sp, decimalFormatter)
    }

    @Test
    fun toUnitsString() {
        assertThat(sut.fromMgdlToStringInUnits(glucoseValue.value, GlucoseUnit.MGDL)).isEqualTo("100")
        assertThat(sut.fromMgdlToStringInUnits(glucoseValue.value, GlucoseUnit.MMOL)).isEqualTo("5.6")
        assertThat(sut.convertToMgdl(0.1, GlucoseUnit.MGDL)).isWithin(0.01).of(0.1)
        assertThat(sut.convertToMgdl(1.0, GlucoseUnit.MMOL)).isWithin(0.01).of(18.0)
        assertThat(sut.convertToMmol(18.0, GlucoseUnit.MGDL)).isWithin(0.01).of(1.0)
        assertThat(sut.convertToMmol(18.0, GlucoseUnit.MMOL)).isWithin(0.01).of(18.0)
        assertThat(sut.fromMgdlToUnits(18.0, GlucoseUnit.MGDL)).isWithin(0.01).of(18.0)
        assertThat(sut.fromMgdlToUnits(18.0, GlucoseUnit.MMOL)).isWithin(0.01).of(1.0)
        assertThat(sut.fromMgdlToUnits(18.0, GlucoseUnit.MGDL)).isWithin(0.01).of(18.0)
        assertThat(sut.fromMgdlToUnits(18.0, GlucoseUnit.MMOL)).isWithin(0.01).of(1.0)
        assertThat(sut.fromMgdlToStringInUnits(18.0, GlucoseUnit.MGDL)).isEqualTo("18")
        assertThat(sut.fromMgdlToStringInUnits(18.0, GlucoseUnit.MMOL).replace(",", ".")).isEqualTo("1.0")
        assertThat(sut.toTargetRangeString(5.0, 6.0, GlucoseUnit.MGDL, GlucoseUnit.MGDL)).isEqualTo("5 - 6")
        assertThat(sut.toTargetRangeString(4.0, 4.0, GlucoseUnit.MGDL, GlucoseUnit.MGDL)).isEqualTo("4")
    }
}
