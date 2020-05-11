package info.nightscout.androidaps.plugins.pump.danaRKorean.comm

import info.nightscout.androidaps.danars.comm.DanaRSTestBase
import info.nightscout.androidaps.interfaces.ActivePluginProvider
import info.nightscout.androidaps.interfaces.CommandQueueProvider
import info.nightscout.androidaps.interfaces.Constraint
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin
import info.nightscout.androidaps.plugins.configBuilder.ConstraintChecker
import info.nightscout.androidaps.plugins.general.nsclient.NSUpload
import info.nightscout.androidaps.plugins.pump.common.bolusInfo.DetailedBolusInfoStorage
import info.nightscout.androidaps.danar.DanaRPlugin
import info.nightscout.androidaps.danaRKorean.DanaRKoreanPlugin
import info.nightscout.androidaps.danaRKorean.comm.MessageHashTableRKorean
import info.nightscout.androidaps.interfaces.DatabaseHelperInterface
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito
import org.powermock.core.classloader.annotations.PrepareForTest
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
@PrepareForTest(ConstraintChecker::class, DetailedBolusInfoStorage::class, ConfigBuilderPlugin::class)
class MessageHashTableRKoreanTest : DanaRSTestBase() {

    @Mock lateinit var constraintChecker: ConstraintChecker
    @Mock lateinit var danaRPlugin: DanaRPlugin
    @Mock lateinit var danaRKoreanPlugin: DanaRKoreanPlugin
    @Mock lateinit var configBuilderPlugin: ConfigBuilderPlugin
    @Mock lateinit var commandQueue: CommandQueueProvider
    @Mock lateinit var activePlugin: ActivePluginProvider
    @Mock lateinit var detailedBolusInfoStorage: DetailedBolusInfoStorage
    @Mock lateinit var nsUpload: NSUpload
    @Mock lateinit var databaseHelper: DatabaseHelperInterface

    @Test fun runTest() {
        Mockito.`when`(constraintChecker.applyBolusConstraints(anyObject())).thenReturn(Constraint(0.0))
        val messageHashTable = MessageHashTableRKorean(aapsLogger, rxBus, resourceHelper, constraintChecker, danaPump, danaRPlugin, danaRKoreanPlugin, configBuilderPlugin, commandQueue, activePlugin, dateUtil, nsUpload, databaseHelper, injector)
        val testMessage = messageHashTable.findMessage(0x41f2)
        Assert.assertEquals("CMD_HISTORY_ALL", testMessage.messageName)
    }
}