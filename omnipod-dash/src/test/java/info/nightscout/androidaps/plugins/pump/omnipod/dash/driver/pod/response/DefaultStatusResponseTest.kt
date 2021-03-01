package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.response

import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.DeliveryStatus
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.PodStatus
import org.apache.commons.codec.DecoderException
import org.apache.commons.codec.binary.Hex
import org.junit.Assert
import org.junit.Test

class DefaultStatusResponseTest {

    @Test @Throws(DecoderException::class) fun testValidResponse() {
        val encoded = Hex.decodeHex("1D1800A02800000463FF")
        val response = DefaultStatusResponse(encoded)
        Assert.assertArrayEquals(encoded, response.encoded)
        Assert.assertNotSame(encoded, response.encoded)
        Assert.assertEquals(ResponseType.DEFAULT_STATUS_RESPONSE, response.responseType)
        Assert.assertEquals(ResponseType.DEFAULT_STATUS_RESPONSE.value, response.messageType)
        Assert.assertEquals(DeliveryStatus.BASAL_ACTIVE, response.deliveryStatus)
        Assert.assertEquals(PodStatus.RUNNING_ABOVE_MIN_VOLUME, response.podStatus)
        Assert.assertEquals(320.toShort(), response.totalPulsesDelivered)
        Assert.assertEquals(5.toShort(), response.sequenceNumberOfLastProgrammingCommand)
        Assert.assertEquals(0.toShort(), response.bolusPulsesRemaining)
        Assert.assertEquals(0, response.activeAlerts.size)
        Assert.assertEquals(280.toShort(), response.minutesSinceActivation)
        Assert.assertEquals(1023.toShort(), response.reservoirPulsesRemaining)
    }
}