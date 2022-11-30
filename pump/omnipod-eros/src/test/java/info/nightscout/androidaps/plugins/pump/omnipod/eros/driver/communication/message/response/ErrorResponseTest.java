package info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.communication.message.response;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.jupiter.api.Test;

import info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.definition.FaultEventCode;
import info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.definition.PodProgressStatus;
import info.nightscout.pump.core.utils.ByteUtil;

public class ErrorResponseTest {
    @Test
    public void testGetRawData() {
        byte[] encodedData = ByteUtil.fromHexString("060314fa92");
        ErrorResponse errorResponse = new ErrorResponse(encodedData);

        assertArrayEquals(encodedData, errorResponse.getRawData());
    }

    @Test
    public void testGetRawDataWithLongerMessage() {
        byte[] encodedData = ByteUtil.fromHexString("060314fa9201");
        byte[] expected = ByteUtil.fromHexString("060314fa92");

        ErrorResponse errorResponse = new ErrorResponse(encodedData);

        assertArrayEquals(expected, errorResponse.getRawData());
    }

    @Test
    public void testBadNonce() {
        byte[] encodedData = ByteUtil.fromHexString("060314fa92");

        ErrorResponse errorResponse = new ErrorResponse(encodedData);
        assertEquals(ErrorResponse.ERROR_RESPONSE_CODE_BAD_NONCE, errorResponse.getErrorResponseCode());
        // TODO add assertion on nonce search key (obtain captures first)
        assertNull(errorResponse.getFaultEventCode());
        assertNull(errorResponse.getPodProgressStatus());
    }

    @Test
    public void testOtherError() {
        ErrorResponse errorResponse = new ErrorResponse(ByteUtil.fromHexString("0603101308"));
        assertEquals(0x10, errorResponse.getErrorResponseCode());
        assertEquals(FaultEventCode.MESSAGE_LENGTH_TOO_LONG, errorResponse.getFaultEventCode());
        assertEquals(PodProgressStatus.ABOVE_FIFTY_UNITS, errorResponse.getPodProgressStatus());

        assertNull(errorResponse.getNonceSearchKey());
    }
}
