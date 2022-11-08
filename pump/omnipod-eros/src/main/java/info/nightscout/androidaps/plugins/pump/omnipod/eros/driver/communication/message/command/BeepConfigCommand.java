package info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.communication.message.command;

import androidx.annotation.NonNull;

import org.joda.time.Duration;

import info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.communication.message.MessageBlock;
import info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.definition.BeepConfigType;
import info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.definition.MessageBlockType;
import info.nightscout.pump.core.utils.ByteUtil;

public class BeepConfigCommand extends MessageBlock {
    private final BeepConfigType beepType;
    private final boolean basalCompletionBeep;
    private final Duration basalIntervalBeep;
    private final boolean tempBasalCompletionBeep;
    private final Duration tempBasalIntervalBeep;
    private final boolean bolusCompletionBeep;
    private final Duration bolusIntervalBeep;

    public BeepConfigCommand(BeepConfigType beepType, boolean basalCompletionBeep, Duration basalIntervalBeep,
                             boolean tempBasalCompletionBeep, Duration tempBasalIntervalBeep,
                             boolean bolusCompletionBeep, Duration bolusIntervalBeep) {
        this.beepType = beepType;
        this.basalCompletionBeep = basalCompletionBeep;
        this.basalIntervalBeep = basalIntervalBeep;
        this.tempBasalCompletionBeep = tempBasalCompletionBeep;
        this.tempBasalIntervalBeep = tempBasalIntervalBeep;
        this.bolusCompletionBeep = bolusCompletionBeep;
        this.bolusIntervalBeep = bolusIntervalBeep;

        encode();
    }

    private void encode() {
        encodedData = new byte[]{beepType.getValue()};
        encodedData = ByteUtil.concat(encodedData, (byte) ((basalCompletionBeep ? (1 << 6) : 0) + (basalIntervalBeep.getStandardMinutes() & 0x3f)));
        encodedData = ByteUtil.concat(encodedData, (byte) ((tempBasalCompletionBeep ? (1 << 6) : 0) + (tempBasalIntervalBeep.getStandardMinutes() & 0x3f)));
        encodedData = ByteUtil.concat(encodedData, (byte) ((bolusCompletionBeep ? (1 << 6) : 0) + (bolusIntervalBeep.getStandardMinutes() & 0x3f)));
    }

    @Override
    public MessageBlockType getType() {
        return MessageBlockType.BEEP_CONFIG;
    }

    @Override @NonNull
    public String toString() {
        return "BeepConfigCommand{" +
                "beepType=" + beepType +
                ", basalCompletionBeep=" + basalCompletionBeep +
                ", basalIntervalBeep=" + basalIntervalBeep +
                ", tempBasalCompletionBeep=" + tempBasalCompletionBeep +
                ", tempBasalIntervalBeep=" + tempBasalIntervalBeep +
                ", bolusCompletionBeep=" + bolusCompletionBeep +
                ", bolusIntervalBeep=" + bolusIntervalBeep +
                '}';
    }
}
