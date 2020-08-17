package info.nightscout.androidaps.plugins.pump.omnipod.comm.message.command;

import org.joda.time.Duration;

import java.util.ArrayList;
import java.util.List;

import info.nightscout.androidaps.plugins.pump.common.utils.ByteUtil;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.exception.CommandInitializationException;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.message.MessageBlock;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.MessageBlockType;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.schedule.RateEntry;
import info.nightscout.androidaps.plugins.pump.omnipod.util.OmnipodConst;

public class TempBasalExtraCommand extends MessageBlock {
    private final boolean acknowledgementBeep;
    private final boolean completionBeep;
    private final Duration programReminderInterval;
    private final double remainingPulses;
    // We use a double for the delay until next pulse because the Joda time API lacks precision for our calculations
    private final double delayUntilNextPulse;
    private final List<RateEntry> rateEntries;

    public TempBasalExtraCommand(double rate, Duration duration, boolean acknowledgementBeep, boolean completionBeep,
                                 Duration programReminderInterval) {
        if (rate < 0D) {
            throw new CommandInitializationException("Rate should be >= 0");
        } else if (rate > OmnipodConst.MAX_BASAL_RATE) {
            throw new CommandInitializationException("Rate exceeds max basal rate");
        }
        if (duration.isLongerThan(OmnipodConst.MAX_TEMP_BASAL_DURATION)) {
            throw new CommandInitializationException("Duration exceeds max temp basal duration");
        }

        this.acknowledgementBeep = acknowledgementBeep;
        this.completionBeep = completionBeep;
        this.programReminderInterval = programReminderInterval;

        rateEntries = RateEntry.createEntries(rate, duration);

        RateEntry currentRateEntry = rateEntries.get(0);
        remainingPulses = currentRateEntry.getTotalPulses();
        delayUntilNextPulse = currentRateEntry.getDelayBetweenPulsesInSeconds();

        encode();
    }

    private void encode() {
        byte beepOptions = (byte) ((programReminderInterval.getStandardMinutes() & 0x3f) + (completionBeep ? 1 << 6 : 0) + (acknowledgementBeep ? 1 << 7 : 0));

        encodedData = new byte[]{
                beepOptions,
                (byte) 0x00
        };

        encodedData = ByteUtil.concat(encodedData, ByteUtil.getBytesFromInt16((int) Math.round(remainingPulses * 10)));
        if (remainingPulses == 0) {
            encodedData = ByteUtil.concat(encodedData, ByteUtil.getBytesFromInt((int) (delayUntilNextPulse * 1000 * 100) * 10));
        } else {
            encodedData = ByteUtil.concat(encodedData, ByteUtil.getBytesFromInt((int) (delayUntilNextPulse * 1000 * 100)));
        }

        for (RateEntry entry : rateEntries) {
            encodedData = ByteUtil.concat(encodedData, entry.getRawData());
        }
    }

    @Override
    public MessageBlockType getType() {
        return MessageBlockType.TEMP_BASAL_EXTRA;
    }

    public boolean isAcknowledgementBeep() {
        return acknowledgementBeep;
    }

    public boolean isCompletionBeep() {
        return completionBeep;
    }

    public Duration getProgramReminderInterval() {
        return programReminderInterval;
    }

    public double getRemainingPulses() {
        return remainingPulses;
    }

    public double getDelayUntilNextPulse() {
        return delayUntilNextPulse;
    }

    public List<RateEntry> getRateEntries() {
        return new ArrayList<>(rateEntries);
    }

    @Override
    public String toString() {
        return "TempBasalExtraCommand{" +
                "acknowledgementBeep=" + acknowledgementBeep +
                ", completionBeep=" + completionBeep +
                ", programReminderInterval=" + programReminderInterval +
                ", remainingPulses=" + remainingPulses +
                ", delayUntilNextPulse=" + delayUntilNextPulse +
                ", rateEntries=" + rateEntries +
                '}';
    }
}
