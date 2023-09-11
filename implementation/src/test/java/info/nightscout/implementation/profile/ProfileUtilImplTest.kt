package info.nightscout.implementation.profile

import info.nightscout.database.entities.GlucoseValue
import info.nightscout.implementation.utils.DecimalFormatterImpl
import info.nightscout.interfaces.GlucoseUnit
import info.nightscout.shared.interfaces.ResourceHelper
import info.nightscout.shared.sharedPreferences.SP
import info.nightscout.sharedtests.TestBase
import org.junit.jupiter.api.Assertions
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
        Assertions.assertEquals("100", sut.fromMgdlToStringInUnits(glucoseValue.value, GlucoseUnit.MGDL))
        Assertions.assertEquals("5.6", sut.fromMgdlToStringInUnits(glucoseValue.value, GlucoseUnit.MMOL))
        Assertions.assertEquals(0.1, sut.convertToMgdl(0.1, GlucoseUnit.MGDL), 0.01)
        Assertions.assertEquals(18.0, sut.convertToMgdl(1.0, GlucoseUnit.MMOL), 0.01)
        Assertions.assertEquals(1.0, sut.convertToMmol(18.0, GlucoseUnit.MGDL), 0.01)
        Assertions.assertEquals(18.0, sut.convertToMmol(18.0, GlucoseUnit.MMOL), 0.01)
        Assertions.assertEquals(18.0, sut.fromMgdlToUnits(18.0, GlucoseUnit.MGDL), 0.01)
        Assertions.assertEquals(1.0, sut.fromMgdlToUnits(18.0, GlucoseUnit.MMOL), 0.01)
        Assertions.assertEquals(18.0, sut.fromMgdlToUnits(18.0, GlucoseUnit.MGDL), 0.01)
        Assertions.assertEquals(1.0, sut.fromMgdlToUnits(18.0, GlucoseUnit.MMOL), 0.01)
        Assertions.assertEquals("18", sut.fromMgdlToStringInUnits(18.0, GlucoseUnit.MGDL))
        Assertions.assertEquals("1.0", sut.fromMgdlToStringInUnits(18.0, GlucoseUnit.MMOL).replace(",", "."))
        Assertions.assertEquals("5 - 6", sut.toTargetRangeString(5.0, 6.0, GlucoseUnit.MGDL, GlucoseUnit.MGDL))
        Assertions.assertEquals("4", sut.toTargetRangeString(4.0, 4.0, GlucoseUnit.MGDL, GlucoseUnit.MGDL))
    }
}