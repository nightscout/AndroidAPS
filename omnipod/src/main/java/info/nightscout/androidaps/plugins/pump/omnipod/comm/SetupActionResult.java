package info.nightscout.androidaps.plugins.pump.omnipod.comm;

import info.nightscout.androidaps.plugins.pump.omnipod.defs.PodProgressStatus;

public class SetupActionResult {
    private final ResultType resultType;
    private String message;
    private Exception exception;
    private PodProgressStatus podProgressStatus;

    public SetupActionResult(ResultType resultType) {
        this.resultType = resultType;
    }

    public SetupActionResult message(String message) {
        this.message = message;
        return this;
    }

    public SetupActionResult exception(Exception ex) {
        exception = ex;
        return this;
    }

    public SetupActionResult podProgressStatus(PodProgressStatus podProgressStatus) {
        this.podProgressStatus = podProgressStatus;
        return this;
    }

    public ResultType getResultType() {
        return resultType;
    }

    public String getMessage() {
        return message;
    }

    public Exception getException() {
        return exception;
    }

    public PodProgressStatus getPodProgressStatus() {
        return podProgressStatus;
    }

    public enum ResultType {
        SUCCESS(true),
        VERIFICATION_FAILURE(false),
        FAILURE(false);

        private final boolean success;

        ResultType(boolean success) {
            this.success = success;
        }

        public boolean isSuccess() {
            return success;
        }
    }
}
