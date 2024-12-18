package app.aaps.pump.omnipod.eros.driver.communication.message.command;

import androidx.annotation.NonNull;

import org.joda.time.Duration;

import java.util.ArrayList;
import java.util.List;

import app.aaps.core.utils.pump.ByteUtil;
import app.aaps.pump.omnipod.eros.driver.communication.message.MessageBlock;
import app.aaps.pump.omnipod.eros.driver.definition.MessageBlockType;
import app.aaps.pump.omnipod.eros.driver.definition.OmnipodConstants;
import app.aaps.pump.omnipod.eros.driver.definition.schedule.BasalSchedule;
import app.aaps.pump.omnipod.eros.driver.definition.schedule.RateEntry;

public class BasalScheduleExtraCommand extends MessageBlock {
    private final boolean acknowledgementBeep;
    private final boolean completionBeep;
    private final Duration programReminderInterval;
    private final byte currentEntryIndex;
    private final double remainingPulses;
    // We use a double for the delay between pulses because the Joda time API lacks precision for our calculations
    private final double delayUntilNextTenthOfPulseInSeconds;
    private final List<RateEntry> rateEntries;

    public BasalScheduleExtraCommand(boolean acknowledgementBeep, boolean completionBeep,
                                     Duration programReminderInterval, byte currentEntryIndex,
                                     double remainingPulses, double delayUntilNextTenthOfPulseInSeconds, List<RateEntry> rateEntries) {

        this.acknowledgementBeep = acknowledgementBeep;
        this.completionBeep = completionBeep;
        this.programReminderInterval = programReminderInterval;
        this.currentEntryIndex = currentEntryIndex;
        this.remainingPulses = remainingPulses;
        this.delayUntilNextTenthOfPulseInSeconds = delayUntilNextTenthOfPulseInSeconds;
        this.rateEntries = rateEntries;
        encode();
    }

    public BasalScheduleExtraCommand(@NonNull BasalSchedule schedule, Duration scheduleOffset,
                                     boolean acknowledgementBeep, boolean completionBeep, Duration programReminderInterval) {
        rateEntries = new ArrayList<>();
        this.acknowledgementBeep = acknowledgementBeep;
        this.completionBeep = completionBeep;
        this.programReminderInterval = programReminderInterval;
        Duration scheduleOffsetNearestSecond = Duration.standardSeconds(Math.round(scheduleOffset.getMillis() / 1000.0));

        BasalSchedule mergedSchedule = new BasalSchedule(schedule.adjacentEqualRatesMergedEntries());
        List<BasalSchedule.BasalScheduleDurationEntry> durations = mergedSchedule.getDurations();

        for (BasalSchedule.BasalScheduleDurationEntry entry : durations) {
            rateEntries.addAll(RateEntry.createEntries(entry.getRate(), entry.getDuration()));
        }

        BasalSchedule.BasalScheduleLookupResult entryLookupResult = mergedSchedule.lookup(scheduleOffsetNearestSecond);
        currentEntryIndex = (byte) entryLookupResult.getIndex();
        double timeRemainingInEntryInSeconds = entryLookupResult.getStartTime().minus(scheduleOffsetNearestSecond.minus(entryLookupResult.getDuration())).getMillis() / 1000.0;
        double rate = mergedSchedule.rateAt(scheduleOffsetNearestSecond);
        int pulsesPerHour = (int) Math.round(rate / OmnipodConstants.POD_PULSE_SIZE);
        double timeBetweenPulses = 3600.0 / pulsesPerHour;
        delayUntilNextTenthOfPulseInSeconds = (timeRemainingInEntryInSeconds % (timeBetweenPulses / 10.0));
        remainingPulses = pulsesPerHour * (timeRemainingInEntryInSeconds - delayUntilNextTenthOfPulseInSeconds) / 3600.0 + 0.1;

        encode();
    }

    private void encode() {
        byte beepOptions = (byte) ((programReminderInterval.getStandardMinutes() & 0x3f) + (completionBeep ? 1 << 6 : 0) + (acknowledgementBeep ? 1 << 7 : 0));

        encodedData = new byte[]{
                beepOptions,
                currentEntryIndex
        };

        encodedData = ByteUtil.INSTANCE.concat(encodedData, ByteUtil.INSTANCE.getBytesFromInt16((int) Math.round(remainingPulses * 10)));
        encodedData = ByteUtil.INSTANCE.concat(encodedData, ByteUtil.INSTANCE.getBytesFromInt((int) Math.round(delayUntilNextTenthOfPulseInSeconds * 1000 * 1000)));

        for (RateEntry entry : rateEntries) {
            encodedData = ByteUtil.INSTANCE.concat(encodedData, entry.getRawData());
        }
    }

    @Override
    public MessageBlockType getType() {
        return MessageBlockType.BASAL_SCHEDULE_EXTRA;
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

    public byte getCurrentEntryIndex() {
        return currentEntryIndex;
    }

    public double getRemainingPulses() {
        return remainingPulses;
    }

    public double getDelayUntilNextTenthOfPulseInSeconds() {
        return delayUntilNextTenthOfPulseInSeconds;
    }

    public List<RateEntry> getRateEntries() {
        return new ArrayList<>(rateEntries);
    }

    @NonNull @Override
    public String toString() {
        return "BasalScheduleExtraCommand{" +
                "acknowledgementBeep=" + acknowledgementBeep +
                ", completionBeep=" + completionBeep +
                ", programReminderInterval=" + programReminderInterval +
                ", currentEntryIndex=" + currentEntryIndex +
                ", remainingPulses=" + remainingPulses +
                ", delayUntilNextTenthOfPulseInSeconds=" + delayUntilNextTenthOfPulseInSeconds +
                ", rateEntries=" + rateEntries +
                '}';
    }
}
