package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command.insulin.program;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.BasalProgram;

public final class ProgramBasalUtil {
    private static final byte NUMBER_OF_BASAL_SLOTS = 48;
    private static final byte MAX_NUMBER_OF_SLOTS_IN_SHORT_INSULIN_PROGRAM_ELEMENT = 16;

    private ProgramBasalUtil() {
    }

    public static List<LongInsulinProgramElement> mapPulsesPerSlotToLongInsulinProgramElements(short[] pulsesPerSlot) {
        if (pulsesPerSlot.length != NUMBER_OF_BASAL_SLOTS) {
            throw new IllegalArgumentException("Basal program must contain 48 slots");
        }

        // TODO

        return new ArrayList<>();
    }

    public static List<ShortInsulinProgramElement> mapPulsesPerSlotToShortInsulinProgramElements(short[] pulsesPerSlot) {
        if (pulsesPerSlot.length != NUMBER_OF_BASAL_SLOTS) {
            throw new IllegalArgumentException("Basal program must contain 48 slots");
        }

        List<ShortInsulinProgramElement> elements = new ArrayList<>();
        boolean extraAlternatePulse = false;
        short previousPulsesPerSlot = 0;
        byte numberOfSlotsInCurrentElement = 0;
        byte currentTotalNumberOfSlots = 0;

        while (currentTotalNumberOfSlots < NUMBER_OF_BASAL_SLOTS) {
            if (currentTotalNumberOfSlots == 0) {
                // First slot

                previousPulsesPerSlot = pulsesPerSlot[0];
                currentTotalNumberOfSlots++;
                numberOfSlotsInCurrentElement = 1;
            } else if (pulsesPerSlot[currentTotalNumberOfSlots] == previousPulsesPerSlot) {
                // Subsequent slot in element (same pulses per slot as previous slot)

                if (numberOfSlotsInCurrentElement < MAX_NUMBER_OF_SLOTS_IN_SHORT_INSULIN_PROGRAM_ELEMENT) {
                    numberOfSlotsInCurrentElement++;
                } else {
                    elements.add(new ShortInsulinProgramElement(numberOfSlotsInCurrentElement, previousPulsesPerSlot, false));
                    previousPulsesPerSlot = pulsesPerSlot[currentTotalNumberOfSlots];
                    numberOfSlotsInCurrentElement = 1;
                    extraAlternatePulse = false;
                }

                currentTotalNumberOfSlots++;
            } else if (numberOfSlotsInCurrentElement == 1 && pulsesPerSlot[currentTotalNumberOfSlots] == previousPulsesPerSlot + 1) {
                // Second slot of segment with extra alternate pulse

                boolean expectAlternatePulseForNextSegment = false;
                currentTotalNumberOfSlots++;
                extraAlternatePulse = true;
                while (currentTotalNumberOfSlots < NUMBER_OF_BASAL_SLOTS) {
                    // Loop rest alternate pulse segment

                    if (pulsesPerSlot[currentTotalNumberOfSlots] == previousPulsesPerSlot + (expectAlternatePulseForNextSegment ? 1 : 0)) {
                        // Still in alternate pulse segment

                        currentTotalNumberOfSlots++;
                        expectAlternatePulseForNextSegment = !expectAlternatePulseForNextSegment;

                        if (numberOfSlotsInCurrentElement < MAX_NUMBER_OF_SLOTS_IN_SHORT_INSULIN_PROGRAM_ELEMENT) {
                            numberOfSlotsInCurrentElement++;
                        } else {
                            // End of alternate pulse segment (no slots left in element)

                            elements.add(new ShortInsulinProgramElement(numberOfSlotsInCurrentElement, previousPulsesPerSlot, true));
                            previousPulsesPerSlot = pulsesPerSlot[currentTotalNumberOfSlots];
                            numberOfSlotsInCurrentElement = 1;
                            extraAlternatePulse = false;
                            break;
                        }
                    } else {
                        // End of alternate pulse segment (new number of pulses per slot)

                        elements.add(new ShortInsulinProgramElement(numberOfSlotsInCurrentElement, previousPulsesPerSlot, true));
                        previousPulsesPerSlot = pulsesPerSlot[currentTotalNumberOfSlots];
                        numberOfSlotsInCurrentElement = 1;
                        extraAlternatePulse = false;
                        currentTotalNumberOfSlots++;
                        break;
                    }
                }
            } else if (previousPulsesPerSlot != pulsesPerSlot[currentTotalNumberOfSlots]) {
                // End of segment (new number of pulses per slot)
                elements.add(new ShortInsulinProgramElement(numberOfSlotsInCurrentElement, previousPulsesPerSlot, false));

                previousPulsesPerSlot = pulsesPerSlot[currentTotalNumberOfSlots];
                currentTotalNumberOfSlots++;
                extraAlternatePulse = false;
                numberOfSlotsInCurrentElement = 1;
            } else {
                throw new IllegalStateException("Reached illegal point in mapBasalProgramToShortInsulinProgramElements");
            }
        }

        elements.add(new ShortInsulinProgramElement(numberOfSlotsInCurrentElement, previousPulsesPerSlot, extraAlternatePulse));

        return elements;
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
        short secondsRemaining = (short) ((index + 1) * 1800 - (secondOfMinute + hourOfDay * 3600 + minuteOfHour * 60));
        short pulsesRemaining = (short) ((double) pulsesPerSlot[index] * secondsRemaining / 1800);

        return new CurrentSlot(index, secondsRemaining, pulsesRemaining);
    }

    public static short createChecksum() {
        // TODO
        return 0;
    }
}
