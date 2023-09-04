package info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.communication.message.response;

import org.joda.time.Duration;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.definition.AlertSlot;
import info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.definition.DeliveryStatus;
import info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.definition.OmnipodConstants;
import info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.definition.PodProgressStatus;
import info.nightscout.pump.core.utils.ByteUtil;

class StatusResponseTest {

    @Test
    void testRawData() {
        byte[] encodedData = ByteUtil.fromHexString("1d080000000038800000");

        StatusResponse statusResponse = new StatusResponse(encodedData);

        Assertions.assertArrayEquals(encodedData, statusResponse.getRawData());
    }

    @Test
    void testRawDataWithLongerMessage() {
        byte[] encodedData = ByteUtil.fromHexString("1d08000000003880000001");
        byte[] expected = ByteUtil.fromHexString("1d080000000038800000");

        StatusResponse statusResponse = new StatusResponse(encodedData);

        Assertions.assertArrayEquals(expected, statusResponse.getRawData());
    }

    @Test
    void testWithSampleCapture() {
        byte[] bytes = ByteUtil.fromHexString("1d180258f80000146fff"); // From https://github.com/openaps/openomni/wiki/Command-1D-Status-response
        StatusResponse statusResponse = new StatusResponse(bytes);

        Assertions.assertEquals(DeliveryStatus.NORMAL, statusResponse.getDeliveryStatus());
        Assertions.assertEquals(PodProgressStatus.ABOVE_FIFTY_UNITS, statusResponse.getPodProgressStatus());
        Assertions.assertNull(statusResponse.getReservoirLevel());
        Assertions.assertEquals(Duration.standardMinutes(1307).getMillis(), statusResponse.getTimeActive().getMillis());
        Assertions.assertEquals(1201, statusResponse.getTicksDelivered());
        Assertions.assertEquals(60.05, statusResponse.getInsulinDelivered(), 0.000001);
        Assertions.assertEquals(15, statusResponse.getPodMessageCounter());
        Assertions.assertEquals(0, statusResponse.getBolusNotDelivered(), 0.000001);
        Assertions.assertEquals(0, statusResponse.getUnacknowledgedAlerts().getAlertSlots().size());

        Assertions.assertArrayEquals(ByteUtil.fromHexString("1d180258f80000146fff"), statusResponse.getRawData());
    }

    @Test
    void testWithSampleCaptureWithChangePodSoonAlert() {
        byte[] bytes = ByteUtil.fromHexString("1d19061f6800044295e8"); // From https://github.com/openaps/openomni/wiki/Status-Response-1D-long-run-%28Lytrix%29
        StatusResponse statusResponse = new StatusResponse(bytes);

        Assertions.assertEquals(DeliveryStatus.NORMAL, statusResponse.getDeliveryStatus());
        Assertions.assertEquals(PodProgressStatus.FIFTY_OR_LESS_UNITS, statusResponse.getPodProgressStatus());
        Assertions.assertEquals(24.4, statusResponse.getReservoirLevel(), 0.000001);
        Assertions.assertEquals(Duration.standardMinutes(4261).getMillis(), statusResponse.getTimeActive().getMillis());
        Assertions.assertEquals(3134, statusResponse.getTicksDelivered());
        Assertions.assertEquals(156.7, statusResponse.getInsulinDelivered(), 0.000001);
        Assertions.assertEquals(13, statusResponse.getPodMessageCounter());
        Assertions.assertEquals(0, statusResponse.getBolusNotDelivered(), 0.000001);
        Assertions.assertEquals(1, statusResponse.getUnacknowledgedAlerts().getAlertSlots().size());
        Assertions.assertEquals(AlertSlot.SLOT3, statusResponse.getUnacknowledgedAlerts().getAlertSlots().get(0));

        Assertions.assertArrayEquals(ByteUtil.fromHexString("1d19061f6800044295e8"), statusResponse.getRawData());
    }

    @Test
    void testLargeValues() {
        byte[] bytes = ByteUtil.fromHexString("1d11ffffffffffffffff");
        StatusResponse statusResponse = new StatusResponse(bytes);

        Assertions.assertEquals(Duration.standardMinutes(8191).getMillis(), statusResponse.getTimeActive().getMillis());
        Assertions.assertEquals(OmnipodConstants.POD_PULSE_SIZE * 1023, statusResponse.getBolusNotDelivered(), 0.000001);
        Assertions.assertNull(statusResponse.getReservoirLevel());
        Assertions.assertEquals(OmnipodConstants.POD_PULSE_SIZE * 8191, statusResponse.getInsulinDelivered(), 0.0000001);
        Assertions.assertEquals(8191, statusResponse.getTicksDelivered());
        Assertions.assertEquals(OmnipodConstants.POD_PULSE_SIZE * 8191, statusResponse.getInsulinDelivered(), 0.0000001);
        Assertions.assertEquals(15, statusResponse.getPodMessageCounter());
        Assertions.assertEquals(8, statusResponse.getUnacknowledgedAlerts().getAlertSlots().size());

        Assertions.assertArrayEquals(ByteUtil.fromHexString("1d11ffffffffffffffff"), statusResponse.getRawData());
    }

    @Test
    void testWithReservoirLevel() {
        byte[] bytes = ByteUtil.fromHexString("1d19050ec82c08376f98");
        StatusResponse statusResponse = new StatusResponse(bytes);

        Assertions.assertTrue(Duration.standardMinutes(3547).isEqual(statusResponse.getTimeActive()));
        Assertions.assertEquals(DeliveryStatus.NORMAL, statusResponse.getDeliveryStatus());
        Assertions.assertEquals(PodProgressStatus.FIFTY_OR_LESS_UNITS, statusResponse.getPodProgressStatus());
        Assertions.assertEquals(2589, statusResponse.getTicksDelivered());
        Assertions.assertEquals(129.45, statusResponse.getInsulinDelivered(), 0.00001);
        Assertions.assertEquals(46.00, statusResponse.getReservoirLevel(), 0.00001);
        Assertions.assertEquals(2.2, statusResponse.getBolusNotDelivered(), 0.0001);
        Assertions.assertEquals(9, statusResponse.getPodMessageCounter());

        Assertions.assertArrayEquals(ByteUtil.fromHexString("1d19050ec82c08376f98"), statusResponse.getRawData());
    }
}