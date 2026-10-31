package app.aaps.implementation.utils

import app.aaps.core.data.model.RM
import app.aaps.core.data.model.TE
import app.aaps.core.data.model.TT
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.shared.tests.generatedTextResolver
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Exercises every branch of [TranslatorImpl]'s `translate` overloads by iterating each enum's
 * `.entries`.
 *
 * The resolver answers with the REAL English text, so a mapped value that points at a string nobody
 * owns is visible: the resolver falls back to the name of the ref, which is snake_case, while every
 * real English string here is not. That is what [assertTranslated] checks, on top of catching a
 * missing or duplicate `when` branch the way the old marker-string version did.
 */
internal class TranslatorImplTest {

    private lateinit var sut: TranslatorImpl

    @BeforeEach
    fun setUp() {
        sut = TranslatorImpl(generatedTextResolver())
    }

    /** Real text, not the snake_case name an unresolved [app.aaps.core.keys.interfaces.TextRef.Named] falls back to. */
    private fun assertTranslated(text: String) {
        assertThat(text).isNotEmpty()
        assertThat(text).doesNotContain("_")
    }

    @Test
    fun `every Action translates to a resource`() {
        Action.entries.forEach { assertTranslated(sut.translate(it)) }
    }

    @Test
    fun `every TE type, meter type, location and arrow translates`() {
        TE.Type.entries.forEach { assertTranslated(sut.translate(it)) }
        TE.MeterType.entries.forEach { assertTranslated(sut.translate(it)) }
        TE.Location.entries.forEach { assertTranslated(sut.translate(it)) }
        TE.Arrow.entries.forEach { assertTranslated(sut.translate(it)) }
        // null falls through to the else -> unknown branch.
        assertTranslated(sut.translate(null as TE.Type?))
        assertTranslated(sut.translate(null as TE.MeterType?))
        assertTranslated(sut.translate(null as TE.Location?))
        assertTranslated(sut.translate(null as TE.Arrow?))
    }

    @Test
    fun `every TT reason and RM mode translates`() {
        TT.Reason.entries.forEach { assertTranslated(sut.translate(it)) }
        RM.Mode.entries.forEach { assertTranslated(sut.translate(it)) }
        assertTranslated(sut.translate(null as TT.Reason?))
        // RM.Mode null maps to empty, not unknown.
        assertThat(sut.translate(null as RM.Mode?)).isEmpty()
    }

    @Test
    fun `every Source translates to a non-empty string`() {
        // Mapped sources return their text; unmapped ones fall through to source.name (also non-empty).
        Sources.entries.forEach { assertThat(sut.translate(it)).isNotEmpty() }
    }

    @Test
    fun `ValueWithUnit units translate and other subtypes yield empty`() {
        val mapped = listOf(
            ValueWithUnit.Gram(1), ValueWithUnit.Hour(1), ValueWithUnit.Insulin(1.0), ValueWithUnit.Mgdl(1.0),
            ValueWithUnit.Minute(1), ValueWithUnit.Mmoll(1.0), ValueWithUnit.Percent(1), ValueWithUnit.UnitPerHour(1.0)
        )
        mapped.forEach { assertTranslated(sut.translate(it)) }
        // Non-unit subtypes and null fall through to the else -> "".
        assertThat(sut.translate(ValueWithUnit.SimpleInt(1))).isEmpty()
        assertThat(sut.translate(null as ValueWithUnit?)).isEmpty()
    }
}
