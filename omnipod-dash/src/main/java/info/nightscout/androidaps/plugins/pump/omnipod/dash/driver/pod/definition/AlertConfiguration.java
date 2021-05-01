package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition;

import androidx.annotation.NonNull;

import java.nio.ByteBuffer;

public class AlertConfiguration implements Encodable {
    private AlertSlot slot;
    private boolean enabled;
    private short durationInMinutes;
    private boolean autoOff;
    private AlertTriggerType triggerType;
    private short offsetInMinutesOrThresholdInMicroLiters;
    private BeepType beepType;
    private BeepRepetitionType beepRepetition;

    public AlertConfiguration(AlertSlot slot, boolean enabled, short durationInMinutes, boolean autoOff, AlertTriggerType triggerType, short offsetInMinutesOrThresholdInMicroLiters, BeepType beepType, BeepRepetitionType beepRepetition) {
        this.slot = slot;
        this.enabled = enabled;
        this.durationInMinutes = durationInMinutes;
        this.autoOff = autoOff;
        this.triggerType = triggerType;
        this.offsetInMinutesOrThresholdInMicroLiters = offsetInMinutesOrThresholdInMicroLiters;
        this.beepType = beepType;
        this.beepRepetition = beepRepetition;
    }

    @Override public byte[] getEncoded() {
        byte firstByte = (byte) (slot.getValue() << 4);
        if (enabled) {
            firstByte |= 1 << 3;
        }
        if (triggerType == AlertTriggerType.RESERVOIR_VOLUME_TRIGGER) {
            firstByte |= 1 << 2;
        }
        if (autoOff) {
            firstByte |= 1 << 1;
        }
        firstByte |= ((durationInMinutes >> 8) & 0x01);

        return ByteBuffer.allocate(6) //
                .put(firstByte)
                .put((byte) durationInMinutes) //
                .putShort(offsetInMinutesOrThresholdInMicroLiters) //
                .put(beepRepetition.getValue()) //
                .put(beepType.getValue()) //
                .array();
    }

    @NonNull @Override public String toString() {
        return "AlertConfiguration{" +
                "slot=" + slot +
                ", enabled=" + enabled +
                ", durationInMinutes=" + durationInMinutes +
                ", autoOff=" + autoOff +
                ", triggerType=" + triggerType +
                ", offsetInMinutesOrThresholdInMicroLiters=" + offsetInMinutesOrThresholdInMicroLiters +
                ", beepType=" + beepType +
                ", beepRepetition=" + beepRepetition +
                '}';
    }

}
