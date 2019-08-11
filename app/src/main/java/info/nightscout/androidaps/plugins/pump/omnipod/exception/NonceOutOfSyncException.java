package info.nightscout.androidaps.plugins.pump.omnipod.exception;

import info.nightscout.androidaps.plugins.pump.omnipod.comm.message.response.ErrorResponse;

public class NonceOutOfSyncException extends PodReturnedErrorResponseException {
    public NonceOutOfSyncException(ErrorResponse errorResponse) {
        super(errorResponse);
    }

    public NonceOutOfSyncException(ErrorResponse errorResponse, Throwable cause) {
        super(errorResponse, cause);
    }
}
