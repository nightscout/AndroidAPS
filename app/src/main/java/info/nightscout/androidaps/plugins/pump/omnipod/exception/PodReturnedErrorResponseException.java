package info.nightscout.androidaps.plugins.pump.omnipod.exception;

import info.nightscout.androidaps.plugins.pump.omnipod.comm.message.response.ErrorResponse;

public class PodReturnedErrorResponseException extends OmnipodException {
    private final ErrorResponse errorResponse;

    public PodReturnedErrorResponseException(ErrorResponse errorResponse) {
        super("Pod returned error response");
        this.errorResponse = errorResponse;
    }

    public PodReturnedErrorResponseException(ErrorResponse errorResponse, Throwable cause) {
        super("Pod returned error response", cause);
        this.errorResponse = errorResponse;
    }

    public ErrorResponse getErrorResponse() {
        return errorResponse;
    }

    @Override
    public void printStackTrace() {
        System.out.println(errorResponse.toString());
        super.printStackTrace();
    }
}
