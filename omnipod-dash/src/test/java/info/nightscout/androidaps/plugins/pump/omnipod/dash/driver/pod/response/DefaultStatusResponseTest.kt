package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.response

import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.DeliveryStatus
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.PodStatus
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.response.ResponseType
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
        Assert.assertEquals(ResponseType.DEFAULT_STATUS_RESPONSE.value, response.getMessageType())
        Assert.assertEquals(DeliveryStatus.BASAL_ACTIVE, response.getDeliveryStatus())
        Assert.assertEquals(PodStatus.RUNNING_ABOVE_MIN_VOLUME, response.getPodStatus())
        Assert.assertEquals(320.toShort(), response.getTotalPulsesDelivered())
        Assert.assertEquals(5.toShort(), response.getSequenceNumberOfLastProgrammingCommand())
        Assert.assertEquals(0.toShort(), response.getBolusPulsesRemaining())
        Assert.assertFalse(response.isOcclusionAlertActive())
        Assert.assertFalse(response.isAlert1Active())
        Assert.assertFalse(response.isAlert2Active())
        Assert.assertFalse(response.isAlert3Active())
        Assert.assertFalse(response.isAlert4Active())
        Assert.assertFalse(response.isAlert5Active())
        Assert.assertFalse(response.isAlert6Active())
        Assert.assertFalse(response.isAlert7Active())
        Assert.assertEquals(280.toShort(), response.getMinutesSinceActivation())
        Assert.assertEquals(1023.toShort(), response.getReservoirPulsesRemaining())
    }
}