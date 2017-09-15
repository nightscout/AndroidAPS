package info.nightscout.androidaps.plugins.PumpDanaRS.comm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;

import com.cozmo.danar.util.BleCommandUtil;

import info.nightscout.androidaps.db.Treatment;
import info.nightscout.androidaps.plugins.Overview.events.EventOverviewBolusProgress;

public class DanaRS_Packet_Bolus_Set_Step_Bolus_Stop extends DanaRS_Packet {
    private static Logger log = LoggerFactory.getLogger(DanaRS_Packet_Bolus_Set_Step_Bolus_Stop.class);
    private static Treatment t;
    private static Double amount;

    public static boolean stopped = false;
    public static boolean forced = false;

    public DanaRS_Packet_Bolus_Set_Step_Bolus_Stop() {
        super();
        opCode = BleCommandUtil.DANAR_PACKET__OPCODE_BOLUS__SET_STEP_BOLUS_STOP;
    }

    public DanaRS_Packet_Bolus_Set_Step_Bolus_Stop(Double amount, Treatment t) {
        this();
        this.t = t;
        this.amount = amount;
        forced = false;
        stopped = false;
    }

    @Override
    public void handleMessage(byte[] data) {
        int dataIndex = DATA_START;
        int dataSize = 1;
        int status = byteArrayToInt(getBytes(data, dataIndex, dataSize));

        if (Config.logDanaMessageDetail) {
            log.debug("Result: " + status);
        }
        EventOverviewBolusProgress bolusingEvent = EventOverviewBolusProgress.getInstance();
        stopped = true;
        if (!forced) {
            t.insulin = amount;
            bolusingEvent.status = MainApp.sResources.getString(R.string.overview_bolusprogress_delivered);
            bolusingEvent.percent = 100;
        } else {
            bolusingEvent.status = MainApp.sResources.getString(R.string.overview_bolusprogress_stoped);
        }
        MainApp.bus().post(bolusingEvent);
    }

    @Override
    public String getFriendlyName() {
        return "BOLUS__SET_STEP_BOLUS_STOP";
    }
}
