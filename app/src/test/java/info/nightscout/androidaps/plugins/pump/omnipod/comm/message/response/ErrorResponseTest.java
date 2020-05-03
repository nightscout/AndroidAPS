package info.nightscout.androidaps.plugins.pump.omnipod.comm.message.response;

import org.junit.Test;

import info.nightscout.androidaps.plugins.pump.common.utils.ByteUtil;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.ErrorResponseType;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

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
        assertEquals(ErrorResponseType.BAD_NONCE, errorResponse.getErrorResponseType());
        // TODO add assertion one nonce search key (obtain captures first)
    }

    @Test
    public void testUnknownError() {
        ErrorResponse errorResponse = new ErrorResponse(ByteUtil.fromHexString("060307fa92"));

        assertNull(errorResponse.getErrorResponseType());
    }
}
