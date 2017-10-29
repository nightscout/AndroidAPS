package de.jotomo.ruffy.spi.history;

public class WarningOrErrorCode {
    public final Integer warningCode;
    public final Integer errorCode;

    public WarningOrErrorCode(Integer warningCode, Integer errorCode) {
        this.warningCode = warningCode;
        this.errorCode = errorCode;
    }
}

