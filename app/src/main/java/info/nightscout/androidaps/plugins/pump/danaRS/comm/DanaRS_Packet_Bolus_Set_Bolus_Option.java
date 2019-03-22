package info.nightscout.androidaps.plugins.pump.danaRS.comm;

import com.cozmo.danar.util.BleCommandUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.logging.L;

public class DanaRS_Packet_Bolus_Set_Bolus_Option extends DanaRS_Packet {
    private Logger log = LoggerFactory.getLogger(L.PUMPCOMM);
    private int extendedBolusOptionOnOff;

    private int bolusCalculationOption;
    private int missedBolusConfig;
    private int missedBolus01StartHour;
    private int missedBolus01StartMin;
    private int missedBolus01EndHour;
    private int missedBolus01EndMin;
    private int missedBolus02StartHour;
    private int missedBolus02StartMin;
    private int missedBolus02EndHour;
    private int missedBolus02EndMin;
    private int missedBolus03StartHour;
    private int missedBolus03StartMin;
    private int missedBolus03EndHour;
    private int missedBolus03EndMin;
    private int missedBolus04StartHour;
    private int missedBolus04StartMin;
    private int missedBolus04EndHour;
    private int missedBolus04EndMin;

    public DanaRS_Packet_Bolus_Set_Bolus_Option() {
        super();
        opCode = BleCommandUtil.DANAR_PACKET__OPCODE_BOLUS__SET_BOLUS_OPTION;
    }

    public DanaRS_Packet_Bolus_Set_Bolus_Option(
            int extendedBolusOptionOnOff,
            int bolusCalculationOption,
            int missedBolusConfig,
            int missedBolus01StartHour,
            int missedBolus01StartMin,
            int missedBolus01EndHour,
            int missedBolus01EndMin,
            int missedBolus02StartHour,
            int missedBolus02StartMin,
            int missedBolus02EndHour,
            int missedBolus02EndMin,
            int missedBolus03StartHour,
            int missedBolus03StartMin,
            int missedBolus03EndHour,
            int missedBolus03EndMin,
            int missedBolus04StartHour,
            int missedBolus04StartMin,
            int missedBolus04EndHour,
            int missedBolus04EndMin) {
        this();
        this.extendedBolusOptionOnOff = extendedBolusOptionOnOff;
        this.bolusCalculationOption = bolusCalculationOption;
        this.missedBolusConfig = missedBolusConfig;
        this.missedBolus01StartHour = missedBolus01StartHour;
        this.missedBolus01StartMin = missedBolus01StartMin;
        this.missedBolus01EndHour = missedBolus01EndHour;
        this.missedBolus01EndMin = missedBolus01EndMin;
        this.missedBolus02StartHour = missedBolus02StartHour;
        this.missedBolus02StartMin = missedBolus02StartMin;
        this.missedBolus02EndHour = missedBolus02EndHour;
        this.missedBolus02EndMin = missedBolus02EndMin;
        this.missedBolus03StartHour = missedBolus03StartHour;
        this.missedBolus03StartMin = missedBolus03StartMin;
        this.missedBolus03EndHour = missedBolus03EndHour;
        this.missedBolus03EndMin = missedBolus03EndMin;
        this.missedBolus04StartHour = missedBolus04StartHour;
        this.missedBolus04StartMin = missedBolus04StartMin;
        this.missedBolus04EndHour = missedBolus04EndHour;
        this.missedBolus04EndMin = missedBolus04EndMin;

        if (L.isEnabled(L.PUMPCOMM)) {
            log.debug("Setting bolus options");
        }
    }

    @Override
    public byte[] getRequestParams() {
        byte[] request = new byte[19];
        request[0] = (byte) (extendedBolusOptionOnOff & 0xff);
        request[1] = (byte) (bolusCalculationOption & 0xff);
        request[2] = (byte) (missedBolusConfig & 0xff);

        request[3] = (byte) (missedBolus01StartHour & 0xff);
        request[4] = (byte) (missedBolus01StartMin & 0xff);
        request[5] = (byte) (missedBolus01EndHour & 0xff);
        request[6] = (byte) (missedBolus01EndMin & 0xff);

        request[7] = (byte) (missedBolus02StartHour & 0xff);
        request[8] = (byte) (missedBolus02StartMin & 0xff);
        request[9] = (byte) (missedBolus02EndHour & 0xff);
        request[10] = (byte) (missedBolus02EndMin & 0xff);

        request[11] = (byte) (missedBolus03StartHour & 0xff);
        request[12] = (byte) (missedBolus03StartMin & 0xff);
        request[13] = (byte) (missedBolus03EndHour & 0xff);
        request[14] = (byte) (missedBolus03EndMin & 0xff);

        request[15] = (byte) (missedBolus04StartHour & 0xff);
        request[16] = (byte) (missedBolus04StartMin & 0xff);
        request[17] = (byte) (missedBolus04EndHour & 0xff);
        request[18] = (byte) (missedBolus04EndMin & 0xff);
        return request;
    }

    @Override
    public void handleMessage(byte[] data) {
        int result = intFromBuff(data, 0, 1);
        if ( result != 0)
            failed = true;
        if (L.isEnabled(L.PUMPCOMM)) {
            if (result == 0)
                log.debug("Result OK");
            else
                log.error("Result Error: " + result);
        }
    }

    @Override
    public String getFriendlyName() {
        return "BOLUS__SET_BOLUS_OPTION";
    }
}
