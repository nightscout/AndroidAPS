package app.aaps.pump.danars.comm

import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.objects.constraints.ConstraintObject
import app.aaps.pump.dana.DanaPump
import app.aaps.pump.danars.DanaRSTestBase
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.danars.encryption.BleEncryption
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`

class DanaRsMessageHashTableTest : DanaRSTestBase() {

    @Mock lateinit var constraintChecker: ConstraintsChecker

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
        `when`(constraintChecker.applyBolusConstraints(anyObject())).thenReturn(ConstraintObject(0.0, aapsLogger))

        val danaRSMessageHashTable = DanaRSMessageHashTable(packetInjector)
        val forTesting: DanaRSPacket = DanaRSPacketAPSSetEventHistory(packetInjector, DanaPump.HistoryEntry.CARBS.value, 0, 0, 0)
        val testPacket: DanaRSPacket = danaRSMessageHashTable.findMessage(forTesting.command)
        Assertions.assertEquals(BleEncryption.DANAR_PACKET__OPCODE__APS_SET_EVENT_HISTORY.toLong(), testPacket.opCode.toLong())
    }
}