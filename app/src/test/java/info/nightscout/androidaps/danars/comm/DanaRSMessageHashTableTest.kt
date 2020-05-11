package info.nightscout.androidaps.danars.comm

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.interfaces.ActivePluginProvider
import info.nightscout.androidaps.interfaces.Constraint
import info.nightscout.androidaps.plugins.configBuilder.ConstraintChecker
import info.nightscout.androidaps.plugins.pump.common.bolusInfo.DetailedBolusInfoStorage
import info.nightscout.androidaps.danars.encryption.BleEncryption
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.powermock.core.classloader.annotations.PrepareForTest
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
@PrepareForTest(ConstraintChecker::class, DetailedBolusInfoStorage::class)
class DanaRSMessageHashTableTest : DanaRSTestBase() {

    @Mock lateinit var activePlugin: ActivePluginProvider
    @Mock lateinit var constraintChecker: ConstraintChecker
    @Mock lateinit var detailedBolusInfoStorage: DetailedBolusInfoStorage

    private val packetInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is DanaRS_Packet) {
                it.aapsLogger = aapsLogger
                it.dateUtil = dateUtil
            }
            if (it is DanaRS_Packet_Bolus_Set_Step_Bolus_Start) {
                it.constraintChecker = constraintChecker
            }
            if (it is DanaRS_Packet_Bolus_Set_Step_Bolus_Start) {
                it.danaPump = danaPump
            }
            if (it is DanaRS_Packet_APS_History_Events) {
                it.danaPump = danaPump
            }
        }
    }

    @Test
    fun runTest() {
        `when`(constraintChecker.applyBolusConstraints(anyObject())).thenReturn(Constraint(0.0))

        val danaRSMessageHashTable = DanaRSMessageHashTable(packetInjector)
        val forTesting: DanaRS_Packet = DanaRS_Packet_APS_Set_Event_History(packetInjector, info.nightscout.androidaps.dana.DanaPump.CARBS, 0, 0, 0)
        val testPacket: DanaRS_Packet = danaRSMessageHashTable.findMessage(forTesting.command)
        Assert.assertEquals(BleEncryption.DANAR_PACKET__OPCODE__APS_SET_EVENT_HISTORY.toLong(), testPacket.getOpCode().toLong())
    }
}