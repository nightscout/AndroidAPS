package app.aaps.pump.omnipod.eros.driver.communication.message.response;

import static com.google.common.truth.Truth.assertThat;

import org.joda.time.Duration;
import org.junit.jupiter.api.Test;

import app.aaps.core.utils.pump.ByteUtil;
import app.aaps.pump.omnipod.eros.driver.definition.AlertSlot;
import app.aaps.pump.omnipod.eros.driver.definition.DeliveryStatus;
import app.aaps.pump.omnipod.eros.driver.definition.OmnipodConstants;
import app.aaps.pump.omnipod.eros.driver.definition.PodProgressStatus;

class StatusResponseTest {

    @Test
    void testRawData() {
        byte[] encodedData = ByteUtil.INSTANCE.fromHexString("1d080000000038800000");

        StatusResponse statusResponse = new StatusResponse(encodedData);

        assertThat(statusResponse.getRawData()).isEqualTo(encodedData);
    }

    @Test
    void testRawDataWithLongerMessage() {
        byte[] encodedData = ByteUtil.INSTANCE.fromHexString("1d08000000003880000001");
        byte[] expected = ByteUtil.INSTANCE.fromHexString("1d080000000038800000");

        StatusResponse statusResponse = new StatusResponse(encodedData);

        assertThat(statusResponse.getRawData()).isEqualTo(expected);
    }

    @Test
    void testWithSampleCapture() {
        byte[] bytes = ByteUtil.INSTANCE.fromHexString("1d180258f80000146fff"); // From https://github.com/openaps/openomni/wiki/Command-1D-Status-response
        StatusResponse statusResponse = new StatusResponse(bytes);

        assertThat(statusResponse.getDeliveryStatus()).isEqualTo(DeliveryStatus.NORMAL);
        assertThat(statusResponse.getPodProgressStatus()).isEqualTo(PodProgressStatus.ABOVE_FIFTY_UNITS);
        assertThat(statusResponse.getReservoirLevel()).isNull();
        assertThat(statusResponse.getTimeActive().getMillis()).isEqualTo(Duration.standardMinutes(1307).getMillis());
        assertThat(statusResponse.getTicksDelivered()).isEqualTo(1201);
        assertThat(statusResponse.getInsulinDelivered()).isWithin(0.000001).of(60.05);
        assertThat(statusResponse.getPodMessageCounter()).isEqualTo(15);
        assertThat(statusResponse.getBolusNotDelivered()).isWithin(0.000001).of(0);
        assertThat(statusResponse.getUnacknowledgedAlerts().getAlertSlots()).isEmpty();

        assertThat(statusResponse.getRawData()).isEqualTo(ByteUtil.INSTANCE.fromHexString("1d180258f80000146fff"));
    }

    @Test
    void testWithSampleCaptureWithChangePodSoonAlert() {
        byte[] bytes = ByteUtil.INSTANCE.fromHexString("1d19061f6800044295e8"); // From https://github.com/openaps/openomni/wiki/Status-Response-1D-long-run-%28Lytrix%29
        StatusResponse statusResponse = new StatusResponse(bytes);

        assertThat(statusResponse.getDeliveryStatus()).isEqualTo(DeliveryStatus.NORMAL);
        assertThat(statusResponse.getPodProgressStatus()).isEqualTo(PodProgressStatus.FIFTY_OR_LESS_UNITS);
        assertThat(statusResponse.getReservoirLevel()).isWithin(0.000001).of(24.4);
        assertThat(statusResponse.getTimeActive().getMillis()).isEqualTo(Duration.standardMinutes(4261).getMillis());
        assertThat(statusResponse.getTicksDelivered()).isEqualTo(3134);
        assertThat(statusResponse.getInsulinDelivered()).isWithin(0.000001).of(156.7);
        assertThat(statusResponse.getPodMessageCounter()).isEqualTo(13);
        assertThat(statusResponse.getBolusNotDelivered()).isWithin(0.000001).of(0);
        assertThat(statusResponse.getUnacknowledgedAlerts().getAlertSlots()).containsExactly(AlertSlot.SLOT3);

        assertThat(statusResponse.getRawData()).isEqualTo(ByteUtil.INSTANCE.fromHexString("1d19061f6800044295e8"));
    }

    @Test
    void testLargeValues() {
        byte[] bytes = ByteUtil.INSTANCE.fromHexString("1d11ffffffffffffffff");
        StatusResponse statusResponse = new StatusResponse(bytes);

        assertThat(statusResponse.getTimeActive().getMillis()).isEqualTo(Duration.standardMinutes(8191).getMillis());
        assertThat(statusResponse.getBolusNotDelivered()).isWithin(0.000001).of(OmnipodConstants.POD_PULSE_SIZE * 1023);
        assertThat(statusResponse.getReservoirLevel()).isNull();
        assertThat(statusResponse.getInsulinDelivered()).isWithin(0.0000001).of(OmnipodConstants.POD_PULSE_SIZE * 8191);
        assertThat(statusResponse.getTicksDelivered()).isEqualTo(8191);
        assertThat(statusResponse.getInsulinDelivered()).isWithin(0.0000001).of(OmnipodConstants.POD_PULSE_SIZE * 8191);
        assertThat(statusResponse.getPodMessageCounter()).isEqualTo(15);
        assertThat(statusResponse.getUnacknowledgedAlerts().getAlertSlots()).hasSize(8);

        assertThat(statusResponse.getRawData()).isEqualTo(ByteUtil.INSTANCE.fromHexString("1d11ffffffffffffffff"));
    }

    @Test
    void testWithReservoirLevel() {
        byte[] bytes = ByteUtil.INSTANCE.fromHexString("1d19050ec82c08376f98");
        StatusResponse statusResponse = new StatusResponse(bytes);

        assertThat(statusResponse.getTimeActive()).isEqualTo(Duration.standardMinutes(3547));
        assertThat(statusResponse.getDeliveryStatus()).isEqualTo(DeliveryStatus.NORMAL);
        assertThat(statusResponse.getPodProgressStatus()).isEqualTo(PodProgressStatus.FIFTY_OR_LESS_UNITS);
        assertThat(statusResponse.getTicksDelivered()).isEqualTo(2589);
        assertThat(statusResponse.getInsulinDelivered()).isWithin(0.00001).of(129.45);
        assertThat(statusResponse.getReservoirLevel()).isWithin(0.00001).of(46.00);
        assertThat(statusResponse.getBolusNotDelivered()).isWithin(0.0001).of(2.2);
        assertThat(statusResponse.getPodMessageCounter()).isEqualTo(9);

        assertThat(statusResponse.getRawData()).isEqualTo(ByteUtil.INSTANCE.fromHexString("1d19050ec82c08376f98"));
    }
}
