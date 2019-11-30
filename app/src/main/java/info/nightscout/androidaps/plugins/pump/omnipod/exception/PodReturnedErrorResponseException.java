package info.nightscout.androidaps.plugins.pump.omnipod.exception;

import info.nightscout.androidaps.plugins.pump.omnipod.comm.message.response.ErrorResponse;

public class PodReturnedErrorResponseException extends OmnipodException {
    private final ErrorResponse errorResponse;

    public PodReturnedErrorResponseException(ErrorResponse errorResponse) {
        super("Pod returned error response: " + errorResponse.getErrorResponseType());
        this.errorResponse = errorResponse;
    }

    public ErrorResponse getErrorResponse() {
        return errorResponse;
    }
}
