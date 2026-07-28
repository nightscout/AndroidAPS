package app.aaps.implementation.insulin

import app.aaps.core.data.model.ICfg
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.pump.PumpInsulin
import app.aaps.core.interfaces.pump.PumpRate
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.Mock
import org.mockito.kotlin.anyVararg
import org.mockito.kotlin.whenever

/**
 * Covers [ConcentrationHelperImpl] concentration conversions (pump⇄internal units, U100 detection)
 * and the rh/dateUtil-backed string builders. The activePump-dependent bolus-step paths are out of scope.
 */
class ConcentrationHelperImplTest : TestBase() {

    @Mock lateinit var activePlugin: ActivePlugin
    @Mock lateinit var profileFunction: ProfileFunction
    @Mock lateinit var rh: ResourceHelper
    @Mock lateinit var decimalFormatter: DecimalFormatter
    @Mock lateinit var dateUtil: DateUtil

    @BeforeEach
    fun setup() {
        whenever(rh.gs(anyInt())).thenReturn("s")
        whenever(rh.gs(anyInt(), anyVararg())).thenReturn("s")
        whenever(dateUtil.timeString(anyLong())).thenReturn("12:00")
        whenever(dateUtil.now()).thenReturn(1_000_000L)
    }

    private fun sut(concentration: Double): ConcentrationHelperImpl {
        whenever(profileFunction.runningICfg).thenReturn(MutableStateFlow(ICfg("Insulin", 75, 6.0, concentration)))
        return ConcentrationHelperImpl(aapsLogger, activePlugin, profileFunction, rh, decimalFormatter, dateUtil)
    }

    // Before the first profile switch there is no insulin in force. Every conversion here multiplies or divides
    // by concentration, so 1.0 (the identity) passes the pump's own units through untouched rather than scaling
    // them by an insulin the user never chose.
    @Test
    fun withNoInsulinInForceConversionsAreIdentity() {
        whenever(profileFunction.runningICfg).thenReturn(MutableStateFlow(null))
        val sut = ConcentrationHelperImpl(aapsLogger, activePlugin, profileFunction, rh, decimalFormatter, dateUtil)

        assertThat(sut.concentration).isEqualTo(1.0)
        assertThat(sut.isU100()).isTrue()
        assertThat(sut.toPump(10.0).cU).isEqualTo(10.0)
        assertThat(sut.fromPump(PumpInsulin(10.0), isPriming = false)).isEqualTo(10.0)
    }

    @Test
    fun concentrationAndIsU100() {
        assertThat(sut(1.0).isU100()).isTrue()
        assertThat(sut(2.0).isU100()).isFalse()
        assertThat(sut(2.0).concentration).isEqualTo(2.0)
    }

    @Test
    fun toPumpConvertsByConcentration() {
        val s = sut(2.0)
        assertThat(s.toPump(10.0).cU).isEqualTo(5.0)       // 10 iU / 2 = 5 cU
        assertThat(s.toPumpRate(6.0).cU).isEqualTo(3.0)
    }

    @Test
    fun fromPumpConvertsByConcentration() {
        val s = sut(2.0)
        assertThat(s.fromPump(PumpInsulin(5.0), isPriming = false)).isEqualTo(10.0) // 5 cU * 2
        assertThat(s.fromPump(PumpInsulin(5.0), isPriming = true)).isEqualTo(5.0)    // priming → raw cU
        assertThat(s.fromPump(PumpRate(3.0))).isEqualTo(6.0)                          // absolute rate * 2
    }

    @Test
    fun toPumpFromPumpRoundTrip() {
        val s = sut(2.5)
        val internal = 7.5
        assertThat(s.fromPump(s.toPump(internal), isPriming = false)).isWithin(1e-9).of(internal)
    }

    @Test
    fun insulinConcentrationString_usesResource() {
        assertThat(sut(2.0).insulinConcentrationString()).isEqualTo("s")
    }

    @Test
    fun basalRateString_percentAndAbsoluteBranches() {
        assertThat(sut(1.0).basalRateString(PumpRate(1.5), isAbsolute = false)).isEqualTo("s") // percent branch
        assertThat(sut(1.0).basalRateString(PumpRate(1.5), isAbsolute = true)).isEqualTo("s")  // U100 absolute
        assertThat(sut(2.0).basalRateString(PumpRate(1.5), isAbsolute = true)).isEqualTo("s")  // non-U100 absolute
    }

    @Test
    fun basalTbrString_buildsString() {
        assertThat(sut(1.0).basalTbrString(PumpRate(1.5), startTime = 0L, durationInMin = 30, isAbsolute = true, isExtended = false))
            .isEqualTo("s")
    }

    @Test
    fun insulinAmountAgoString_nullWhenTooOld() {
        // lastBolusTime at epoch → ago > 6h → null (does not touch activePump)
        assertThat(sut(1.0).insulinAmountAgoString(PumpInsulin(1.0), lastBolusTime = 0L)).isNull()
    }

    @Test
    fun bolusProgressStrings_useResource() {
        val s = sut(2.0)
        assertThat(s.bolusProgressString(PumpInsulin(1.0), isPriming = false)).isEqualTo("s")
        assertThat(s.bolusProgressString(PumpInsulin(1.0), total = 5.0, isPriming = false)).isEqualTo("s")
    }
}
