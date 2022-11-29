package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.response

import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.AlertType
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.DeliveryStatus
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.PodStatus
import org.apache.commons.codec.DecoderException
import org.apache.commons.codec.binary.Hex
import org.junit.Assert
import org.junit.jupiter.api.Test

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

    /**
     * response (hex) 1D1905281000004387D3039A
     Status response: 29
     Pod status: RUNNING_BELOW_MIN_VOLUME
     Basal active: true
     Temp Basal active: false
     Immediate bolus active: false
     Extended bolus active: false
     Bolus pulses remaining: 0
     sequence number of last programing command: 2
     Total full pulses delivered: 2640
     Full reservoir pulses remaining: 979
     Time since activation: 4321
     Alert 1 is InActive
     Alert 2 is InActive
     Alert 3 is InActive
     Alert 4 is InActive
     Alert 5 is InActive
     Alert 6 is InActive
     Alert 7 is InActive
     Occlusion alert active false
     */
    @Test @Throws(DecoderException::class) fun testValidResponseBelowMin() {
        val encoded = Hex.decodeHex("1D1905281000004387D3039A")
        val response = DefaultStatusResponse(encoded)
        Assert.assertArrayEquals(encoded, response.encoded)
        Assert.assertNotSame(encoded, response.encoded)
        Assert.assertEquals(ResponseType.DEFAULT_STATUS_RESPONSE, response.responseType)
        Assert.assertEquals(ResponseType.DEFAULT_STATUS_RESPONSE.value, response.messageType)
        Assert.assertEquals(DeliveryStatus.BASAL_ACTIVE, response.deliveryStatus)
        Assert.assertEquals(PodStatus.RUNNING_BELOW_MIN_VOLUME, response.podStatus)
        Assert.assertEquals(2.toShort(), response.sequenceNumberOfLastProgrammingCommand)
        Assert.assertEquals(0.toShort(), response.bolusPulsesRemaining)
        Assert.assertEquals(0, response.activeAlerts.size)
        Assert.assertEquals(4321.toShort(), response.minutesSinceActivation)
        Assert.assertEquals(979.toShort(), response.reservoirPulsesRemaining)
        Assert.assertEquals(2640.toShort(), response.totalPulsesDelivered)
    }

    /**
     * response (hex) 1D180519C00E0039A7FF8085
     Status response: 29
     Pod status: RUNNING_ABOVE_MIN_VOLUME
     Basal active: true
     Temp Basal active: false
     Immediate bolus active: false
     Extended bolus active: false
     Bolus pulses remaining: 14
     sequence number of last programing command: 8
     Total full pulses delivered: 2611
     Full reservoir pulses remaining: 1023
     Time since activation: 3689
     Alert 1 is InActive
     Alert 2 is InActive
     Alert 3 is InActive
     Alert 4 is InActive
     Alert 5 is InActive
     Alert 6 is InActive
     Alert 7 is InActive
     Occlusion alert active false
     */
    @Test @Throws(DecoderException::class) fun testValidResponseBolusPulsesRemaining() {
        val encoded = Hex.decodeHex("1D180519C00E0039A7FF8085")
        val response = DefaultStatusResponse(encoded)
        Assert.assertArrayEquals(encoded, response.encoded)
        Assert.assertNotSame(encoded, response.encoded)
        Assert.assertEquals(ResponseType.DEFAULT_STATUS_RESPONSE, response.responseType)
        Assert.assertEquals(ResponseType.DEFAULT_STATUS_RESPONSE.value, response.messageType)
        Assert.assertEquals(DeliveryStatus.BASAL_ACTIVE, response.deliveryStatus)
        Assert.assertEquals(PodStatus.RUNNING_ABOVE_MIN_VOLUME, response.podStatus)
        Assert.assertEquals(8.toShort(), response.sequenceNumberOfLastProgrammingCommand)
        Assert.assertEquals(14.toShort(), response.bolusPulsesRemaining)
        Assert.assertEquals(0, response.activeAlerts.size)
        Assert.assertEquals(3689.toShort(), response.minutesSinceActivation)
        Assert.assertEquals(1023.toShort(), response.reservoirPulsesRemaining)
        Assert.assertEquals(2611.toShort(), response.totalPulsesDelivered)
    }

    /** response (hex) 1D990714201F0042ED8801DE
     Status response: 29
     Pod status: RUNNING_BELOW_MIN_VOLUME
     Basal active: true
     Temp Basal active: false
     Immediate bolus active: false
     Extended bolus active: true
     Bolus pulses remaining: 31
     sequence number of last programing command: 4
     Total full pulses delivered: 3624
     Full reservoir pulses remaining: 392
     Time since activation: 4283
     */
    @Test @Throws(DecoderException::class) fun testValidResponseReservoirPulsesRemaining() {
        val encoded = Hex.decodeHex("1D990714201F0042ED8801DE")
        val response = DefaultStatusResponse(encoded)
        Assert.assertArrayEquals(encoded, response.encoded)
        Assert.assertNotSame(encoded, response.encoded)
        Assert.assertEquals(ResponseType.DEFAULT_STATUS_RESPONSE, response.responseType)
        Assert.assertEquals(ResponseType.DEFAULT_STATUS_RESPONSE.value, response.messageType)
        Assert.assertEquals(DeliveryStatus.UNKNOWN, response.deliveryStatus) // Extended bolus active
        Assert.assertEquals(PodStatus.RUNNING_BELOW_MIN_VOLUME, response.podStatus)
        Assert.assertEquals(4.toShort(), response.sequenceNumberOfLastProgrammingCommand)
        Assert.assertEquals(31.toShort(), response.bolusPulsesRemaining)
        Assert.assertEquals(0, response.activeAlerts.size)
        Assert.assertEquals(4283.toShort(), response.minutesSinceActivation)
        Assert.assertEquals(392.toShort(), response.reservoirPulsesRemaining)
        Assert.assertEquals(3624.toShort(), response.totalPulsesDelivered)
    }

    /** response (hex) 1d68002601f400002bff0368
     Status response: 29
     Pod status: RUNNING_BELOW_MIN_VOLUME
     Basal active: true
     Temp Basal active: false
     Immediate bolus active: false
     Extended bolus active: true
     Bolus pulses remaining: 31
     sequence number of last programing command: 4
     Total full pulses delivered: 3624
     Full reservoir pulses remaining: 392
     Time since activation: 4283
     */
    @Test @Throws(DecoderException::class) fun testValidResponseBolusPulsesRemaining3() {
        val encoded = Hex.decodeHex("1d68002601f400002bff0368")
        val response = DefaultStatusResponse(encoded)
        Assert.assertArrayEquals(encoded, response.encoded)
        Assert.assertNotSame(encoded, response.encoded)
        Assert.assertEquals(500.toShort(), response.bolusPulsesRemaining)
        Assert.assertEquals(0, response.activeAlerts.size)
    }
    /** response (hex) 1d28002e91e400002fff8256
     Status response: 29
     Pod status: RUNNING_BELOW_MIN_VOLUME
     Basal active: true
     Temp Basal active: false
     Immediate bolus active: false
     Extended bolus active: true
     Bolus pulses remaining: 31
     sequence number of last programing command: 4
     Total full pulses delivered: 3624
     Full reservoir pulses remaining: 392
     Time since activation: 4283
     */
    @Test @Throws(DecoderException::class) fun testValidResponseBolusPulsesRemaining4() {
        val encoded = Hex.decodeHex("1d28002e91e400002fff8256")
        val response = DefaultStatusResponse(encoded)
        Assert.assertArrayEquals(encoded, response.encoded)
        Assert.assertNotSame(encoded, response.encoded)
        Assert.assertEquals(484.toShort(), response.bolusPulsesRemaining)
        Assert.assertEquals(0, response.activeAlerts.size)
    }

    /*
    1D980559C820404393FF83AA
    Pod status: RUNNING_ABOVE_MIN_VOLUME
    Basal active: true
    Temp Basal active: false
    Immediate bolus active: false
    Extended bolus active: true
    Bolus pulses remaining: 32
    sequence number of last programing command: 9
    Total full pulses delivered: 2739
    Full reservoir pulses remaining: 1023
    Time since activation: 4324
    Alert 1 is InActive
    Alert 2 is InActive
    Alert 3 is InActive
    Alert 4 is InActive
    Alert 5 is InActive
    Alert 6 is InActive
    Alert 7 is Active
    Occlusion alert active false
    */
    @Test @Throws(DecoderException::class) fun testValidResponseActiveAlert1() {
        val encoded = Hex.decodeHex("1D980559C820404393FF83AA")
        val response = DefaultStatusResponse(encoded)
        Assert.assertArrayEquals(encoded, response.encoded)
        Assert.assertNotSame(encoded, response.encoded)
        Assert.assertEquals(ResponseType.DEFAULT_STATUS_RESPONSE, response.responseType)
        Assert.assertEquals(ResponseType.DEFAULT_STATUS_RESPONSE.value, response.messageType)
        Assert.assertEquals(DeliveryStatus.UNKNOWN, response.deliveryStatus)
        Assert.assertEquals(PodStatus.RUNNING_ABOVE_MIN_VOLUME, response.podStatus)
        Assert.assertEquals(9.toShort(), response.sequenceNumberOfLastProgrammingCommand)
        Assert.assertEquals(32.toShort(), response.bolusPulsesRemaining)
        Assert.assertEquals(1, response.activeAlerts.size)
        Assert.assertEquals(4324.toShort(), response.minutesSinceActivation)
        Assert.assertEquals(1023.toShort(), response.reservoirPulsesRemaining)
        Assert.assertEquals(2739.toShort(), response.totalPulsesDelivered)
        Assert.assertEquals(true, response.activeAlerts.contains(AlertType.EXPIRATION))
    }
}
