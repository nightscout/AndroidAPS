package app.aaps.ui.compose.overview

import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.TE
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.shared.tests.generatedTextResolver
import app.aaps.ui.UiStringsValues
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * The conversion itself is `ProfileUtilImpl`'s and tested there. This checks that the label asks for
 * it with the unit the value was entered in, and where the value and the note go.
 */
class TherapyEventLabelTest {

    private val rh = generatedTextResolver("ui" to UiStringsValues::textOf)
    private val profileUtil = mock<ProfileUtil>().also {
        whenever(it.convertToMgdl(6.1, GlucoseUnit.MMOL)).thenReturn(110.0)
        whenever(it.convertToMgdl(110.0, GlucoseUnit.MGDL)).thenReturn(110.0)
        whenever(it.fromMgdlToStringWithUnits(110.0)).thenReturn("6.1 mmol/L")
    }

    private fun te(glucose: Double?, unit: GlucoseUnit, note: String? = null) =
        TE(timestamp = 1_000L, type = TE.Type.FINGER_STICK_BG_VALUE, glucose = glucose, glucoseUnit = unit, note = note)

    @Test
    fun `bg check shows its value in the user's units`() {
        assertThat(therapyEventLabel(te(110.0, GlucoseUnit.MGDL), "BG check", profileUtil, rh)).isEqualTo("6.1 mmol/L")
    }

    @Test
    fun `value entered in mmol is converted from its own unit`() {
        assertThat(therapyEventLabel(te(6.1, GlucoseUnit.MMOL), "BG check", profileUtil, rh)).isEqualTo("6.1 mmol/L")
    }

    @Test
    fun `value comes before the note`() {
        assertThat(therapyEventLabel(te(6.1, GlucoseUnit.MMOL, note = "before lunch"), "BG check", profileUtil, rh))
            .isEqualTo("6.1 mmol/L, before lunch")
    }

    @Test
    fun `event without glucose keeps its note`() {
        assertThat(therapyEventLabel(te(null, GlucoseUnit.MGDL, note = "site sore"), "Note", profileUtil, rh)).isEqualTo("site sore")
    }

    @Test
    fun `event without glucose or note shows its type`() {
        assertThat(therapyEventLabel(te(null, GlucoseUnit.MGDL, note = " "), "Note", profileUtil, rh)).isEqualTo("Note")
    }
}
