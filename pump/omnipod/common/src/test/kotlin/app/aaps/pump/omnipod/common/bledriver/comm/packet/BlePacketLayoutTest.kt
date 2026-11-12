package app.aaps.pump.omnipod.common.bledriver.comm.packet

import app.aaps.pump.omnipod.common.bledriver.pod.definition.PodType
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Pins [BlePacketLayout]'s derived capacities against the values that used to be hardcoded
 * constants in [BlePacket] (Dash) and against OmnipodKit's BlePodProfile.swift (O5), so a
 * future edit to the formulas can't silently drift from either.
 */
class BlePacketLayoutTest : TestBase() {

    @Test
    fun `Dash layout reproduces the previously-hardcoded packet constants exactly`() {
        val layout = BlePacketLayout.DASH

        assertThat(layout.maxPayloadSize).isEqualTo(20)
        assertThat(layout.maxFragments).isEqualTo(15)
        assertThat(layout.firstPacketCapacityWithoutMiddlePackets).isEqualTo(13)
        assertThat(layout.firstPacketCapacityWithMiddlePackets).isEqualTo(18)
        assertThat(layout.firstPacketCapacityWithOptionalPlusOnePacket).isEqualTo(18)
        assertThat(layout.middlePacketCapacity).isEqualTo(19)
        assertThat(layout.lastPacketCapacity).isEqualTo(14)
    }

    @Test
    fun `O5 layout matches OmnipodKit's BlePodProfile omnipod5 preset`() {
        val layout = BlePacketLayout.OMNIPOD_5

        assertThat(layout.maxPayloadSize).isEqualTo(244)
        assertThat(layout.maxFragments).isEqualTo(15)
        assertThat(layout.firstPacketCapacityWithoutMiddlePackets).isEqualTo(237)
        assertThat(layout.firstPacketCapacityWithMiddlePackets).isEqualTo(242)
        assertThat(layout.firstPacketCapacityWithOptionalPlusOnePacket).isEqualTo(242)
        assertThat(layout.middlePacketCapacity).isEqualTo(243)
        assertThat(layout.lastPacketCapacity).isEqualTo(238)
    }

    @Test
    fun `PodType blePacketLayout picks OMNIPOD_5 only for the O5 pod type`() {
        assertThat(PodType.OMNIPOD_5.blePacketLayout).isEqualTo(BlePacketLayout.OMNIPOD_5)
        assertThat(PodType.DASH.blePacketLayout).isEqualTo(BlePacketLayout.DASH)
        assertThat(PodType.EROS.blePacketLayout).isEqualTo(BlePacketLayout.DASH)
        assertThat(PodType.UNKNOWN.blePacketLayout).isEqualTo(BlePacketLayout.DASH)
    }
}
