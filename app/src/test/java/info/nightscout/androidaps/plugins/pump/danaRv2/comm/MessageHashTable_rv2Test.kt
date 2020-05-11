package info.nightscout.androidaps.plugins.pump.danaRv2.comm

import android.content.Context
import info.nightscout.androidaps.danaRKorean.DanaRKoreanPlugin
import info.nightscout.androidaps.danaRv2.DanaRv2Plugin
import info.nightscout.androidaps.danaRv2.comm.MessageHashTableRv2
import info.nightscout.androidaps.danaRv2.comm.MsgStatusAPS_v2
import info.nightscout.androidaps.danar.DanaRPlugin
import info.nightscout.androidaps.danar.comm.MessageBase
import info.nightscout.androidaps.interfaces.ActivePluginProvider
import info.nightscout.androidaps.interfaces.CommandQueueProvider
import info.nightscout.androidaps.interfaces.Constraint
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin
import info.nightscout.androidaps.plugins.configBuilder.ConstraintChecker
import info.nightscout.androidaps.plugins.general.nsclient.NSUpload
import info.nightscout.androidaps.plugins.pump.common.bolusInfo.DetailedBolusInfoStorage
import info.nightscout.androidaps.plugins.pump.danaR.comm.DanaRTestBase
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.resources.ResourceHelper
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.powermock.core.classloader.annotations.PrepareForTest
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
@PrepareForTest(ConstraintChecker::class, DetailedBolusInfoStorage::class, ConfigBuilderPlugin::class)
class MessageHashTable_rv2Test : DanaRTestBase() {

    @Mock lateinit var context: Context
    @Mock lateinit var rxBus: RxBusWrapper
    @Mock lateinit var resourceHelper: ResourceHelper
    @Mock lateinit var constraintChecker: ConstraintChecker
    @Mock lateinit var danaRPlugin: DanaRPlugin
    @Mock lateinit var danaRKoreanPlugin: DanaRKoreanPlugin
    @Mock lateinit var danaRv2Plugin: DanaRv2Plugin
    @Mock lateinit var configBuilderPlugin: ConfigBuilderPlugin
    @Mock lateinit var commandQueue: CommandQueueProvider
    @Mock lateinit var activePlugin: ActivePluginProvider
    @Mock lateinit var detailedBolusInfoStorage: DetailedBolusInfoStorage
    @Mock lateinit var treatmentsPlugin: TreatmentsPlugin
    @Mock lateinit var nsUpload: NSUpload

    @Test
    fun runTest() {
        `when`(constraintChecker.applyBolusConstraints(anyObject())).thenReturn(Constraint(0.0))
        val messageHashTableRv2 = MessageHashTableRv2(aapsLogger, rxBus, resourceHelper, constraintChecker, danaPump, danaRPlugin, danaRKoreanPlugin, danaRv2Plugin, configBuilderPlugin, commandQueue, activePlugin, detailedBolusInfoStorage, nsUpload, injector, DateUtil(context, resourceHelper), databaseHelper)
        val forTesting: MessageBase = MsgStatusAPS_v2(aapsLogger, danaPump)
        val testPacket: MessageBase = messageHashTableRv2.findMessage(forTesting.command)
        Assert.assertEquals(0xE001, testPacket.command.toLong())
        // try putting another command
        val testMessage = MessageBase()
        testMessage.SetCommand(0xE005)
        messageHashTableRv2.put(testMessage)
        Assert.assertEquals(0xE005, messageHashTableRv2.findMessage(0xE005).command.toLong())
    }
}