package app.aaps.pump.carelevo.presentation.type

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

internal class CarelevoScreenTypeTest {

    @Test
    fun `has exactly four screens in declaration order`() {
        assertThat(CarelevoScreenType.entries).containsExactly(
            CarelevoScreenType.CONNECTION_FLOW_START,
            CarelevoScreenType.PATCH_DISCARD,
            CarelevoScreenType.SAFETY_CHECK,
            CarelevoScreenType.NEEDLE_INSERTION
        ).inOrder()
    }

    @Test
    fun `ordinals are contiguous from zero`() {
        assertThat(CarelevoScreenType.CONNECTION_FLOW_START.ordinal).isEqualTo(0)
        assertThat(CarelevoScreenType.PATCH_DISCARD.ordinal).isEqualTo(1)
        assertThat(CarelevoScreenType.SAFETY_CHECK.ordinal).isEqualTo(2)
        assertThat(CarelevoScreenType.NEEDLE_INSERTION.ordinal).isEqualTo(3)
    }

    @Test
    fun `valueOf resolves each name`() {
        CarelevoScreenType.entries.forEach { type ->
            assertThat(CarelevoScreenType.valueOf(type.name)).isEqualTo(type)
        }
    }

    @Test
    fun `valueOf rejects an unknown name`() {
        assertFailsWith<IllegalArgumentException> { CarelevoScreenType.valueOf("NOPE") }
    }
}
