package info.nightscout.androidaps.plugins.pump.omnipod.driver.communication.message.response;

import org.joda.time.Duration;
import org.junit.Test;

import info.nightscout.androidaps.plugins.pump.common.utils.ByteUtil;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.definition.AlertSlot;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.definition.DeliveryStatus;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.definition.OmnipodConstants;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.definition.PodProgressStatus;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class StatusResponseTest {
    // TODO add /extend tests

    @Test
    public void testRawData() {
        byte[] encodedData = ByteUtil.fromHexString("1d080000000038800000");

        StatusResponse statusResponse = new StatusResponse(encodedData);

        assertArrayEquals(encodedData, statusResponse.getRawData());
    }

    @Test
    public void testRawDataWithLongerMessage() {
        byte[] encodedData = ByteUtil.fromHexString("1d08000000003880000001");
        byte[] expected = ByteUtil.fromHexString("1d080000000038800000");

        StatusResponse statusResponse = new StatusResponse(encodedData);

        assertArrayEquals(expected, statusResponse.getRawData());
    }

    @Test
    public void testWithSampleCapture() {
        byte[] bytes = ByteUtil.fromHexString("1d180258f80000146fff"); // From https://github.com/openaps/openomni/wiki/Command-1D-Status-response
        StatusResponse statusResponse = new StatusResponse(bytes);

        assertEquals(DeliveryStatus.NORMAL, statusResponse.getDeliveryStatus());
        assertEquals(PodProgressStatus.ABOVE_FIFTY_UNITS, statusResponse.getPodProgressStatus());
        assertNull("Reservoir level should be null", statusResponse.getReservoirLevel());
        assertEquals(Duration.standardMinutes(1307).getMillis(), statusResponse.getTimeActive().getMillis());
        assertEquals(1201, statusResponse.getTicksDelivered());
        assertEquals(60.05, statusResponse.getInsulinDelivered(), 0.000001);
        assertEquals(15, statusResponse.getPodMessageCounter());
        assertEquals(0, statusResponse.getBolusNotDelivered(), 0.000001);
        assertEquals(0, statusResponse.getUnacknowledgedAlerts().getAlertSlots().size());

        assertArrayEquals(ByteUtil.fromHexString("1d180258f80000146fff"), statusResponse.getRawData());
    }

    @Test
    public void testWithSampleCaptureWithChangePodSoonAlert() {
        byte[] bytes = ByteUtil.fromHexString("1d19061f6800044295e8"); // From https://github.com/openaps/openomni/wiki/Status-Response-1D-long-run-%28Lytrix%29
        StatusResponse statusResponse = new StatusResponse(bytes);

        assertEquals(DeliveryStatus.NORMAL, statusResponse.getDeliveryStatus());
        assertEquals(PodProgressStatus.FIFTY_OR_LESS_UNITS, statusResponse.getPodProgressStatus());
        assertEquals(24.4, statusResponse.getReservoirLevel(), 0.000001);
        assertEquals(Duration.standardMinutes(4261).getMillis(), statusResponse.getTimeActive().getMillis());
        assertEquals(3134, statusResponse.getTicksDelivered());
        assertEquals(156.7, statusResponse.getInsulinDelivered(), 0.000001);
        assertEquals(13, statusResponse.getPodMessageCounter());
        assertEquals(0, statusResponse.getBolusNotDelivered(), 0.000001);
        assertEquals(1, statusResponse.getUnacknowledgedAlerts().getAlertSlots().size());
        assertEquals(AlertSlot.SLOT3, statusResponse.getUnacknowledgedAlerts().getAlertSlots().get(0));

        assertArrayEquals(ByteUtil.fromHexString("1d19061f6800044295e8"), statusResponse.getRawData());
    }

    @Test
    public void testLargeValues() {
        byte[] bytes = ByteUtil.fromHexString("1d11ffffffffffffffff");
        StatusResponse statusResponse = new StatusResponse(bytes);

        assertEquals(Duration.standardMinutes(8191).getMillis(), statusResponse.getTimeActive().getMillis());
        assertEquals(OmnipodConstants.POD_PULSE_SIZE * 1023, statusResponse.getBolusNotDelivered(), 0.000001);
        assertNull("Reservoir level should be null", statusResponse.getReservoirLevel());
        assertEquals(OmnipodConstants.POD_PULSE_SIZE * 8191, statusResponse.getInsulinDelivered(), 0.0000001);
        assertEquals(8191, statusResponse.getTicksDelivered());
        assertEquals(OmnipodConstants.POD_PULSE_SIZE * 8191, statusResponse.getInsulinDelivered(), 0.0000001);
        assertEquals(15, statusResponse.getPodMessageCounter());
        assertEquals(8, statusResponse.getUnacknowledgedAlerts().getAlertSlots().size());

        assertArrayEquals(ByteUtil.fromHexString("1d11ffffffffffffffff"), statusResponse.getRawData());
    }

    @Test
    public void testWithReservoirLevel() {
        byte[] bytes = ByteUtil.fromHexString("1d19050ec82c08376f98");
        StatusResponse statusResponse = new StatusResponse(bytes);

        assertTrue(Duration.standardMinutes(3547).isEqual(statusResponse.getTimeActive()));
        assertEquals(DeliveryStatus.NORMAL, statusResponse.getDeliveryStatus());
        assertEquals(PodProgressStatus.FIFTY_OR_LESS_UNITS, statusResponse.getPodProgressStatus());
        assertEquals(2589, statusResponse.getTicksDelivered());
        assertEquals(129.45, statusResponse.getInsulinDelivered(), 0.00001);
        assertEquals(46.00, statusResponse.getReservoirLevel(), 0.00001);
        assertEquals(2.2, statusResponse.getBolusNotDelivered(), 0.0001);
        assertEquals(9, statusResponse.getPodMessageCounter());

        assertArrayEquals(ByteUtil.fromHexString("1d19050ec82c08376f98"), statusResponse.getRawData());
    }
}