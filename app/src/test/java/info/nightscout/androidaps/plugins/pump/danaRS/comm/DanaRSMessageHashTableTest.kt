package info.nightscout.androidaps.plugins.pump.danaRS.comm

<<<<<<< HEAD
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
=======
import android.content.Context
import com.cozmo.danar.util.BleCommandUtil
>>>>>>> origin/dev
import info.nightscout.androidaps.interfaces.ActivePluginProvider
import info.nightscout.androidaps.interfaces.Constraint
import info.nightscout.androidaps.plugins.configBuilder.ConstraintChecker
import info.nightscout.androidaps.plugins.pump.common.bolusInfo.DetailedBolusInfoStorage
import info.nightscout.androidaps.plugins.pump.danaR.DanaRPump
<<<<<<< HEAD
import info.nightscout.androidaps.plugins.pump.danaRS.encryption.BleEncryption
=======
import info.nightscout.androidaps.plugins.pump.danaRS.DanaRSPlugin
import info.nightscout.androidaps.utils.DateUtil
>>>>>>> origin/dev
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

<<<<<<< HEAD
=======
    @Mock lateinit var context: Context
    @Mock lateinit var danaRSPlugin: DanaRSPlugin
>>>>>>> origin/dev
    @Mock lateinit var activePlugin: ActivePluginProvider
    @Mock lateinit var constraintChecker: ConstraintChecker
    @Mock lateinit var detailedBolusInfoStorage: DetailedBolusInfoStorage

    private val packetInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is DanaRS_Packet) {
                it.aapsLogger = aapsLogger
            }
            if (it is DanaRS_Packet_Bolus_Set_Step_Bolus_Start) {
                it.constraintChecker = constraintChecker
            }
            if (it is DanaRS_Packet_Bolus_Set_Step_Bolus_Start) {
                it.danaRPump = danaRPump
            }
            if (it is DanaRS_Packet_APS_History_Events) {
                it.danaRPump = danaRPump
            }
        }
    }

    @Test
    fun runTest() {
        `when`(constraintChecker.applyBolusConstraints(anyObject())).thenReturn(Constraint(0.0))

<<<<<<< HEAD
        val danaRSMessageHashTable = DanaRSMessageHashTable(packetInjector)
        val forTesting: DanaRS_Packet = DanaRS_Packet_APS_Set_Event_History(packetInjector, DanaRPump.CARBS, 0, 0, 0)
=======
        val danaRSMessageHashTable = DanaRSMessageHashTable(aapsLogger, rxBus, resourceHelper, danaRPump, danaRSPlugin, activePlugin, constraintChecker, detailedBolusInfoStorage, injector, DateUtil(context, resourceHelper))
        val forTesting: DanaRS_Packet = DanaRS_Packet_APS_Set_Event_History(aapsLogger, dateUtil, DanaRPump.CARBS, 0, 0, 0)
>>>>>>> origin/dev
        val testPacket: DanaRS_Packet = danaRSMessageHashTable.findMessage(forTesting.command)
        Assert.assertEquals(BleEncryption.DANAR_PACKET__OPCODE__APS_SET_EVENT_HISTORY.toLong(), testPacket.getOpCode().toLong())
    }
}