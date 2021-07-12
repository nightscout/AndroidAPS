package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command.insulin.program.util;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command.insulin.program.BasalInsulinProgramElement;
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command.insulin.program.BasalShortInsulinProgramElement;
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command.insulin.program.CurrentBasalInsulinProgramElement;
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command.insulin.program.CurrentSlot;
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command.insulin.program.ShortInsulinProgramElement;
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.BasalProgram;
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.util.MessageUtil;

public final class ProgramBasalUtil {
    public static final int MAX_DELAY_BETWEEN_TENTH_PULSES_IN_USEC_AND_USECS_IN_BASAL_SLOT = 1_800_000_000;

    public static final byte NUMBER_OF_BASAL_SLOTS = 48;
    public static final byte MAX_NUMBER_OF_SLOTS_IN_INSULIN_PROGRAM_ELEMENT = 16;

    private ProgramBasalUtil() {
    }

    public interface BasalInsulinProgramElementFactory<T extends BasalInsulinProgramElement> {
        T create(byte startSlotIndex, byte numberOfSlots, short totalTenthPulses);
    }

    public static List<BasalInsulinProgramElement> mapTenthPulsesPerSlotToLongInsulinProgramElements(short[] tenthPulsesPerSlot) {
        return mapTenthPulsesPerSlotToLongInsulinProgramElements(tenthPulsesPerSlot, BasalInsulinProgramElement::new);
    }

    public static <T extends BasalInsulinProgramElement> List<BasalInsulinProgramElement> mapTenthPulsesPerSlotToLongInsulinProgramElements(short[] tenthPulsesPerSlot, BasalInsulinProgramElementFactory<T> insulinProgramElementFactory) {
        if (tenthPulsesPerSlot.length > NUMBER_OF_BASAL_SLOTS) {
            throw new IllegalArgumentException("Basal program must contain at most 48 slots");
        }

        List<BasalInsulinProgramElement> elements = new ArrayList<>();
        long previousTenthPulsesPerSlot = 0;
        byte numberOfSlotsInCurrentElement = 0;
        byte startSlotIndex = 0;

        for (int i = 0; i < tenthPulsesPerSlot.length; i++) {
            if (i == 0) {
                previousTenthPulsesPerSlot = tenthPulsesPerSlot[i];
                numberOfSlotsInCurrentElement = 1;
            } else if (previousTenthPulsesPerSlot != tenthPulsesPerSlot[i] || (numberOfSlotsInCurrentElement + 1) * previousTenthPulsesPerSlot > 65_534) {
                elements.add(insulinProgramElementFactory.create(startSlotIndex, numberOfSlotsInCurrentElement, (short) (previousTenthPulsesPerSlot * numberOfSlotsInCurrentElement)));

                previousTenthPulsesPerSlot = tenthPulsesPerSlot[i];
                numberOfSlotsInCurrentElement = 1;
                startSlotIndex += numberOfSlotsInCurrentElement;
            } else {
                numberOfSlotsInCurrentElement++;
            }
        }
        elements.add(insulinProgramElementFactory.create(startSlotIndex, numberOfSlotsInCurrentElement, (short) (previousTenthPulsesPerSlot * numberOfSlotsInCurrentElement)));

        return elements;
    }

    public static List<ShortInsulinProgramElement> mapPulsesPerSlotToShortInsulinProgramElements(short[] pulsesPerSlot) {
        if (pulsesPerSlot.length > NUMBER_OF_BASAL_SLOTS) {
            throw new IllegalArgumentException("Basal program must contain at most 48 slots");
        }

        List<ShortInsulinProgramElement> elements = new ArrayList<>();
        boolean extraAlternatePulse = false;
        short previousPulsesPerSlot = 0;
        byte numberOfSlotsInCurrentElement = 0;
        byte currentTotalNumberOfSlots = 0;

        while (currentTotalNumberOfSlots < pulsesPerSlot.length) {
            if (currentTotalNumberOfSlots == 0) {
                // First slot

                previousPulsesPerSlot = pulsesPerSlot[0];
                currentTotalNumberOfSlots++;
                numberOfSlotsInCurrentElement = 1;
            } else if (pulsesPerSlot[currentTotalNumberOfSlots] == previousPulsesPerSlot) {
                // Subsequent slot in element (same pulses per slot as previous slot)

                if (numberOfSlotsInCurrentElement < MAX_NUMBER_OF_SLOTS_IN_INSULIN_PROGRAM_ELEMENT) {
                    numberOfSlotsInCurrentElement++;
                } else {
                    elements.add(new BasalShortInsulinProgramElement(numberOfSlotsInCurrentElement, previousPulsesPerSlot, extraAlternatePulse));
                    previousPulsesPerSlot = pulsesPerSlot[currentTotalNumberOfSlots];
                    numberOfSlotsInCurrentElement = 1;
                    extraAlternatePulse = false;
                }

                currentTotalNumberOfSlots++;
            } else if (numberOfSlotsInCurrentElement == 1 && pulsesPerSlot[currentTotalNumberOfSlots] == previousPulsesPerSlot + 1) {
                // Second slot of segment with extra alternate pulse

                boolean expectAlternatePulseForNextSegment = false;
                currentTotalNumberOfSlots++;
                numberOfSlotsInCurrentElement++;
                extraAlternatePulse = true;
                while (currentTotalNumberOfSlots < pulsesPerSlot.length) {
                    // Loop rest alternate pulse segment

                    if (pulsesPerSlot[currentTotalNumberOfSlots] == previousPulsesPerSlot + (expectAlternatePulseForNextSegment ? 1 : 0)) {
                        // Still in alternate pulse segment

                        currentTotalNumberOfSlots++;
                        expectAlternatePulseForNextSegment = !expectAlternatePulseForNextSegment;

                        if (numberOfSlotsInCurrentElement < MAX_NUMBER_OF_SLOTS_IN_INSULIN_PROGRAM_ELEMENT) {
                            numberOfSlotsInCurrentElement++;
                        } else {
                            // End of alternate pulse segment (no slots left in element)

                            elements.add(new BasalShortInsulinProgramElement(numberOfSlotsInCurrentElement, previousPulsesPerSlot, extraAlternatePulse));
                            previousPulsesPerSlot = pulsesPerSlot[currentTotalNumberOfSlots];
                            numberOfSlotsInCurrentElement = 1;
                            extraAlternatePulse = false;
                            break;
                        }
                    } else {
                        // End of alternate pulse segment (new number of pulses per slot)

                        elements.add(new BasalShortInsulinProgramElement(numberOfSlotsInCurrentElement, previousPulsesPerSlot, extraAlternatePulse));
                        previousPulsesPerSlot = pulsesPerSlot[currentTotalNumberOfSlots];
                        numberOfSlotsInCurrentElement = 1;
                        extraAlternatePulse = false;
                        currentTotalNumberOfSlots++;
                        break;
                    }
                }
            } else if (previousPulsesPerSlot != pulsesPerSlot[currentTotalNumberOfSlots]) {
                // End of segment (new number of pulses per slot)
                elements.add(new BasalShortInsulinProgramElement(numberOfSlotsInCurrentElement, previousPulsesPerSlot, extraAlternatePulse));

                previousPulsesPerSlot = pulsesPerSlot[currentTotalNumberOfSlots];
                currentTotalNumberOfSlots++;
                extraAlternatePulse = false;
                numberOfSlotsInCurrentElement = 1;
            } else {
                throw new IllegalStateException("Reached illegal point in mapBasalProgramToShortInsulinProgramElements");
            }
        }

        elements.add(new BasalShortInsulinProgramElement(numberOfSlotsInCurrentElement, previousPulsesPerSlot, extraAlternatePulse));

        return elements;
    }

    public static short[] mapBasalProgramToTenthPulsesPerSlot(BasalProgram basalProgram) {
        short[] tenthPulsesPerSlot = new short[NUMBER_OF_BASAL_SLOTS];
        for (BasalProgram.Segment segment : basalProgram.getSegments()) {
            for (int i = segment.getStartSlotIndex(); i < segment.getEndSlotIndex(); i++) {
                tenthPulsesPerSlot[i] = (short) (roundToHalf(segment.getPulsesPerHour() / 2.0d) * 10);
            }
        }

        return tenthPulsesPerSlot;
    }

    private static double roundToHalf(double d) {
        return (double) (short) ((short) (int) (d * 10.0d) / 5 * 5) / 10.0d;
    }

    public static short[] mapBasalProgramToPulsesPerSlot(BasalProgram basalProgram) {
        short[] pulsesPerSlot = new short[NUMBER_OF_BASAL_SLOTS];
        for (BasalProgram.Segment segment : basalProgram.getSegments()) {
            boolean remainingPulse = false;
            for (int i = segment.getStartSlotIndex(); i < segment.getEndSlotIndex(); i++) {
                pulsesPerSlot[i] = (short) (segment.getPulsesPerHour() / 2);
                if (segment.getPulsesPerHour() % 2 == 1) { // Do extra alternate pulse
                    if (remainingPulse) {
                        pulsesPerSlot[i] += 1;
                    }
                    remainingPulse = !remainingPulse;
                }
            }
        }

        return pulsesPerSlot;
    }

    public static CurrentSlot calculateCurrentSlot(short[] pulsesPerSlot, Date currentTime) {
        Calendar instance = Calendar.getInstance();
        instance.setTime(currentTime);

        int hourOfDay = instance.get(Calendar.HOUR_OF_DAY);
        int minuteOfHour = instance.get(Calendar.MINUTE);
        int secondOfMinute = instance.get(Calendar.SECOND);

        byte index = (byte) ((hourOfDay * 60 + minuteOfHour) / 30);
        int secondOfDay = secondOfMinute + hourOfDay * 3_600 + minuteOfHour * 60;

        short secondsRemaining = (short) ((index + 1) * 1_800 - secondOfDay);
        short pulsesRemaining = (short) ((double) pulsesPerSlot[index] * secondsRemaining / 1_800);

        return new CurrentSlot(index, (short) (secondsRemaining * 8), pulsesRemaining);
    }

    public static CurrentBasalInsulinProgramElement calculateCurrentLongInsulinProgramElement(List<BasalInsulinProgramElement> elements, Date currentTime) {
        Calendar instance = Calendar.getInstance();
        instance.setTime(currentTime);

        int hourOfDay = instance.get(Calendar.HOUR_OF_DAY);
        int minuteOfHour = instance.get(Calendar.MINUTE);
        int secondOfMinute = instance.get(Calendar.SECOND);

        int secondOfDay = secondOfMinute + hourOfDay * 3_600 + minuteOfHour * 60;
        int startSlotIndex = 0;

        byte index = 0;
        for (BasalInsulinProgramElement element : elements) {
            int startTimeInSeconds = startSlotIndex * 1_800;
            int endTimeInSeconds = startTimeInSeconds + element.getNumberOfSlots() * 1_800;

            if (secondOfDay >= startTimeInSeconds && secondOfDay < endTimeInSeconds) {
                long totalNumberOfTenThousandthPulsesInSlot = element.getTotalTenthPulses() * 1_000;
                if (totalNumberOfTenThousandthPulsesInSlot == 0) {
                    totalNumberOfTenThousandthPulsesInSlot = element.getNumberOfSlots() * 1_000;
                }

                int durationInSeconds = endTimeInSeconds - startTimeInSeconds;
                int secondsPassedInCurrentSlot = secondOfDay - startTimeInSeconds;
                long remainingTenThousandthPulses = (long) ((durationInSeconds - secondsPassedInCurrentSlot) / (double) durationInSeconds * totalNumberOfTenThousandthPulsesInSlot);
                int delayBetweenTenthPulsesInUsec = (int) (durationInSeconds * 1_000_000L * 1_000 / totalNumberOfTenThousandthPulsesInSlot);
                int secondsRemaining = secondsPassedInCurrentSlot % 1_800;
                int delayUntilNextTenthPulseInUsec = delayBetweenTenthPulsesInUsec;
                for (int i = 0; i < secondsRemaining; i++) {
                    delayUntilNextTenthPulseInUsec = delayUntilNextTenthPulseInUsec - 1_000_000;
                    while (delayUntilNextTenthPulseInUsec <= 0) {
                        delayUntilNextTenthPulseInUsec += delayBetweenTenthPulsesInUsec;
                    }
                }
                short remainingTenthPulses = (short) ((remainingTenThousandthPulses % 1_000 != 0 ? 1 : 0) + remainingTenThousandthPulses / 1_000);

                return new CurrentBasalInsulinProgramElement(index, delayUntilNextTenthPulseInUsec, remainingTenthPulses);
            }

            index++;
            startSlotIndex += element.getNumberOfSlots();
        }
        throw new IllegalStateException("Could not determine current long insulin program element");
    }

    public static short calculateChecksum(short[] pulsesPerSlot, CurrentSlot currentSlot) {
        ByteBuffer buffer = ByteBuffer.allocate(1 + 2 + 2 + NUMBER_OF_BASAL_SLOTS * 2) //
                .put(currentSlot.getIndex()) //
                .putShort(currentSlot.getPulsesRemaining()) //
                .putShort(currentSlot.getEighthSecondsRemaining());

        for (short pulses : pulsesPerSlot) {
            buffer.putShort(pulses);
        }

        return MessageUtil.calculateChecksum(buffer.array());
    }
}
