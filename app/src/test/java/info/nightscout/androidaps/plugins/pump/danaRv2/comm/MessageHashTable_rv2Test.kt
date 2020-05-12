package info.nightscout.androidaps.plugins.pump.danaRv2.comm

import info.nightscout.androidaps.danaRv2.comm.MessageHashTableRv2
import info.nightscout.androidaps.danaRv2.comm.MsgStatusAPS_v2
import info.nightscout.androidaps.danar.comm.MessageBase
import info.nightscout.androidaps.interfaces.Constraint
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin
import info.nightscout.androidaps.plugins.configBuilder.ConstraintChecker
import info.nightscout.androidaps.plugins.pump.common.bolusInfo.DetailedBolusInfoStorage
import info.nightscout.androidaps.plugins.pump.danaR.comm.DanaRTestBase
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.`when`
import org.powermock.core.classloader.annotations.PrepareForTest
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
@PrepareForTest(ConstraintChecker::class, DetailedBolusInfoStorage::class, ConfigBuilderPlugin::class)
class MessageHashTable_rv2Test : DanaRTestBase() {

    @Test
    fun runTest() {
        `when`(constraintChecker.applyBolusConstraints(anyObject())).thenReturn(Constraint(0.0))
        val messageHashTableRv2 = MessageHashTableRv2(injector)
        val forTesting: MessageBase = MsgStatusAPS_v2(injector)
        val testPacket: MessageBase = messageHashTableRv2.findMessage(forTesting.command)
        Assert.assertEquals(0xE001, testPacket.command.toLong())
        // try putting another command
        val testMessage = MessageBase(injector)
        testMessage.SetCommand(0xE005)
        messageHashTableRv2.put(testMessage)
        Assert.assertEquals(0xE005, messageHashTableRv2.findMessage(0xE005).command.toLong())
    }
}