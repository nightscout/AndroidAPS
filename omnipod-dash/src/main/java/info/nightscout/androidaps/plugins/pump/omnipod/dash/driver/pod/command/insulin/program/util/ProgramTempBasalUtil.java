package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command.insulin.program.util;

import java.nio.ByteBuffer;
import java.util.List;

import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command.insulin.program.BasalInsulinProgramElement;
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command.insulin.program.ShortInsulinProgramElement;
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command.insulin.program.TempBasalInsulinProgramElement;
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.util.MessageUtil;

public final class ProgramTempBasalUtil {
    private ProgramTempBasalUtil() {
    }

    public static List<BasalInsulinProgramElement> mapTenthPulsesPerSlotToLongInsulinProgramElements(short[] tenthPulsesPerSlot) {
        return ProgramBasalUtil.mapTenthPulsesPerSlotToLongInsulinProgramElements(tenthPulsesPerSlot, TempBasalInsulinProgramElement::new);
    }

    public static short[] mapTempBasalToTenthPulsesPerSlot(int durationInSlots, double rateInUnitsPerHour) {
        short pulsesPerHour = (short) Math.round(rateInUnitsPerHour * 20);

        short[] tenthPulsesPerSlot = new short[durationInSlots];
        for (int i = 0; durationInSlots > i; i++) {
            tenthPulsesPerSlot[i] = (short) (roundToHalf(pulsesPerHour / 2.0d) * 10);
        }

        return tenthPulsesPerSlot;
    }

    private static double roundToHalf(double d) {
        return (double) (short) ((short) (int) (d * 10.0d) / 5 * 5) / 10.0d;
    }

    public static short[] mapTempBasalToPulsesPerSlot(byte durationInSlots, double rateInUnitsPerHour) {
        short pulsesPerHour = (short) Math.round(rateInUnitsPerHour * 20);
        short[] pulsesPerSlot = new short[durationInSlots];

        boolean remainingPulse = false;

        for (int i = 0; durationInSlots > i; i++) {
            pulsesPerSlot[i] = (short) (pulsesPerHour / 2);
            if (pulsesPerHour % 2 == 1) { // Do extra alternate pulse
                if (remainingPulse) {
                    pulsesPerSlot[i] += 1;
                }
                remainingPulse = !remainingPulse;
            }
        }

        return pulsesPerSlot;
    }

    public static short calculateChecksum(byte totalNumberOfSlots, short pulsesInFirstSlot, short[] pulsesPerSlot) {
        ByteBuffer buffer = ByteBuffer.allocate(1 + 2 + 2 + 2 * pulsesPerSlot.length) //
                .put(totalNumberOfSlots) //
                .putShort((short) 0x3840) //
                .putShort(pulsesInFirstSlot);
        for (short pulses : pulsesPerSlot) {
            buffer.putShort(pulses);
        }

        return MessageUtil.calculateChecksum(buffer.array());
    }

    public static List<ShortInsulinProgramElement> mapPulsesPerSlotToShortInsulinProgramElements(short[] pulsesPerSlot) {
        return ProgramBasalUtil.mapPulsesPerSlotToShortInsulinProgramElements(pulsesPerSlot);
    }
}
