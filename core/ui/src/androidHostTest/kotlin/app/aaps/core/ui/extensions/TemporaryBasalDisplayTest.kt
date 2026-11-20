package app.aaps.core.ui.extensions

import app.aaps.core.data.model.TB
import app.aaps.core.data.pump.defs.PumpDescription
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.utils.DateUtil
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyVararg
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/** Review #78: the temp basal texts showed a rate of 0.275 U/h as 0.28 U/h, also for a pump that runs 0.275. */
class TemporaryBasalDisplayTest {

    private val start = 1_800_000_000_000L

    /** Gives the arguments, so a test sees the rate as it was formatted. The separator is the one of the platform. */
    private val rh: TextResolver = mock<TextResolver>().also {
        whenever(it.gs(any(), anyVararg<Any>())).thenAnswer { invocation ->
            invocation.arguments.drop(1).joinToString("|") { argument -> if (argument is Array<*>) argument.joinToString("|") else argument.toString() }.replace(',', '.')
        }
    }
    private val dateUtil: DateUtil = mock<DateUtil>().also {
        whenever(it.now()).thenReturn(start)
        whenever(it.timeString(any<Long>())).thenReturn("12:00")
    }
    private val threeDigits = PumpDescription().apply { tempAbsoluteStep = 0.025 }.tempAbsoluteFormat
    private val tempBasal = TB(timestamp = start, duration = 30 * 60_000L, rate = 0.275, isAbsolute = true, type = TB.Type.NORMAL)

    @Test
    fun aPumpWithAStepOf0025ShowsTheThirdDigit() {
        assertThat(tempBasal.toStringFull(mock<Profile>(), dateUtil, rh, threeDigits)).startsWith("0.275|")
        assertThat(tempBasal.toStringShort(rh, threeDigits)).isEqualTo("0.275")
    }

    /** Callers that pass no format show what `%.2f` showed. */
    @Test
    fun withoutTheFormatOfThePumpItIsTwoDigitsAsBefore() {
        assertThat(tempBasal.toStringFull(mock<Profile>(), dateUtil, rh)).startsWith("0.28|")
        assertThat(tempBasal.toStringShort(rh)).isEqualTo("0.28")
    }

    @Test
    fun theShortPumpStatusShowsTheThirdDigit() {
        val running = PumpSync.PumpState.TemporaryBasal(start, 30 * 60_000L, 0.275, true, PumpSync.TemporaryBasalType.NORMAL, 0L, null)
        assertThat(running.toStringFull(dateUtil, rh, threeDigits)).startsWith("0.275|")
    }
}
