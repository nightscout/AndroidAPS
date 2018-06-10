package info.nightscout.androidaps.plugins.PumpMedtronic.comm.data;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import info.nightscout.androidaps.plugins.PumpMedtronic.util.MedtronicUtil;

/**
 * Created by geoff on 5/29/15.
 * <p>
 * Just need a class to keep the pair together, for parcel transport.
 */
public class TempBasalPair {

    private static final Logger LOG = LoggerFactory.getLogger(TempBasalPair.class);

    private double insulinRate = 0.0;
    private int durationMinutes = 0;
    private boolean isPercent = false;


    public double getInsulinRate() {
        return insulinRate;
    }


    public void setInsulinRate(double insulinRate) {
        this.insulinRate = insulinRate;
    }


    public int getDurationMinutes() {
        return durationMinutes;
    }


    public void setDurationMinutes(int durationMinutes) {
        this.durationMinutes = durationMinutes;
    }


    public boolean isPercent() {
        return isPercent;
    }


    public void setIsPercent(boolean yesIsPercent) {
        this.isPercent = yesIsPercent;
    }


    public TempBasalPair() {
    }


    public TempBasalPair(double insulinRate, boolean isPercent, int durationMinutes) {
        this.insulinRate = insulinRate;
        this.isPercent = isPercent;
        this.durationMinutes = durationMinutes;
    }


    public TempBasalPair(byte[] response) {

        LOG.debug("Received response: " + response);

        isPercent = response[0] == 1;

        if (isPercent) {
            insulinRate = response[1];
        } else {
            int strokes = MedtronicUtil.makeUnsignedShort(response[2], response[3]);

            insulinRate = strokes / 40.0d;
        }

        durationMinutes = MedtronicUtil.makeUnsignedShort(response[4], response[5]);

    }


    public byte[] getAsRawData() {

        // TODO check if this works with 523 and higher
        List<Byte> list = new ArrayList<Byte>();

        list.add((byte) 5);

        byte[] insulinRate = MedtronicUtil.getBasalStrokes(this.insulinRate, true);
        byte[] timeMin = MedtronicUtil.getByteArrayFromUnsignedShort(MedtronicUtil.getIntervalFromMinutes(durationMinutes), true);

        //list.add((byte) 0); // ?

        //list.add((byte) 0); // is_absolute

        if (insulinRate.length == 1)
            list.add((byte) 0x00);
        else
            list.add(insulinRate[1]);

        list.add(insulinRate[0]);
        //list.add((byte) 0); // percent amount

        list.add(timeMin[0]); // 3 (time) - OK

        if (insulinRate.length == 1)
            list.add((byte) 0x00);
        else
            list.add(insulinRate[1]);

        list.add(insulinRate[0]);

        return MedtronicUtil.createByteArray(list);
    }


    @Override
    public String toString() {
        return "TempBasalPair [" + "Rate=" + insulinRate + ", DurationMinutes=" + durationMinutes + ", IsPercent=" + isPercent + "]";
    }
}
