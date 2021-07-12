package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.response;

import java.util.Arrays;

abstract class ResponseBase implements Response {
    final ResponseType responseType;
    final byte[] encoded;

    ResponseBase(ResponseType responseType, byte[] encoded) {
        this.responseType = responseType;
        this.encoded = Arrays.copyOf(encoded, encoded.length);
    }

    @Override public ResponseType getResponseType() {
        return responseType;
    }

    @Override
    public byte[] getEncoded() {
        return encoded;
    }
}
