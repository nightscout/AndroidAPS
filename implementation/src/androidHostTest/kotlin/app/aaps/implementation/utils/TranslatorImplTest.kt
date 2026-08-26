package app.aaps.implementation.utils

import app.aaps.core.data.model.RM
import app.aaps.core.data.model.TE
import app.aaps.core.data.model.TT
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.interfaces.resources.ResourceHelper
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.whenever

/**
 * Exercises every branch of [TranslatorImpl]'s `translate` overloads by iterating each enum's
 * `.entries`. `rh.gs(id)` is stubbed to a fixed marker, so every mapped value returns it; this
 * catches a missing/duplicate `when` branch and a wrong string-resource reference.
 */
internal class TranslatorImplTest {

    @Mock private lateinit var rh: ResourceHelper
    private lateinit var sut: TranslatorImpl

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        whenever(rh.gs(anyInt())).thenReturn("x")
        sut = TranslatorImpl(rh)
    }

    @Test
    fun `every Action translates to a resource`() {
        Action.entries.forEach { assertThat(sut.translate(it)).isEqualTo("x") }
    }

    @Test
    fun `every TE type, meter type, location and arrow translates`() {
        TE.Type.entries.forEach { assertThat(sut.translate(it)).isEqualTo("x") }
        TE.MeterType.entries.forEach { assertThat(sut.translate(it)).isEqualTo("x") }
        TE.Location.entries.forEach { assertThat(sut.translate(it)).isEqualTo("x") }
        TE.Arrow.entries.forEach { assertThat(sut.translate(it)).isEqualTo("x") }
        // null falls through to the else -> unknown branch.
        assertThat(sut.translate(null as TE.Type?)).isEqualTo("x")
        assertThat(sut.translate(null as TE.MeterType?)).isEqualTo("x")
        assertThat(sut.translate(null as TE.Location?)).isEqualTo("x")
        assertThat(sut.translate(null as TE.Arrow?)).isEqualTo("x")
    }

    @Test
    fun `every TT reason and RM mode translates`() {
        TT.Reason.entries.forEach { assertThat(sut.translate(it)).isEqualTo("x") }
        RM.Mode.entries.forEach { assertThat(sut.translate(it)).isEqualTo("x") }
        assertThat(sut.translate(null as TT.Reason?)).isEqualTo("x")
        // RM.Mode null maps to empty, not unknown.
        assertThat(sut.translate(null as RM.Mode?)).isEmpty()
    }

    @Test
    fun `every Source translates to a non-empty string`() {
        // Mapped sources return the marker; unmapped ones fall through to source.name (also non-empty).
        Sources.entries.forEach { assertThat(sut.translate(it)).isNotEmpty() }
    }

    @Test
    fun `ValueWithUnit units translate and other subtypes yield empty`() {
        val mapped = listOf(
            ValueWithUnit.Gram(1), ValueWithUnit.Hour(1), ValueWithUnit.Insulin(1.0), ValueWithUnit.Mgdl(1.0),
            ValueWithUnit.Minute(1), ValueWithUnit.Mmoll(1.0), ValueWithUnit.Percent(1), ValueWithUnit.UnitPerHour(1.0)
        )
        mapped.forEach { assertThat(sut.translate(it)).isEqualTo("x") }
        // Non-unit subtypes and null fall through to the else -> "".
        assertThat(sut.translate(ValueWithUnit.SimpleInt(1))).isEmpty()
        assertThat(sut.translate(null as ValueWithUnit?)).isEmpty()
    }
}
