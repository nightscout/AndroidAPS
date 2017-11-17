package de.jotomo.ruffy.spi.history;

import android.support.annotation.Nullable;

public class WarningOrErrorCode {
    @Nullable
    public final Integer warningCode;
    @Nullable
    public final Integer errorCode;

    public WarningOrErrorCode(@Nullable Integer warningCode, @Nullable Integer errorCode) {
        if (warningCode == null && errorCode == null) {
            throw new IllegalArgumentException("Either code must be non-null");
        }
        this.warningCode = warningCode;
        this.errorCode = errorCode;
    }

    @Override
    public String toString() {
        return "WarningOrErrorCode{" +
                "warningCode=" + warningCode +
                ", errorCode=" + errorCode +
                '}';
    }
}

