package info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.communication.message.response.podinfo;

import org.joda.time.Duration;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.definition.DeliveryStatus;
import info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.definition.ErrorEventInfo;
import info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.definition.FaultEventCode;
import info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.definition.PodProgressStatus;
import info.nightscout.pump.core.utils.ByteUtil;

/**
 * @noinspection SpellCheckingInspection
 */ // From https://github.com/ps2/rileylink_ios/blob/omnipod-testing/OmniKitTests/PodInfoTests.swift
class PodInfoDetailedStatusTest {
    @Test
    void testPodInfoFaultEventNoFaultAlerts() {
        PodInfoDetailedStatus podInfoDetailedStatus = new PodInfoDetailedStatus(ByteUtil.fromHexString("02080100000a003800000003ff008700000095ff0000"));

        Assertions.assertEquals(PodProgressStatus.ABOVE_FIFTY_UNITS, podInfoDetailedStatus.getPodProgressStatus());
        Assertions.assertEquals(DeliveryStatus.NORMAL, podInfoDetailedStatus.getDeliveryStatus());
        Assertions.assertEquals(0, podInfoDetailedStatus.getBolusNotDelivered(), 0.000001);
        Assertions.assertEquals(0x0a, podInfoDetailedStatus.getPodMessageCounter());
        Assertions.assertNull(podInfoDetailedStatus.getFaultEventCode());
        Assertions.assertTrue(Duration.ZERO.isEqual(podInfoDetailedStatus.getFaultEventTime()));
        Assertions.assertNull(podInfoDetailedStatus.getReservoirLevel());
        Assertions.assertTrue(Duration.standardSeconds(8100).isEqual(podInfoDetailedStatus.getTimeActive()));
        Assertions.assertEquals(0, podInfoDetailedStatus.getUnacknowledgedAlerts().getRawValue());
        Assertions.assertFalse(podInfoDetailedStatus.isFaultAccessingTables());
        ErrorEventInfo errorEventInfo = podInfoDetailedStatus.getErrorEventInfo();
        Assertions.assertNull(errorEventInfo);
        Assertions.assertNull(podInfoDetailedStatus.getPreviousPodProgressStatus());
        Assertions.assertEquals(2, podInfoDetailedStatus.getReceiverLowGain());
        Assertions.assertEquals(21, podInfoDetailedStatus.getRadioRSSI());
    }

    @Test
    void testPodInfoFaultEventDeliveryErrorDuringPriming() {
        PodInfoDetailedStatus podInfoDetailedStatus = new PodInfoDetailedStatus(ByteUtil.fromHexString("020f0000000900345c000103ff0001000005ae056029"));

        Assertions.assertEquals(PodProgressStatus.INACTIVE, podInfoDetailedStatus.getPodProgressStatus());
        Assertions.assertEquals(DeliveryStatus.SUSPENDED, podInfoDetailedStatus.getDeliveryStatus());
        Assertions.assertEquals(0, podInfoDetailedStatus.getBolusNotDelivered(), 0.000001);
        Assertions.assertEquals(0x09, podInfoDetailedStatus.getPodMessageCounter());
        Assertions.assertEquals(FaultEventCode.PRIME_OPEN_COUNT_TOO_LOW, podInfoDetailedStatus.getFaultEventCode());
        Assertions.assertTrue(Duration.standardSeconds(60).isEqual(podInfoDetailedStatus.getFaultEventTime()));
        Assertions.assertNull(podInfoDetailedStatus.getReservoirLevel());
        Assertions.assertTrue(Duration.standardSeconds(60).isEqual(podInfoDetailedStatus.getTimeActive()));
        Assertions.assertEquals(0, podInfoDetailedStatus.getUnacknowledgedAlerts().getRawValue());
        Assertions.assertFalse(podInfoDetailedStatus.isFaultAccessingTables());
        ErrorEventInfo errorEventInfo = podInfoDetailedStatus.getErrorEventInfo();
        Assertions.assertFalse(errorEventInfo.isInsulinStateTableCorruption());
        Assertions.assertEquals(0x00, errorEventInfo.getInternalVariable());
        Assertions.assertFalse(errorEventInfo.isImmediateBolusInProgress());
        Assertions.assertEquals(PodProgressStatus.PRIMING_COMPLETED, errorEventInfo.getPodProgressStatus());
        Assertions.assertEquals(PodProgressStatus.PRIMING_COMPLETED, podInfoDetailedStatus.getPreviousPodProgressStatus());
        Assertions.assertEquals(2, podInfoDetailedStatus.getReceiverLowGain());
        Assertions.assertEquals(46, podInfoDetailedStatus.getRadioRSSI());
    }

    @Test
    void testPodInfoFaultEventErrorShuttingDown() {
        PodInfoDetailedStatus podInfoDetailedStatus = new PodInfoDetailedStatus(ByteUtil.fromHexString("020d0000000407f28609ff03ff0a0200000823080000"));

        Assertions.assertEquals(PodProgressStatus.FAULT_EVENT_OCCURRED, podInfoDetailedStatus.getPodProgressStatus());
        Assertions.assertEquals(DeliveryStatus.SUSPENDED, podInfoDetailedStatus.getDeliveryStatus());
        Assertions.assertEquals(2034, podInfoDetailedStatus.getTicksDelivered());
        Assertions.assertEquals(101.7, podInfoDetailedStatus.getInsulinDelivered(), 0.000001);
        Assertions.assertEquals(0, podInfoDetailedStatus.getBolusNotDelivered(), 0.000001);
        Assertions.assertEquals(0x04, podInfoDetailedStatus.getPodMessageCounter());
        Assertions.assertEquals(FaultEventCode.BASAL_OVER_INFUSION_PULSE, podInfoDetailedStatus.getFaultEventCode());
        Assertions.assertTrue(Duration.standardMinutes(2559).isEqual(podInfoDetailedStatus.getFaultEventTime()));
        Assertions.assertNull(podInfoDetailedStatus.getReservoirLevel());
        Assertions.assertEquals(0, podInfoDetailedStatus.getUnacknowledgedAlerts().getRawValue());
        Assertions.assertFalse(podInfoDetailedStatus.isFaultAccessingTables());
        ErrorEventInfo errorEventInfo = podInfoDetailedStatus.getErrorEventInfo();
        Assertions.assertFalse(errorEventInfo.isInsulinStateTableCorruption());
        Assertions.assertEquals(0x00, errorEventInfo.getInternalVariable());
        Assertions.assertFalse(errorEventInfo.isImmediateBolusInProgress());
        Assertions.assertEquals(PodProgressStatus.ABOVE_FIFTY_UNITS, errorEventInfo.getPodProgressStatus());
        Assertions.assertEquals(PodProgressStatus.ABOVE_FIFTY_UNITS, podInfoDetailedStatus.getPreviousPodProgressStatus());
        Assertions.assertEquals(0, podInfoDetailedStatus.getReceiverLowGain());
        Assertions.assertEquals(35, podInfoDetailedStatus.getRadioRSSI());
    }

    @Test
    void testPodInfoFaultEventInsulinNotDelivered() {
        PodInfoDetailedStatus podInfoDetailedStatus = new PodInfoDetailedStatus(ByteUtil.fromHexString("020f0000010200ec6a026803ff026b000028a7082023"));

        Assertions.assertEquals(PodProgressStatus.INACTIVE, podInfoDetailedStatus.getPodProgressStatus());
        Assertions.assertEquals(DeliveryStatus.SUSPENDED, podInfoDetailedStatus.getDeliveryStatus());
        Assertions.assertEquals(236, podInfoDetailedStatus.getTicksDelivered());
        Assertions.assertEquals(11.8, podInfoDetailedStatus.getInsulinDelivered(), 0.000001);
        Assertions.assertEquals(0.05, podInfoDetailedStatus.getBolusNotDelivered(), 0.000001);
        Assertions.assertEquals(0x02, podInfoDetailedStatus.getPodMessageCounter());
        Assertions.assertEquals(FaultEventCode.OCCLUSION_CHECK_ABOVE_THRESHOLD, podInfoDetailedStatus.getFaultEventCode());
        Assertions.assertTrue(Duration.standardMinutes(616).isEqual(podInfoDetailedStatus.getFaultEventTime()));
        Assertions.assertNull(podInfoDetailedStatus.getReservoirLevel());
        Assertions.assertEquals(0, podInfoDetailedStatus.getUnacknowledgedAlerts().getRawValue());
        Assertions.assertFalse(podInfoDetailedStatus.isFaultAccessingTables());
        ErrorEventInfo errorEventInfo = podInfoDetailedStatus.getErrorEventInfo();
        Assertions.assertFalse(errorEventInfo.isInsulinStateTableCorruption());
        Assertions.assertEquals(0x01, errorEventInfo.getInternalVariable());
        Assertions.assertFalse(errorEventInfo.isImmediateBolusInProgress());
        Assertions.assertEquals(PodProgressStatus.ABOVE_FIFTY_UNITS, errorEventInfo.getPodProgressStatus());
        Assertions.assertEquals(PodProgressStatus.ABOVE_FIFTY_UNITS, podInfoDetailedStatus.getPreviousPodProgressStatus());
        Assertions.assertEquals(2, podInfoDetailedStatus.getReceiverLowGain());
        Assertions.assertEquals(39, podInfoDetailedStatus.getRadioRSSI());
    }

    @Test
    void testPodInfoFaultEventMaxBolusNotDelivered() {
        PodInfoDetailedStatus podInfoDetailedStatus = new PodInfoDetailedStatus(ByteUtil.fromHexString("020f00ffff0200ec6a026803ff026b000028a7082023"));

        Assertions.assertEquals(PodProgressStatus.INACTIVE, podInfoDetailedStatus.getPodProgressStatus());
        Assertions.assertEquals(DeliveryStatus.SUSPENDED, podInfoDetailedStatus.getDeliveryStatus());
        Assertions.assertEquals(236, podInfoDetailedStatus.getTicksDelivered());
        Assertions.assertEquals(11.8, podInfoDetailedStatus.getInsulinDelivered(), 0.000001);
        Assertions.assertEquals(3276.75, podInfoDetailedStatus.getBolusNotDelivered(), 0.000001); // Insane and will not happen, but this verifies that we convert it to an unsigned int
        Assertions.assertEquals(0x02, podInfoDetailedStatus.getPodMessageCounter());
        Assertions.assertEquals(FaultEventCode.OCCLUSION_CHECK_ABOVE_THRESHOLD, podInfoDetailedStatus.getFaultEventCode());
        Assertions.assertTrue(Duration.standardMinutes(616).isEqual(podInfoDetailedStatus.getFaultEventTime()));
        Assertions.assertNull(podInfoDetailedStatus.getReservoirLevel());
        Assertions.assertEquals(0, podInfoDetailedStatus.getUnacknowledgedAlerts().getRawValue());
        Assertions.assertFalse(podInfoDetailedStatus.isFaultAccessingTables());
        ErrorEventInfo errorEventInfo = podInfoDetailedStatus.getErrorEventInfo();
        Assertions.assertFalse(errorEventInfo.isInsulinStateTableCorruption());
        Assertions.assertEquals(0x01, errorEventInfo.getInternalVariable());
        Assertions.assertFalse(errorEventInfo.isImmediateBolusInProgress());
        Assertions.assertEquals(PodProgressStatus.ABOVE_FIFTY_UNITS, errorEventInfo.getPodProgressStatus());
        Assertions.assertEquals(PodProgressStatus.ABOVE_FIFTY_UNITS, podInfoDetailedStatus.getPreviousPodProgressStatus());
        Assertions.assertEquals(2, podInfoDetailedStatus.getReceiverLowGain());
        Assertions.assertEquals(39, podInfoDetailedStatus.getRadioRSSI());
    }

    @Test
    void testPodInfoFaultEventInsulinStateTableCorruptionFoundDuringErrorLogging() {
        PodInfoDetailedStatus podInfoDetailedStatus = new PodInfoDetailedStatus(ByteUtil.fromHexString("020D00000000000012FFFF03FF00160000879A070000"));

        Assertions.assertEquals(PodProgressStatus.FAULT_EVENT_OCCURRED, podInfoDetailedStatus.getPodProgressStatus());
        Assertions.assertEquals(DeliveryStatus.SUSPENDED, podInfoDetailedStatus.getDeliveryStatus());
        Assertions.assertEquals(0, podInfoDetailedStatus.getBolusNotDelivered(), 0.000001);
        Assertions.assertEquals(0x00, podInfoDetailedStatus.getPodMessageCounter());
        Assertions.assertEquals(FaultEventCode.RESET_DUE_TO_LVD, podInfoDetailedStatus.getFaultEventCode());
        Assertions.assertTrue(Duration.ZERO.isEqual(podInfoDetailedStatus.getFaultEventTime()));
        Assertions.assertNull(podInfoDetailedStatus.getReservoirLevel());
        Assertions.assertTrue(Duration.standardSeconds(1320).isEqual(podInfoDetailedStatus.getTimeActive()));
        Assertions.assertEquals(0, podInfoDetailedStatus.getUnacknowledgedAlerts().getRawValue());
        Assertions.assertFalse(podInfoDetailedStatus.isFaultAccessingTables());
        ErrorEventInfo errorEventInfo = podInfoDetailedStatus.getErrorEventInfo();
        Assertions.assertTrue(errorEventInfo.isInsulinStateTableCorruption());
        Assertions.assertEquals(0x00, errorEventInfo.getInternalVariable());
        Assertions.assertFalse(errorEventInfo.isImmediateBolusInProgress());
        Assertions.assertEquals(PodProgressStatus.INSERTING_CANNULA, errorEventInfo.getPodProgressStatus());
        Assertions.assertEquals(PodProgressStatus.INSERTING_CANNULA, podInfoDetailedStatus.getPreviousPodProgressStatus());
        Assertions.assertEquals(2, podInfoDetailedStatus.getReceiverLowGain());
        Assertions.assertEquals(26, podInfoDetailedStatus.getRadioRSSI());
    }
}
