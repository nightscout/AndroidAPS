package de.jotomo.ruffy.spi.history;

import android.support.annotation.Nullable;

public class WarningOrErrorCode {
    @Nullable
    public final Integer warningCode;
    @Nullable
    public final Integer errorCode;
    @Nullable
    public String message;

    public WarningOrErrorCode(@Nullable Integer warningCode, @Nullable Integer errorCode, @Nullable String message) {
        if (warningCode == null && errorCode == null) {
            throw new IllegalArgumentException("Either code must be non-null");
        }
        this.warningCode = warningCode;
        this.errorCode = errorCode;
        this.message = message;
    }

    @Override
    public String toString() {
        return "WarningOrErrorCode{" +
                "warningCode=" + warningCode +
                ", errorCode=" + errorCode +
                ", message=" + message +
                '}';
    }
}

