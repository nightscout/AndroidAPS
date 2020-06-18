package info.nightscout.androidaps.plugins.pump.omnipod.comm;

import info.nightscout.androidaps.plugins.pump.omnipod.defs.SetupProgress;

public class SetupActionResult {
    private final ResultType resultType;
    private String message;
    private Exception exception;
    private SetupProgress setupProgress;

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

    public SetupActionResult setupProgress(SetupProgress setupProgress) {
        this.setupProgress = setupProgress;
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

    public SetupProgress getSetupProgress() {
        return setupProgress;
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
