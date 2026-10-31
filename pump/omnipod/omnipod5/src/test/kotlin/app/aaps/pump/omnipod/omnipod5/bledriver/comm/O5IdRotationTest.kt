package app.aaps.pump.omnipod.omnipod5.bledriver.comm

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class O5IdRotationTest {

    @Test
    fun `controllerIdForPodId clears the low 2 bits`() {
        assertThat(O5IdRotation.controllerIdForPodId(0b1100L)).isEqualTo(0b1100L)
        assertThat(O5IdRotation.controllerIdForPodId(0b1101L)).isEqualTo(0b1100L)
        assertThat(O5IdRotation.controllerIdForPodId(0b1110L)).isEqualTo(0b1100L)
        assertThat(O5IdRotation.controllerIdForPodId(0b1111L)).isEqualTo(0b1100L)
    }

    @Test
    fun `firstPodId normalizes every controller id suffix`() {
        val base = 0xAABBCC00L

        assertThat(O5IdRotation.firstPodId(base)).isEqualTo(base + 1)
        assertThat(O5IdRotation.firstPodId(base + 1)).isEqualTo(base + 1)
        assertThat(O5IdRotation.firstPodId(base + 2)).isEqualTo(base + 1)
        assertThat(O5IdRotation.firstPodId(base + 3)).isEqualTo(base + 1)
    }

    @Test
    fun `nextPodId advances to the identity for the next pod`() {
        val base = 0b1000L

        assertThat(O5IdRotation.nextPodId(base + 1)).isEqualTo(base + 2)
        assertThat(O5IdRotation.nextPodId(base + 2)).isEqualTo(base + 3)
    }

    @Test
    fun `nextPodId wraps back to base plus 1 after the third pod`() {
        val base = 0b10000L

        assertThat(O5IdRotation.nextPodId(base + 3)).isEqualTo(base + 1)
    }

    @Test
    fun `nextPodId preserves the controllerId bits across a wrap`() {
        val base = 0xAABBCC00L

        val wrapped = O5IdRotation.nextPodId(base + 3)

        assertThat(O5IdRotation.controllerIdForPodId(wrapped)).isEqualTo(base)
    }

    @Test
    fun `full rotation cycle visits exactly base+1, base+2, base+3, then repeats`() {
        val base = 0b100L
        var podId = base + 1

        val visited = mutableListOf(podId)
        repeat(3) {
            podId = O5IdRotation.nextPodId(podId)
            visited.add(podId)
        }

        assertThat(visited).containsExactly(base + 1, base + 2, base + 3, base + 1).inOrder()
    }
}
