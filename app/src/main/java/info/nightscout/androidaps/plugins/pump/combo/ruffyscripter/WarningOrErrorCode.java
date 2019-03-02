package info.nightscout.androidaps.plugins.pump.combo.ruffyscripter;

import android.support.annotation.Nullable;

public class WarningOrErrorCode {
    @Nullable
    public final Integer warningCode;
    @Nullable
    public final Integer errorCode;
    @Nullable
    public String message;

    public WarningOrErrorCode(@Nullable Integer warningCode, @Nullable Integer errorCode, @Nullable String message) {
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
