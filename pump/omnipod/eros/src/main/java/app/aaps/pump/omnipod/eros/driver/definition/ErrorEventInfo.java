package app.aaps.pump.omnipod.eros.driver.definition;

import app.aaps.core.utils.pump.ByteUtil;

public final class ErrorEventInfo {
    private final boolean insulinStateTableCorruption;
    private final byte internalVariable;
    private final boolean immediateBolusInProgress;
    private final PodProgressStatus podProgressStatus;

    private ErrorEventInfo(boolean insulinStateTableCorruption, byte internalVariable, boolean immediateBolusInProgress, PodProgressStatus podProgressStatus) {
        this.insulinStateTableCorruption = insulinStateTableCorruption;
        this.internalVariable = internalVariable;
        this.immediateBolusInProgress = immediateBolusInProgress;
        this.podProgressStatus = podProgressStatus;
    }

    public static ErrorEventInfo fromByte(byte faultEventInfo) {
        int loggedFaultEventInfo = ByteUtil.INSTANCE.convertUnsignedByteToInt(faultEventInfo);
        boolean insulinStateTableCorruption = (loggedFaultEventInfo & 0x80) == 0x80;
        byte internalVariable = (byte) ((loggedFaultEventInfo >>> 5) & 0x03);
        boolean immediateBolusInProgress = (loggedFaultEventInfo & 0x10) == 0x10;
        PodProgressStatus podProgressStatus = PodProgressStatus.fromByte((byte) (loggedFaultEventInfo & 0x0f));

        return new ErrorEventInfo(insulinStateTableCorruption, internalVariable, immediateBolusInProgress, podProgressStatus);
    }

    public boolean isInsulinStateTableCorruption() {
        return insulinStateTableCorruption;
    }

    public byte getInternalVariable() {
        return internalVariable;
    }

    public boolean isImmediateBolusInProgress() {
        return immediateBolusInProgress;
    }

    public PodProgressStatus getPodProgressStatus() {
        return podProgressStatus;
    }

    @Override public String toString() {
        return "ErrorEventInfo{" +
                "insulinStateTableCorruption=" + insulinStateTableCorruption +
                ", internalVariable=" + internalVariable +
                ", immediateBolusInProgress=" + immediateBolusInProgress +
                ", podProgressStatus=" + podProgressStatus +
                '}';
    }
}
