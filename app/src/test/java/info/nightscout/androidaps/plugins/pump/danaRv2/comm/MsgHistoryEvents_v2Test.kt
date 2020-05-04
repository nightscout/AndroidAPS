package info.nightscout.androidaps.plugins.pump.danaRv2.comm

import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.pump.common.bolusInfo.DetailedBolusInfoStorage
import info.nightscout.androidaps.plugins.pump.danaR.comm.DanaRTestBase
import info.nightscout.androidaps.plugins.pump.danaRv2.DanaRv2Plugin
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin
import info.nightscout.androidaps.utils.resources.ResourceHelper
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.powermock.core.classloader.annotations.PrepareForTest
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
@PrepareForTest(DetailedBolusInfoStorage::class)
class MsgHistoryEvents_v2Test : DanaRTestBase() {

    @Mock lateinit var resourceHelper: ResourceHelper
    @Mock lateinit var detailedBolusInfoStorage: DetailedBolusInfoStorage
    @Mock lateinit var danaRv2Plugin: DanaRv2Plugin
    @Mock lateinit var treatmentsPlugin: TreatmentsPlugin

    @Test @Throws(Exception::class) fun runTest() {
        var packet = MsgHistoryEvents_v2(aapsLogger, resourceHelper, detailedBolusInfoStorage, danaRv2Plugin, RxBusWrapper(), treatmentsPlugin, injector, 0)

        // test message decoding
        val array = ByteArray(100)

        putByteToArray(array, 0, 0xFF.toByte())
        packet.handleMessage(array)
        Assert.assertEquals(true, danaRv2Plugin.eventsLoadingDone)
        // passing an bigger number
        putByteToArray(array, 0, 0x01.toByte())
        packet.handleMessage(array)
        Assert.assertEquals(false, danaRv2Plugin.eventsLoadingDone)
    }
}