package info.nightscout.androidaps.plugins.pump.omnipod.comm.message.response.podinfo;

import org.joda.time.Duration;
import org.junit.Test;

import info.nightscout.androidaps.plugins.pump.common.utils.ByteUtil;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.DeliveryStatus;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.FaultEventCode;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.LogEventErrorCode;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.PodProgressStatus;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

// From https://github.com/ps2/rileylink_ios/blob/omnipod-testing/OmniKitTests/PodInfoTests.swift
public class PodInfoFaultEventTest {
    @Test
    public void testPodInfoFaultEventNoFaultAlerts() {
        PodInfoFaultEvent podInfoFaultEvent = new PodInfoFaultEvent(ByteUtil.fromHexString("02080100000a003800000003ff008700000095ff0000"));

        assertEquals(PodProgressStatus.ABOVE_FIFTY_UNITS, podInfoFaultEvent.getPodProgressStatus());
        assertEquals(DeliveryStatus.NORMAL, podInfoFaultEvent.getDeliveryStatus());
        assertEquals(0, podInfoFaultEvent.getInsulinNotDelivered(), 0.000001);
        assertEquals(0x0a, podInfoFaultEvent.getPodMessageCounter());
        assertEquals(FaultEventCode.NO_FAULTS, podInfoFaultEvent.getFaultEventCode());
        assertTrue(Duration.ZERO.isEqual(podInfoFaultEvent.getFaultEventTime()));
        assertNull(podInfoFaultEvent.getReservoirLevel());
        assertTrue(Duration.standardSeconds(8100).isEqual(podInfoFaultEvent.getTimeSinceActivation()));
        assertEquals(0, podInfoFaultEvent.getUnacknowledgedAlerts().getRawValue());
        assertFalse(podInfoFaultEvent.isFaultAccessingTables());
        assertEquals(LogEventErrorCode.NONE, podInfoFaultEvent.getLogEventErrorType());
        assertEquals(PodProgressStatus.INACTIVE, podInfoFaultEvent.getPodProgressStatusAtTimeOfFirstLoggedFaultEvent());
        assertEquals(2, podInfoFaultEvent.getReceiverLowGain());
        assertEquals(21, podInfoFaultEvent.getRadioRSSI());
    }

    @Test
    public void testPodInfoFaultEventDeliveryErrorDuringPriming() {
        PodInfoFaultEvent podInfoFaultEvent = new PodInfoFaultEvent(ByteUtil.fromHexString("020f0000000900345c000103ff0001000005ae056029"));

        assertEquals(PodProgressStatus.INACTIVE, podInfoFaultEvent.getPodProgressStatus());
        assertEquals(DeliveryStatus.SUSPENDED, podInfoFaultEvent.getDeliveryStatus());
        assertEquals(0, podInfoFaultEvent.getInsulinNotDelivered(), 0.000001);
        assertEquals(0x09, podInfoFaultEvent.getPodMessageCounter());
        assertEquals(FaultEventCode.PRIME_OPEN_COUNT_TOO_LOW, podInfoFaultEvent.getFaultEventCode());
        assertTrue(Duration.standardSeconds(60).isEqual(podInfoFaultEvent.getFaultEventTime()));
        assertNull(podInfoFaultEvent.getReservoirLevel());
        assertTrue(Duration.standardSeconds(60).isEqual(podInfoFaultEvent.getTimeSinceActivation()));
        assertEquals(0, podInfoFaultEvent.getUnacknowledgedAlerts().getRawValue());
        assertFalse(podInfoFaultEvent.isFaultAccessingTables());
        assertEquals(LogEventErrorCode.NONE, podInfoFaultEvent.getLogEventErrorType());
        assertEquals(PodProgressStatus.PRIMING_COMPLETED, podInfoFaultEvent.getPodProgressStatusAtTimeOfFirstLoggedFaultEvent());
        assertEquals(2, podInfoFaultEvent.getReceiverLowGain());
        assertEquals(46, podInfoFaultEvent.getRadioRSSI());
    }

    @Test
    public void testPodInfoFaultEventErrorShuttingDown() {
        PodInfoFaultEvent podInfoFaultEvent = new PodInfoFaultEvent(ByteUtil.fromHexString("020d0000000407f28609ff03ff0a0200000823080000"));

        assertEquals(PodProgressStatus.FAULT_EVENT_OCCURRED, podInfoFaultEvent.getPodProgressStatus());
        assertEquals(DeliveryStatus.SUSPENDED, podInfoFaultEvent.getDeliveryStatus());
        assertEquals(101.7, podInfoFaultEvent.getTotalInsulinDelivered(), 0.000001);
        assertEquals(0, podInfoFaultEvent.getInsulinNotDelivered(), 0.000001);
        assertEquals(0x04, podInfoFaultEvent.getPodMessageCounter());
        assertEquals(FaultEventCode.BASAL_OVER_INFUSION_PULSE, podInfoFaultEvent.getFaultEventCode());
        assertTrue(Duration.standardMinutes(2559).isEqual(podInfoFaultEvent.getFaultEventTime()));
        assertNull(podInfoFaultEvent.getReservoirLevel());
        assertEquals(0, podInfoFaultEvent.getUnacknowledgedAlerts().getRawValue());
        assertFalse(podInfoFaultEvent.isFaultAccessingTables());
        assertEquals(LogEventErrorCode.NONE, podInfoFaultEvent.getLogEventErrorType());
        assertEquals(PodProgressStatus.ABOVE_FIFTY_UNITS, podInfoFaultEvent.getPodProgressStatusAtTimeOfFirstLoggedFaultEvent());
        assertEquals(0, podInfoFaultEvent.getReceiverLowGain());
        assertEquals(35, podInfoFaultEvent.getRadioRSSI());
    }

    @Test
    public void testPodInfoFaultEventInsulinNotDelivered() {
        PodInfoFaultEvent podInfoFaultEvent = new PodInfoFaultEvent(ByteUtil.fromHexString("020f0000010200ec6a026803ff026b000028a7082023"));

        assertEquals(PodProgressStatus.INACTIVE, podInfoFaultEvent.getPodProgressStatus());
        assertEquals(DeliveryStatus.SUSPENDED, podInfoFaultEvent.getDeliveryStatus());
        assertEquals(11.8, podInfoFaultEvent.getTotalInsulinDelivered(), 0.000001);
        assertEquals(0.05, podInfoFaultEvent.getInsulinNotDelivered(), 0.000001);
        assertEquals(0x02, podInfoFaultEvent.getPodMessageCounter());
        assertEquals(FaultEventCode.OCCLUSION_CHECK_ABOVE_THRESHOLD, podInfoFaultEvent.getFaultEventCode());
        assertTrue(Duration.standardMinutes(616).isEqual(podInfoFaultEvent.getFaultEventTime()));
        assertNull(podInfoFaultEvent.getReservoirLevel());
        assertEquals(0, podInfoFaultEvent.getUnacknowledgedAlerts().getRawValue());
        assertFalse(podInfoFaultEvent.isFaultAccessingTables());
        assertEquals(LogEventErrorCode.INTERNAL_2_BIT_VARIABLE_SET_AND_MANIPULATED_IN_MAIN_LOOP_ROUTINES_2, podInfoFaultEvent.getLogEventErrorType());
        assertEquals(PodProgressStatus.ABOVE_FIFTY_UNITS, podInfoFaultEvent.getPodProgressStatusAtTimeOfFirstLoggedFaultEvent());
        assertEquals(2, podInfoFaultEvent.getReceiverLowGain());
        assertEquals(39, podInfoFaultEvent.getRadioRSSI());
    }

    @Test
    public void testPodInfoFaultEventMaxInsulinNotDelivered() {
        PodInfoFaultEvent podInfoFaultEvent = new PodInfoFaultEvent(ByteUtil.fromHexString("020f00ffff0200ec6a026803ff026b000028a7082023"));

        assertEquals(PodProgressStatus.INACTIVE, podInfoFaultEvent.getPodProgressStatus());
        assertEquals(DeliveryStatus.SUSPENDED, podInfoFaultEvent.getDeliveryStatus());
        assertEquals(11.8, podInfoFaultEvent.getTotalInsulinDelivered(), 0.000001);
        assertEquals(3276.75, podInfoFaultEvent.getInsulinNotDelivered(), 0.000001); // Insane and will not happen, but this verifies that we convert it to an unsigned int
        assertEquals(0x02, podInfoFaultEvent.getPodMessageCounter());
        assertEquals(FaultEventCode.OCCLUSION_CHECK_ABOVE_THRESHOLD, podInfoFaultEvent.getFaultEventCode());
        assertTrue(Duration.standardMinutes(616).isEqual(podInfoFaultEvent.getFaultEventTime()));
        assertNull(podInfoFaultEvent.getReservoirLevel());
        assertEquals(0, podInfoFaultEvent.getUnacknowledgedAlerts().getRawValue());
        assertFalse(podInfoFaultEvent.isFaultAccessingTables());
        assertEquals(LogEventErrorCode.INTERNAL_2_BIT_VARIABLE_SET_AND_MANIPULATED_IN_MAIN_LOOP_ROUTINES_2, podInfoFaultEvent.getLogEventErrorType());
        assertEquals(PodProgressStatus.ABOVE_FIFTY_UNITS, podInfoFaultEvent.getPodProgressStatusAtTimeOfFirstLoggedFaultEvent());
        assertEquals(2, podInfoFaultEvent.getReceiverLowGain());
        assertEquals(39, podInfoFaultEvent.getRadioRSSI());
    }
}
