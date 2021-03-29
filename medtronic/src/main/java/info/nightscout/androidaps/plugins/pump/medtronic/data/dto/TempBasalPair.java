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
public class TempBasalPair extends info.nightscout.androidaps.plugins.pump.common.defs.TempBasalPair {

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
            this.setInsulinRate(rateByte);
        else
            this.setInsulinRate(rateInt * 0.025);
        this.setDurationMinutes(startTimeByte * 30);
        this.setPercent(isPercent);
    }


    /**
     * This constructor is for use with PumpHistoryDecoder
     *
     * @param rateByte0
     * @param startTimeByte
     * @param isPercent
     */
    public TempBasalPair(byte rateByte0, byte rateByte1, int startTimeByte, boolean isPercent) {
        if (isPercent) {
            this.setInsulinRate(rateByte0);
        } else {
            this.setInsulinRate(ByteUtil.toInt(rateByte1, rateByte0) * 0.025);
        }
        this.setDurationMinutes(startTimeByte * 30);
        this.setPercent(isPercent);
    }

    public TempBasalPair(AAPSLogger aapsLogger, byte[] response) {
        super();

        aapsLogger.debug(LTag.PUMPBTCOMM, "Received TempBasal response: " + ByteUtil.getHex(response));

        setPercent(response[0] == 1);

        if (isPercent()) {
            setInsulinRate(response[1]);
        } else {
            int strokes = MedtronicUtil.makeUnsignedShort(response[2], response[3]);

            setInsulinRate(strokes / 40.0d);
        }

        if (response.length < 6) {
            setDurationMinutes(ByteUtil.asUINT8(response[4]));
        } else {
            setDurationMinutes(MedtronicUtil.makeUnsignedShort(response[4], response[5]));
        }

        aapsLogger.warn(LTag.PUMPBTCOMM, String.format(Locale.ENGLISH, "TempBasalPair (with %d byte response): %s", response.length, toString()));

    }


    public TempBasalPair(double insulinRate, boolean isPercent, int durationMinutes) {
        super(insulinRate, isPercent, durationMinutes);
    }


    public byte[] getAsRawData() {

        List<Byte> list = new ArrayList<>();

        list.add((byte) 5);

        byte[] insulinRate = MedtronicUtil.getBasalStrokes(this.getInsulinRate(), true);
        byte timeMin = (byte) MedtronicUtil.getIntervalFromMinutes(getDurationMinutes());

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
        return (MedtronicUtil.isSame(getInsulinRate(), 0.0d) && getDurationMinutes() == 0);
    }


    public String getDescription() {
        if (isCancelTBR()) {
            return "Cancel TBR";
        }

        if (isPercent()) {
            return String.format(Locale.ENGLISH, "Rate: %.0f%%, Duration: %d min", getInsulinRate(), getDurationMinutes());
        } else {
            return String.format(Locale.ENGLISH, "Rate: %.3f U, Duration: %d min", getInsulinRate(), getDurationMinutes());
        }
    }


    @NonNull @Override
    public String toString() {
        return "TempBasalPair [" + "Rate=" + getInsulinRate() + ", DurationMinutes=" + getDurationMinutes() + ", IsPercent="
                + isPercent() + "]";
    }
}
