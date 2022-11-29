package info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.communication.message.response.podinfo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.joda.time.Duration;
import org.junit.jupiter.api.Test;

import info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.definition.DeliveryStatus;
import info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.definition.ErrorEventInfo;
import info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.definition.FaultEventCode;
import info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.definition.PodProgressStatus;
import info.nightscout.pump.core.utils.ByteUtil;

// From https://github.com/ps2/rileylink_ios/blob/omnipod-testing/OmniKitTests/PodInfoTests.swift
public class PodInfoDetailedStatusTest {
    @Test
    public void testPodInfoFaultEventNoFaultAlerts() {
        PodInfoDetailedStatus podInfoDetailedStatus = new PodInfoDetailedStatus(ByteUtil.fromHexString("02080100000a003800000003ff008700000095ff0000"));

        assertEquals(PodProgressStatus.ABOVE_FIFTY_UNITS, podInfoDetailedStatus.getPodProgressStatus());
        assertEquals(DeliveryStatus.NORMAL, podInfoDetailedStatus.getDeliveryStatus());
        assertEquals(0, podInfoDetailedStatus.getBolusNotDelivered(), 0.000001);
        assertEquals(0x0a, podInfoDetailedStatus.getPodMessageCounter());
        assertNull(podInfoDetailedStatus.getFaultEventCode());
        assertTrue(Duration.ZERO.isEqual(podInfoDetailedStatus.getFaultEventTime()));
        assertNull(podInfoDetailedStatus.getReservoirLevel());
        assertTrue(Duration.standardSeconds(8100).isEqual(podInfoDetailedStatus.getTimeActive()));
        assertEquals(0, podInfoDetailedStatus.getUnacknowledgedAlerts().getRawValue());
        assertFalse(podInfoDetailedStatus.isFaultAccessingTables());
        ErrorEventInfo errorEventInfo = podInfoDetailedStatus.getErrorEventInfo();
        assertNull(errorEventInfo);
        assertNull(podInfoDetailedStatus.getPreviousPodProgressStatus());
        assertEquals(2, podInfoDetailedStatus.getReceiverLowGain());
        assertEquals(21, podInfoDetailedStatus.getRadioRSSI());
    }

    @Test
    public void testPodInfoFaultEventDeliveryErrorDuringPriming() {
        PodInfoDetailedStatus podInfoDetailedStatus = new PodInfoDetailedStatus(ByteUtil.fromHexString("020f0000000900345c000103ff0001000005ae056029"));

        assertEquals(PodProgressStatus.INACTIVE, podInfoDetailedStatus.getPodProgressStatus());
        assertEquals(DeliveryStatus.SUSPENDED, podInfoDetailedStatus.getDeliveryStatus());
        assertEquals(0, podInfoDetailedStatus.getBolusNotDelivered(), 0.000001);
        assertEquals(0x09, podInfoDetailedStatus.getPodMessageCounter());
        assertEquals(FaultEventCode.PRIME_OPEN_COUNT_TOO_LOW, podInfoDetailedStatus.getFaultEventCode());
        assertTrue(Duration.standardSeconds(60).isEqual(podInfoDetailedStatus.getFaultEventTime()));
        assertNull(podInfoDetailedStatus.getReservoirLevel());
        assertTrue(Duration.standardSeconds(60).isEqual(podInfoDetailedStatus.getTimeActive()));
        assertEquals(0, podInfoDetailedStatus.getUnacknowledgedAlerts().getRawValue());
        assertFalse(podInfoDetailedStatus.isFaultAccessingTables());
        ErrorEventInfo errorEventInfo = podInfoDetailedStatus.getErrorEventInfo();
        assertFalse(errorEventInfo.isInsulinStateTableCorruption());
        assertEquals(0x00, errorEventInfo.getInternalVariable());
        assertFalse(errorEventInfo.isImmediateBolusInProgress());
        assertEquals(PodProgressStatus.PRIMING_COMPLETED, errorEventInfo.getPodProgressStatus());
        assertEquals(PodProgressStatus.PRIMING_COMPLETED, podInfoDetailedStatus.getPreviousPodProgressStatus());
        assertEquals(2, podInfoDetailedStatus.getReceiverLowGain());
        assertEquals(46, podInfoDetailedStatus.getRadioRSSI());
    }

    @Test
    public void testPodInfoFaultEventErrorShuttingDown() {
        PodInfoDetailedStatus podInfoDetailedStatus = new PodInfoDetailedStatus(ByteUtil.fromHexString("020d0000000407f28609ff03ff0a0200000823080000"));

        assertEquals(PodProgressStatus.FAULT_EVENT_OCCURRED, podInfoDetailedStatus.getPodProgressStatus());
        assertEquals(DeliveryStatus.SUSPENDED, podInfoDetailedStatus.getDeliveryStatus());
        assertEquals(2034, podInfoDetailedStatus.getTicksDelivered());
        assertEquals(101.7, podInfoDetailedStatus.getInsulinDelivered(), 0.000001);
        assertEquals(0, podInfoDetailedStatus.getBolusNotDelivered(), 0.000001);
        assertEquals(0x04, podInfoDetailedStatus.getPodMessageCounter());
        assertEquals(FaultEventCode.BASAL_OVER_INFUSION_PULSE, podInfoDetailedStatus.getFaultEventCode());
        assertTrue(Duration.standardMinutes(2559).isEqual(podInfoDetailedStatus.getFaultEventTime()));
        assertNull(podInfoDetailedStatus.getReservoirLevel());
        assertEquals(0, podInfoDetailedStatus.getUnacknowledgedAlerts().getRawValue());
        assertFalse(podInfoDetailedStatus.isFaultAccessingTables());
        ErrorEventInfo errorEventInfo = podInfoDetailedStatus.getErrorEventInfo();
        assertFalse(errorEventInfo.isInsulinStateTableCorruption());
        assertEquals(0x00, errorEventInfo.getInternalVariable());
        assertFalse(errorEventInfo.isImmediateBolusInProgress());
        assertEquals(PodProgressStatus.ABOVE_FIFTY_UNITS, errorEventInfo.getPodProgressStatus());
        assertEquals(PodProgressStatus.ABOVE_FIFTY_UNITS, podInfoDetailedStatus.getPreviousPodProgressStatus());
        assertEquals(0, podInfoDetailedStatus.getReceiverLowGain());
        assertEquals(35, podInfoDetailedStatus.getRadioRSSI());
    }

    @Test
    public void testPodInfoFaultEventInsulinNotDelivered() {
        PodInfoDetailedStatus podInfoDetailedStatus = new PodInfoDetailedStatus(ByteUtil.fromHexString("020f0000010200ec6a026803ff026b000028a7082023"));

        assertEquals(PodProgressStatus.INACTIVE, podInfoDetailedStatus.getPodProgressStatus());
        assertEquals(DeliveryStatus.SUSPENDED, podInfoDetailedStatus.getDeliveryStatus());
        assertEquals(236, podInfoDetailedStatus.getTicksDelivered());
        assertEquals(11.8, podInfoDetailedStatus.getInsulinDelivered(), 0.000001);
        assertEquals(0.05, podInfoDetailedStatus.getBolusNotDelivered(), 0.000001);
        assertEquals(0x02, podInfoDetailedStatus.getPodMessageCounter());
        assertEquals(FaultEventCode.OCCLUSION_CHECK_ABOVE_THRESHOLD, podInfoDetailedStatus.getFaultEventCode());
        assertTrue(Duration.standardMinutes(616).isEqual(podInfoDetailedStatus.getFaultEventTime()));
        assertNull(podInfoDetailedStatus.getReservoirLevel());
        assertEquals(0, podInfoDetailedStatus.getUnacknowledgedAlerts().getRawValue());
        assertFalse(podInfoDetailedStatus.isFaultAccessingTables());
        ErrorEventInfo errorEventInfo = podInfoDetailedStatus.getErrorEventInfo();
        assertFalse(errorEventInfo.isInsulinStateTableCorruption());
        assertEquals(0x01, errorEventInfo.getInternalVariable());
        assertFalse(errorEventInfo.isImmediateBolusInProgress());
        assertEquals(PodProgressStatus.ABOVE_FIFTY_UNITS, errorEventInfo.getPodProgressStatus());
        assertEquals(PodProgressStatus.ABOVE_FIFTY_UNITS, podInfoDetailedStatus.getPreviousPodProgressStatus());
        assertEquals(2, podInfoDetailedStatus.getReceiverLowGain());
        assertEquals(39, podInfoDetailedStatus.getRadioRSSI());
    }

    @Test
    public void testPodInfoFaultEventMaxBolusNotDelivered() {
        PodInfoDetailedStatus podInfoDetailedStatus = new PodInfoDetailedStatus(ByteUtil.fromHexString("020f00ffff0200ec6a026803ff026b000028a7082023"));

        assertEquals(PodProgressStatus.INACTIVE, podInfoDetailedStatus.getPodProgressStatus());
        assertEquals(DeliveryStatus.SUSPENDED, podInfoDetailedStatus.getDeliveryStatus());
        assertEquals(236, podInfoDetailedStatus.getTicksDelivered());
        assertEquals(11.8, podInfoDetailedStatus.getInsulinDelivered(), 0.000001);
        assertEquals(3276.75, podInfoDetailedStatus.getBolusNotDelivered(), 0.000001); // Insane and will not happen, but this verifies that we convert it to an unsigned int
        assertEquals(0x02, podInfoDetailedStatus.getPodMessageCounter());
        assertEquals(FaultEventCode.OCCLUSION_CHECK_ABOVE_THRESHOLD, podInfoDetailedStatus.getFaultEventCode());
        assertTrue(Duration.standardMinutes(616).isEqual(podInfoDetailedStatus.getFaultEventTime()));
        assertNull(podInfoDetailedStatus.getReservoirLevel());
        assertEquals(0, podInfoDetailedStatus.getUnacknowledgedAlerts().getRawValue());
        assertFalse(podInfoDetailedStatus.isFaultAccessingTables());
        ErrorEventInfo errorEventInfo = podInfoDetailedStatus.getErrorEventInfo();
        assertFalse(errorEventInfo.isInsulinStateTableCorruption());
        assertEquals(0x01, errorEventInfo.getInternalVariable());
        assertFalse(errorEventInfo.isImmediateBolusInProgress());
        assertEquals(PodProgressStatus.ABOVE_FIFTY_UNITS, errorEventInfo.getPodProgressStatus());
        assertEquals(PodProgressStatus.ABOVE_FIFTY_UNITS, podInfoDetailedStatus.getPreviousPodProgressStatus());
        assertEquals(2, podInfoDetailedStatus.getReceiverLowGain());
        assertEquals(39, podInfoDetailedStatus.getRadioRSSI());
    }

    @Test
    public void testPodInfoFaultEventInsulinStateTableCorruptionFoundDuringErrorLogging() {
        PodInfoDetailedStatus podInfoDetailedStatus = new PodInfoDetailedStatus(ByteUtil.fromHexString("020D00000000000012FFFF03FF00160000879A070000"));

        assertEquals(PodProgressStatus.FAULT_EVENT_OCCURRED, podInfoDetailedStatus.getPodProgressStatus());
        assertEquals(DeliveryStatus.SUSPENDED, podInfoDetailedStatus.getDeliveryStatus());
        assertEquals(0, podInfoDetailedStatus.getBolusNotDelivered(), 0.000001);
        assertEquals(0x00, podInfoDetailedStatus.getPodMessageCounter());
        assertEquals(FaultEventCode.RESET_DUE_TO_LVD, podInfoDetailedStatus.getFaultEventCode());
        assertTrue(Duration.ZERO.isEqual(podInfoDetailedStatus.getFaultEventTime()));
        assertNull(podInfoDetailedStatus.getReservoirLevel());
        assertTrue(Duration.standardSeconds(1320).isEqual(podInfoDetailedStatus.getTimeActive()));
        assertEquals(0, podInfoDetailedStatus.getUnacknowledgedAlerts().getRawValue());
        assertFalse(podInfoDetailedStatus.isFaultAccessingTables());
        ErrorEventInfo errorEventInfo = podInfoDetailedStatus.getErrorEventInfo();
        assertTrue(errorEventInfo.isInsulinStateTableCorruption());
        assertEquals(0x00, errorEventInfo.getInternalVariable());
        assertFalse(errorEventInfo.isImmediateBolusInProgress());
        assertEquals(PodProgressStatus.INSERTING_CANNULA, errorEventInfo.getPodProgressStatus());
        assertEquals(PodProgressStatus.INSERTING_CANNULA, podInfoDetailedStatus.getPreviousPodProgressStatus());
        assertEquals(2, podInfoDetailedStatus.getReceiverLowGain());
        assertEquals(26, podInfoDetailedStatus.getRadioRSSI());
    }
}
