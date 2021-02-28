package info.nightscout.androidaps.plugins.pump.medtronic.data.dto;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.logging.LTag;
import info.nightscout.androidaps.plugins.pump.common.utils.ByteUtil;
import info.nightscout.androidaps.plugins.pump.medtronic.util.MedtronicUtil;

/**
 * Created by geoff on 5/29/15.
 * <p>
 * Just need a class to keep the pair together, for parcel transport.
 */
public class TempBasalPair extends info.nightscout.androidaps.plugins.pump.common.data.TempBasalPair {

    /**
     * This constructor is for use with PumpHistoryDecoder
     *
     * @param rateByte
     * @param startTimeByte
     * @param isPercent
     */
    public TempBasalPair(byte rateByte, int startTimeByte, boolean isPercent) {
        super();
        int rateInt = ByteUtil.asUINT8(rateByte);

        if (isPercent)
            this.insulinRate = rateByte;
        else
            this.insulinRate = rateInt * 0.025;
        this.durationMinutes = startTimeByte * 30;
        this.isPercent = isPercent;
    }


    public TempBasalPair(AAPSLogger aapsLogger, byte[] response) {
        super();

        aapsLogger.debug(LTag.PUMPBTCOMM, "Received TempBasal response: " + ByteUtil.getHex(response));

        isPercent = response[0] == 1;

        if (isPercent) {
            insulinRate = response[1];
        } else {
            int strokes = MedtronicUtil.makeUnsignedShort(response[2], response[3]);

            insulinRate = strokes / 40.0d;
        }

        if (response.length < 6) {
            durationMinutes = ByteUtil.asUINT8(response[4]);
        } else {
            durationMinutes = MedtronicUtil.makeUnsignedShort(response[4], response[5]);
        }

        aapsLogger.warn(LTag.PUMPBTCOMM, "TempBasalPair (with {} byte response): {}", response.length, toString());

    }


    public TempBasalPair(double insulinRate, boolean isPercent, int durationMinutes) {
        super(insulinRate, isPercent, durationMinutes);
    }


    public byte[] getAsRawData() {

        List<Byte> list = new ArrayList<>();

        list.add((byte) 5);

        byte[] insulinRate = MedtronicUtil.getBasalStrokes(this.insulinRate, true);
        byte timeMin = (byte) MedtronicUtil.getIntervalFromMinutes(durationMinutes);

        // list.add((byte) 0); // ?

        // list.add((byte) 0); // is_absolute

        if (insulinRate.length == 1)
            list.add((byte) 0x00);
        else
            list.add(insulinRate[0]);

        list.add(insulinRate[1]);
        // list.add((byte) 0); // percent amount

        list.add(timeMin); // 3 (time) - OK

        if (insulinRate.length == 1)
            list.add((byte) 0x00);
        else
            list.add(insulinRate[0]);

        list.add(insulinRate[1]);

        return MedtronicUtil.createByteArray(list);
    }

    public boolean isCancelTBR() {
        return (MedtronicUtil.isSame(insulinRate, 0.0d) && durationMinutes == 0);
    }


    public String getDescription() {
        if (isCancelTBR()) {
            return "Cancel TBR";
        }

        if (isPercent) {
            return String.format(Locale.ENGLISH, "Rate: %.0f%%, Duration: %d min", insulinRate, durationMinutes);
        } else {
            return String.format(Locale.ENGLISH, "Rate: %.3f U, Duration: %d min", insulinRate, durationMinutes);
        }
    }


    @NonNull @Override
    public String toString() {
        return "TempBasalPair [" + "Rate=" + insulinRate + ", DurationMinutes=" + durationMinutes + ", IsPercent="
                + isPercent + "]";
    }
}
