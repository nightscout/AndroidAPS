package app.aaps.pump.omnipod.dash.driver.pod.response

import app.aaps.pump.omnipod.dash.driver.pod.definition.AlertType
import app.aaps.pump.omnipod.dash.driver.pod.definition.DeliveryStatus
import app.aaps.pump.omnipod.dash.driver.pod.definition.PodStatus
import com.google.common.truth.Truth.assertThat
import org.apache.commons.codec.binary.Hex
import org.junit.jupiter.api.Test

class DefaultStatusResponseTest {

    @Test fun testValidResponse() {
        val encoded = Hex.decodeHex("1D1800A02800000463FF")
        val response = DefaultStatusResponse(encoded)
        assertThat(response.encoded).asList().containsExactlyElementsIn(encoded.asList())
        assertThat(response.encoded).isNotSameInstanceAs(encoded)
        assertThat(response.responseType).isEqualTo(ResponseType.DEFAULT_STATUS_RESPONSE)
        assertThat(response.messageType).isEqualTo(ResponseType.DEFAULT_STATUS_RESPONSE.value)
        assertThat(response.deliveryStatus).isEqualTo(DeliveryStatus.BASAL_ACTIVE)
        assertThat(response.podStatus).isEqualTo(PodStatus.RUNNING_ABOVE_MIN_VOLUME)
        assertThat(response.totalPulsesDelivered).isEqualTo(320.toShort())
        assertThat(response.sequenceNumberOfLastProgrammingCommand).isEqualTo(5.toShort())
        assertThat(response.bolusPulsesRemaining).isEqualTo(0.toShort())
        assertThat(response.activeAlerts).isEmpty()
        assertThat(response.minutesSinceActivation).isEqualTo(280.toShort())
        assertThat(response.reservoirPulsesRemaining).isEqualTo(1023.toShort())
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
    @Test fun testValidResponseBelowMin() {
        val encoded = Hex.decodeHex("1D1905281000004387D3039A")
        val response = DefaultStatusResponse(encoded)
        assertThat(response.encoded).asList().containsExactlyElementsIn(encoded.asList()).inOrder()
        assertThat(response.encoded).isNotSameInstanceAs(encoded)
        assertThat(response.responseType).isEqualTo(ResponseType.DEFAULT_STATUS_RESPONSE)
        assertThat(response.messageType).isEqualTo(ResponseType.DEFAULT_STATUS_RESPONSE.value)
        assertThat(response.deliveryStatus).isEqualTo(DeliveryStatus.BASAL_ACTIVE)
        assertThat(response.podStatus).isEqualTo(PodStatus.RUNNING_BELOW_MIN_VOLUME)
        assertThat(response.sequenceNumberOfLastProgrammingCommand).isEqualTo(2.toShort())
        assertThat(response.bolusPulsesRemaining).isEqualTo(0.toShort())
        assertThat(response.activeAlerts).isEmpty()
        assertThat(response.minutesSinceActivation).isEqualTo(4321.toShort())
        assertThat(response.reservoirPulsesRemaining).isEqualTo(979.toShort())
        assertThat(response.totalPulsesDelivered).isEqualTo(2640.toShort())
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
    @Test fun testValidResponseBolusPulsesRemaining() {
        val encoded = Hex.decodeHex("1D180519C00E0039A7FF8085")
        val response = DefaultStatusResponse(encoded)
        assertThat(response.encoded).asList().containsExactlyElementsIn(encoded.asList()).inOrder()
        assertThat(response.encoded).isNotSameInstanceAs(encoded)
        assertThat(response.responseType).isEqualTo(ResponseType.DEFAULT_STATUS_RESPONSE)
        assertThat(response.messageType).isEqualTo(ResponseType.DEFAULT_STATUS_RESPONSE.value)
        assertThat(response.deliveryStatus).isEqualTo(DeliveryStatus.BASAL_ACTIVE)
        assertThat(response.podStatus).isEqualTo(PodStatus.RUNNING_ABOVE_MIN_VOLUME)
        assertThat(response.sequenceNumberOfLastProgrammingCommand).isEqualTo(8.toShort())
        assertThat(response.bolusPulsesRemaining).isEqualTo(14.toShort())
        assertThat(response.activeAlerts).isEmpty()
        assertThat(response.minutesSinceActivation).isEqualTo(3689.toShort())
        assertThat(response.reservoirPulsesRemaining).isEqualTo(1023.toShort())
        assertThat(response.totalPulsesDelivered).isEqualTo(2611.toShort())
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
    @Test fun testValidResponseReservoirPulsesRemaining() {
        val encoded = Hex.decodeHex("1D990714201F0042ED8801DE")
        val response = DefaultStatusResponse(encoded)
        assertThat(response.encoded).asList().containsExactlyElementsIn(encoded.asList()).inOrder()
        assertThat(response.encoded).isNotSameInstanceAs(encoded)
        assertThat(response.responseType).isEqualTo(ResponseType.DEFAULT_STATUS_RESPONSE)
        assertThat(response.messageType).isEqualTo(ResponseType.DEFAULT_STATUS_RESPONSE.value)
        assertThat(response.deliveryStatus).isEqualTo(DeliveryStatus.UNKNOWN) // Extended bolus active
        assertThat(response.podStatus).isEqualTo(PodStatus.RUNNING_BELOW_MIN_VOLUME)
        assertThat(response.sequenceNumberOfLastProgrammingCommand).isEqualTo(4.toShort())
        assertThat(response.bolusPulsesRemaining).isEqualTo(31.toShort())
        assertThat(response.activeAlerts).isEmpty()
        assertThat(response.minutesSinceActivation).isEqualTo(4283.toShort())
        assertThat(response.reservoirPulsesRemaining).isEqualTo(392.toShort())
        assertThat(response.totalPulsesDelivered).isEqualTo(3624.toShort())
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
    @Test fun testValidResponseBolusPulsesRemaining3() {
        val encoded = Hex.decodeHex("1d68002601f400002bff0368")
        val response = DefaultStatusResponse(encoded)
        assertThat(response.encoded).asList().containsExactlyElementsIn(encoded.asList()).inOrder()
        assertThat(response.encoded).isNotSameInstanceAs(encoded)
        assertThat(response.bolusPulsesRemaining).isEqualTo(500.toShort())
        assertThat(response.activeAlerts).isEmpty()
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
    @Test fun testValidResponseBolusPulsesRemaining4() {
        val encoded = Hex.decodeHex("1d28002e91e400002fff8256")
        val response = DefaultStatusResponse(encoded)
        assertThat(response.encoded).asList().containsExactlyElementsIn(encoded.asList()).inOrder()
        assertThat(response.encoded).isNotSameInstanceAs(encoded)
        assertThat(response.bolusPulsesRemaining).isEqualTo(484.toShort())
        assertThat(response.activeAlerts).isEmpty()
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
    @Test fun testValidResponseActiveAlert1() {
        val encoded = Hex.decodeHex("1D980559C820404393FF83AA")
        val response = DefaultStatusResponse(encoded)
        assertThat(response.encoded).asList().containsExactlyElementsIn(encoded.asList()).inOrder()
        assertThat(response.encoded).isNotSameInstanceAs(encoded)
        assertThat(response.responseType).isEqualTo(ResponseType.DEFAULT_STATUS_RESPONSE)
        assertThat(response.messageType).isEqualTo(ResponseType.DEFAULT_STATUS_RESPONSE.value)
        assertThat(response.deliveryStatus).isEqualTo(DeliveryStatus.UNKNOWN)
        assertThat(response.podStatus).isEqualTo(PodStatus.RUNNING_ABOVE_MIN_VOLUME)
        assertThat(response.sequenceNumberOfLastProgrammingCommand).isEqualTo(9.toShort())
        assertThat(response.bolusPulsesRemaining).isEqualTo(32.toShort())
        assertThat(response.activeAlerts).hasSize(1)
        assertThat(response.minutesSinceActivation).isEqualTo(4324.toShort())
        assertThat(response.reservoirPulsesRemaining).isEqualTo(1023.toShort())
        assertThat(response.totalPulsesDelivered).isEqualTo(2739.toShort())
        assertThat(response.activeAlerts.contains(AlertType.EXPIRATION)).isTrue()
    }
}
