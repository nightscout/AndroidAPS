package info.nightscout.pump.danars.comm

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.danars.encryption.BleEncryption
import info.nightscout.interfaces.constraints.Constraint
import info.nightscout.interfaces.constraints.Constraints
import info.nightscout.pump.dana.DanaPump
import info.nightscout.pump.danars.DanaRSTestBase
import org.junit.Assert
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`

class DanaRsMessageHashTableTest : DanaRSTestBase() {

    @Mock lateinit var constraintChecker: Constraints

    private val packetInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is DanaRSPacket) {
                it.aapsLogger = aapsLogger
                it.dateUtil = dateUtil
            }
            if (it is DanaRSPacketBolusSetStepBolusStart) {
                it.constraintChecker = constraintChecker
            }
            if (it is DanaRSPacketBolusSetStepBolusStart) {
                it.danaPump = danaPump
            }
            if (it is DanaRSPacketAPSHistoryEvents) {
                it.danaPump = danaPump
            }
        }
    }

    @Test
    fun runTest() {
        `when`(constraintChecker.applyBolusConstraints(anyObject())).thenReturn(Constraint(0.0))

        val danaRSMessageHashTable = DanaRSMessageHashTable(packetInjector)
        val forTesting: DanaRSPacket = DanaRSPacketAPSSetEventHistory(packetInjector, DanaPump.HistoryEntry.CARBS.value, 0, 0, 0)
        val testPacket: DanaRSPacket = danaRSMessageHashTable.findMessage(forTesting.command)
        Assert.assertEquals(BleEncryption.DANAR_PACKET__OPCODE__APS_SET_EVENT_HISTORY.toLong(), testPacket.opCode.toLong())
    }
}