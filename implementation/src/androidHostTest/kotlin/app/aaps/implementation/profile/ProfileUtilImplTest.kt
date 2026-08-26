package app.aaps.implementation.profile

import app.aaps.core.data.model.GV
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.SourceSensor
import app.aaps.core.data.model.TrendArrow
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.R
import app.aaps.implementation.utils.DecimalFormatterImpl
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyDouble
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mock
import org.mockito.kotlin.whenever

class ProfileUtilImplTest : TestBase() {

    @Mock lateinit var rh: ResourceHelper
    @Mock lateinit var preferences: Preferences

    private val glucoseValue =
        GV(raw = 0.0, noise = 0.0, value = 100.0, timestamp = 1514766900000, sourceSensor = SourceSensor.UNKNOWN, trendArrow = TrendArrow.FLAT)

    private lateinit var sut: ProfileUtilImpl

    @BeforeEach
    fun setup() {
        val decimalFormatter = DecimalFormatterImpl(rh)
        sut = ProfileUtilImpl(preferences, decimalFormatter, rh)
    }

    @Test
    fun toUnitsString() {
        assertThat(sut.fromMgdlToStringInUnits(glucoseValue.value, GlucoseUnit.MGDL)).isEqualTo("100")
        assertThat(sut.fromMgdlToStringInUnits(glucoseValue.value, GlucoseUnit.MMOL)).isEqualTo("5.6")
        assertThat(sut.convertToMgdl(0.1, GlucoseUnit.MGDL)).isWithin(0.01).of(0.1)
        assertThat(sut.convertToMgdl(1.0, GlucoseUnit.MMOL)).isWithin(0.01).of(18.01559)
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
        assertThat(sut.fromMgdlToStringInUnits(null, GlucoseUnit.MGDL)).isEqualTo("")
        assertThat(sut.fromMgdlToStringInUnits(null, GlucoseUnit.MMOL)).isEqualTo("")
    }

    @Test
    fun unitLabel() {
        whenever(rh.gs(R.string.mgdl)).thenReturn("mg/dl")
        whenever(rh.gs(R.string.mmol)).thenReturn("mmol/l")

        whenever(preferences.get(StringKey.GeneralUnits)).thenReturn(GlucoseUnit.MGDL.asText)
        assertThat(sut.unitLabel).isEqualTo("mg/dl")

        whenever(preferences.get(StringKey.GeneralUnits)).thenReturn(GlucoseUnit.MMOL.asText)
        assertThat(sut.unitLabel).isEqualTo("mmol/l")
    }

    @Test
    fun fromMgdlToStringWithUnits() {
        whenever(rh.gs(R.string.bg_mgdl)).thenReturn("%1\$d mg/dl")
        whenever(rh.gs(R.string.bg_mmol)).thenReturn("%1\$.1f mmol/l")
        whenever(rh.gs(anyInt(), anyInt())).thenAnswer { inv ->
            String.format(rh.gs(inv.arguments[0] as Int), inv.arguments[1])
        }
        whenever(rh.gs(anyInt(), anyDouble())).thenAnswer { inv ->
            String.format(rh.gs(inv.arguments[0] as Int), inv.arguments[1])
        }

        whenever(preferences.get(StringKey.GeneralUnits)).thenReturn(GlucoseUnit.MGDL.asText)
        assertThat(sut.fromMgdlToStringWithUnits(180.0)).isEqualTo("180 mg/dl")
        assertThat(sut.fromMgdlToStringWithUnits(null)).isEqualTo("")

        // 180 mg/dl = 10.0 mmol/l
        whenever(preferences.get(StringKey.GeneralUnits)).thenReturn(GlucoseUnit.MMOL.asText)
        assertThat(sut.fromMgdlToStringWithUnits(180.0)).isEqualTo("10.0 mmol/l")
        assertThat(sut.fromMgdlToStringWithUnits(null)).isEqualTo("")
    }

    @Test
    fun units() {
        whenever(preferences.get(StringKey.GeneralUnits)).thenReturn(GlucoseUnit.MGDL.asText)
        assertThat(sut.units).isEqualTo(GlucoseUnit.MGDL)

        whenever(preferences.get(StringKey.GeneralUnits)).thenReturn(GlucoseUnit.MMOL.asText)
        assertThat(sut.units).isEqualTo(GlucoseUnit.MMOL)

        whenever(preferences.get(StringKey.GeneralUnits)).thenReturn("")
        assertThat(sut.units).isEqualTo(GlucoseUnit.MMOL)
    }

    @Test
    fun fromMmolToUnits() {
        assertThat(sut.fromMmolToUnits(5.0, GlucoseUnit.MMOL)).isWithin(0.01).of(5.0)
        assertThat(sut.fromMmolToUnits(5.0, GlucoseUnit.MGDL)).isWithin(0.01).of(90.07795)
    }

    @Test
    fun isMgdlAndIsMmol() {
        assertThat(sut.isMgdl(36.0)).isTrue()
        assertThat(sut.isMgdl(100.0)).isTrue()
        assertThat(sut.isMgdl(35.9)).isFalse()
        assertThat(sut.isMmol(35.9)).isTrue()
        assertThat(sut.isMmol(5.0)).isTrue()
        assertThat(sut.isMmol(36.0)).isFalse()
    }

    @Test
    fun unitsDetect() {
        assertThat(sut.unitsDetect(100.0)).isEqualTo(GlucoseUnit.MGDL)
        assertThat(sut.unitsDetect(36.0)).isEqualTo(GlucoseUnit.MGDL)
        assertThat(sut.unitsDetect(35.9)).isEqualTo(GlucoseUnit.MMOL)
        assertThat(sut.unitsDetect(5.5)).isEqualTo(GlucoseUnit.MMOL)
    }

    @Test
    fun convertToMgdlDetect() {
        assertThat(sut.convertToMgdlDetect(180.0)).isWithin(0.01).of(180.0)
        assertThat(sut.convertToMgdlDetect(10.0)).isWithin(0.01).of(180.1559)
    }

    @Test
    fun valueInUnitsDetect() {
        // 180.0 is detected as mg/dl (>=36)
        assertThat(sut.valueInUnitsDetect(180.0, GlucoseUnit.MGDL)).isWithin(0.01).of(180.0)
        assertThat(sut.valueInUnitsDetect(180.0, GlucoseUnit.MMOL)).isWithin(0.01).of(10.0)
        // 10.0 is detected as mmol/l (<36)
        assertThat(sut.valueInUnitsDetect(10.0, GlucoseUnit.MMOL)).isWithin(0.01).of(10.0)
        assertThat(sut.valueInUnitsDetect(10.0, GlucoseUnit.MGDL)).isWithin(0.01).of(180.1559)
    }

    @Test
    fun valueInCurrentUnitsDetect() {
        whenever(preferences.get(StringKey.GeneralUnits)).thenReturn(GlucoseUnit.MGDL.asText)
        assertThat(sut.valueInCurrentUnitsDetect(180.0)).isWithin(0.01).of(180.0) // mg/dl → mg/dl
        assertThat(sut.valueInCurrentUnitsDetect(10.0)).isWithin(0.01).of(180.1559)  // mmol/l → mg/dl

        whenever(preferences.get(StringKey.GeneralUnits)).thenReturn(GlucoseUnit.MMOL.asText)
        assertThat(sut.valueInCurrentUnitsDetect(180.0)).isWithin(0.01).of(10.0)  // mg/dl → mmol/l
        assertThat(sut.valueInCurrentUnitsDetect(10.0)).isWithin(0.01).of(10.0)   // mmol/l → mmol/l
    }

    @Test
    fun stringInCurrentUnitsDetect() {
        whenever(preferences.get(StringKey.GeneralUnits)).thenReturn(GlucoseUnit.MGDL.asText)
        assertThat(sut.stringInCurrentUnitsDetect(180.0)).isEqualTo("180")
        assertThat(sut.stringInCurrentUnitsDetect(10.0)).isEqualTo("180") // 10 mmol detected, shown as mg/dl

        whenever(preferences.get(StringKey.GeneralUnits)).thenReturn(GlucoseUnit.MMOL.asText)
        assertThat(sut.stringInCurrentUnitsDetect(180.0).replace(",", ".")).isEqualTo("10.0")
        assertThat(sut.stringInCurrentUnitsDetect(10.0).replace(",", ".")).isEqualTo("10.0")
    }

    @Test
    fun fromMgdlToSignedStringInUnits() {
        assertThat(sut.fromMgdlToSignedStringInUnits(18.0, GlucoseUnit.MGDL)).isEqualTo("+18")
        assertThat(sut.fromMgdlToSignedStringInUnits(-18.0, GlucoseUnit.MGDL)).isEqualTo("-18")
        assertThat(sut.fromMgdlToSignedStringInUnits(18.0, GlucoseUnit.MMOL).replace(",", ".")).isEqualTo("+1.0")
        assertThat(sut.fromMgdlToSignedStringInUnits(-18.0, GlucoseUnit.MMOL).replace(",", ".")).isEqualTo("-1.0")
    }

    @Test
    fun stringInUnitsDetect() {
        // 180.0 is detected as mg/dl
        assertThat(sut.stringInUnitsDetect(180.0, GlucoseUnit.MGDL)).isEqualTo("180")
        assertThat(sut.stringInUnitsDetect(180.0, GlucoseUnit.MMOL).replace(",", ".")).isEqualTo("10.0")
        // 10.0 is detected as mmol/l
        assertThat(sut.stringInUnitsDetect(10.0, GlucoseUnit.MGDL)).isEqualTo("180")
        assertThat(sut.stringInUnitsDetect(10.0, GlucoseUnit.MMOL).replace(",", ".")).isEqualTo("10.0")
    }

    @Test
    fun getBasalProfilesDisplayable() {
        assertThat(sut.getBasalProfilesDisplayable(emptyArray(), PumpType.GENERIC_AAPS)).isEqualTo("")

        val profiles = arrayOf(
            Profile.ProfileValue(0, 1.0),
            Profile.ProfileValue(3600, 0.5)
        )
        val result = sut.getBasalProfilesDisplayable(profiles, PumpType.GENERIC_AAPS)
        assertThat(result).isEqualTo("00:00 1.000,\n01:00 0.500")
    }
}
