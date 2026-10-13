package app.aaps.pump.danars.comm

import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.pump.danars.DanaRSTestBase
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock

class DanaRsMessageHashTableTest : DanaRSTestBase() {

    @Mock lateinit var pumpSync: PumpSync

    lateinit var packetList: Set<DanaRSPacket>

    @BeforeEach
    fun setupMock() {
        packetList = setOf(
            DanaRSPacketNotifyAlarm(aapsLogger, rh, pumpSync, danaPump, uiInteraction),
            DanaRSPacketNotifyDeliveryComplete(aapsLogger, rh, rxBus, danaPump),
            DanaRSPacketNotifyDeliveryRateDisplay(aapsLogger, rh, rxBus, danaPump),
            DanaRSPacketNotifyMissedBolusAlarm(aapsLogger)
        )
    }

    @Test
    fun findMessage() {
        val danaRSMessageHashTable = DanaRSMessageHashTable(packetList)
        val command = DanaRSPacketNotifyMissedBolusAlarm(aapsLogger).command
        Assertions.assertTrue(danaRSMessageHashTable.findMessage(command) is DanaRSPacketNotifyMissedBolusAlarm)
    }

    @Test
    fun throwErrorForUnknownMessage() {
        val danaRSMessageHashTable = DanaRSMessageHashTable(packetList)
        val command = DanaRSPacket().command
        assertThrows(Exception::class.java) {
            danaRSMessageHashTable.findMessage(command)
        }
    }
}