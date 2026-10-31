package app.aaps.pump.carelevo.presentation.type

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

internal class CarelevoPatchStepTest {

    @Test
    fun `has exactly nine steps in declaration order`() {
        assertThat(CarelevoPatchStep.entries).containsExactly(
            CarelevoPatchStep.PROFILE_GATE,
            CarelevoPatchStep.SELECT_INSULIN,
            CarelevoPatchStep.PATCH_START,
            CarelevoPatchStep.SET_AMOUNT,
            CarelevoPatchStep.PATCH_CONNECT,
            CarelevoPatchStep.SAFETY_CHECK,
            CarelevoPatchStep.SITE_LOCATION,
            CarelevoPatchStep.PATCH_ATTACH,
            CarelevoPatchStep.NEEDLE_INSERTION
        ).inOrder()
    }

    @Test
    fun `ordinals are contiguous from zero`() {
        assertThat(CarelevoPatchStep.PROFILE_GATE.ordinal).isEqualTo(0)
        assertThat(CarelevoPatchStep.SELECT_INSULIN.ordinal).isEqualTo(1)
        assertThat(CarelevoPatchStep.PATCH_START.ordinal).isEqualTo(2)
        assertThat(CarelevoPatchStep.SET_AMOUNT.ordinal).isEqualTo(3)
        assertThat(CarelevoPatchStep.PATCH_CONNECT.ordinal).isEqualTo(4)
        assertThat(CarelevoPatchStep.SAFETY_CHECK.ordinal).isEqualTo(5)
        assertThat(CarelevoPatchStep.SITE_LOCATION.ordinal).isEqualTo(6)
        assertThat(CarelevoPatchStep.PATCH_ATTACH.ordinal).isEqualTo(7)
        assertThat(CarelevoPatchStep.NEEDLE_INSERTION.ordinal).isEqualTo(8)
    }

    @Test
    fun `valueOf resolves each name`() {
        CarelevoPatchStep.entries.forEach { step ->
            assertThat(CarelevoPatchStep.valueOf(step.name)).isEqualTo(step)
        }
    }

    @Test
    fun `valueOf rejects an unknown name`() {
        assertFailsWith<IllegalArgumentException> { CarelevoPatchStep.valueOf("NOT_A_STEP") }
    }
}
