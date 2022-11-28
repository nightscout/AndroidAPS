package info.nightscout.comboctl.base.testUtils

import info.nightscout.comboctl.base.ApplicationLayer
import info.nightscout.comboctl.base.TransportLayer
import info.nightscout.comboctl.base.toTransportLayerPacket
import kotlin.test.assertEquals
import kotlin.test.assertTrue

typealias TestPacketData = List<Byte>

fun newTestPacketSequence() = mutableListOf<TestPacketData>()

sealed class TestRefPacketItem {
    data class TransportLayerPacketItem(val packetInfo: TransportLayer.OutgoingPacketInfo) : TestRefPacketItem()
    data class ApplicationLayerPacketItem(val packet: ApplicationLayer.Packet) : TestRefPacketItem()
}

fun checkTestPacketSequence(referenceSequence: List<TestRefPacketItem>, testPacketSequence: List<TestPacketData>) {
    assertTrue(testPacketSequence.size >= referenceSequence.size)

    referenceSequence.zip(testPacketSequence) { referenceItem, tpLayerPacketData ->
        val testTpLayerPacket = tpLayerPacketData.toTransportLayerPacket()

        when (referenceItem) {
            is TestRefPacketItem.TransportLayerPacketItem -> {
                val refPacketInfo = referenceItem.packetInfo
                assertEquals(refPacketInfo.command, testTpLayerPacket.command, "Transport layer packet command mismatch")
                assertEquals(refPacketInfo.payload, testTpLayerPacket.payload, "Transport layer packet payload mismatch")
                assertEquals(refPacketInfo.reliable, testTpLayerPacket.reliabilityBit, "Transport layer packet reliability bit mismatch")
            }
            is TestRefPacketItem.ApplicationLayerPacketItem -> {
                val refAppLayerPacket = referenceItem.packet
                val testAppLayerPacket = ApplicationLayer.Packet(testTpLayerPacket)
                assertEquals(refAppLayerPacket, testAppLayerPacket)
            }
        }
    }
}
