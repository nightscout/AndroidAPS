package info.nightscout.androidaps.plugins.PumpDanaRS.comm;

import com.cozmo.danar.util.BleCommandUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Calendar;
import java.util.GregorianCalendar;

import info.nightscout.androidaps.Config;

public class DanaRS_Packet_APS_Set_Event_History extends DanaRS_Packet {
    private static Logger log = LoggerFactory.getLogger(DanaRS_Packet_APS_Set_Event_History.class);

    private int type;
    private long time;
    public int param1;
    public int param2;

    public DanaRS_Packet_APS_Set_Event_History() {
        super();
        opCode = BleCommandUtil.DANAR_PACKET__OPCODE__APS_SET_EVENT_HISTORY;
    }

    public DanaRS_Packet_APS_Set_Event_History(int type, long time, int param1, int param2) {
        this();

        this.type = type;
        this.time = time;
        this.param1 = param1;
        this.param2 = param2;
    }

    @Override
    public byte[] getRequestParams() {
        GregorianCalendar cal = new GregorianCalendar();
        cal.setTimeInMillis(time);
        int year = cal.get(Calendar.YEAR) - 1900 - 100;
        int month = cal.get(Calendar.MONTH) + 1;
        int day = cal.get(Calendar.DAY_OF_MONTH);
        int hour = cal.get(Calendar.HOUR_OF_DAY);
        int min = cal.get(Calendar.MINUTE);
        int sec = cal.get(Calendar.SECOND);

        byte[] request = new byte[11];
        request[0] = (byte) (type & 0xff);
        request[1] = (byte) (year & 0xff);
        request[2] = (byte) (month & 0xff);
        request[3] = (byte) (day & 0xff);
        request[4] = (byte) (hour & 0xff);
        request[5] = (byte) (min & 0xff);
        request[6] = (byte) (sec & 0xff);
        request[7] = (byte) ((param1 >>> 8) & 0xff);
        request[8] = (byte) (param1 & 0xff);
        request[9] = (byte) ((param2 >>> 8) & 0xff);
        request[10] = (byte) (param2 & 0xff);
        return request;
    }

    @Override
    public void handleMessage(byte[] data) {
        int result = intFromBuff(data, 0, 1);
        if (result != 0) {
            failed = true;
            log.error("Set history entry result: " + result + " FAILED!!!");
        } else {
            if (Config.logDanaMessageDetail)
                log.debug("Set history entry result: " + result);
        }
    }

    @Override
    public String getFriendlyName() {
        return "APS_SET_EVENT_HISTORY";
    }
}
