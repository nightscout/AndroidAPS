package info.nightscout.androidaps.plugins.PumpDanaRS.comm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;

import com.cozmo.danar.util.BleCommandUtil;

import info.nightscout.androidaps.plugins.Treatments.Treatment;
import info.nightscout.androidaps.plugins.Overview.events.EventOverviewBolusProgress;

public class DanaRS_Packet_Notify_Delivery_Complete extends DanaRS_Packet {
    private static Logger log = LoggerFactory.getLogger(DanaRS_Packet_Notify_Delivery_Complete.class);

    private static Treatment t;
    private static double amount;
    public static boolean done;

    public DanaRS_Packet_Notify_Delivery_Complete() {
        super();
        type = BleCommandUtil.DANAR_PACKET__TYPE_NOTIFY;
        opCode = BleCommandUtil.DANAR_PACKET__OPCODE_NOTIFY__DELIVERY_COMPLETE;
    }

    public DanaRS_Packet_Notify_Delivery_Complete(double amount, Treatment t) {
        this();
        this.amount = amount;
        this.t = t;
        done = false;
    }

    @Override
    public void handleMessage(byte[] data) {
        double deliveredInsulin = byteArrayToInt(getBytes(data, DATA_START, 2)) / 100d;

        if (t != null) {
            t.insulin = deliveredInsulin;
            EventOverviewBolusProgress bolusingEvent = EventOverviewBolusProgress.getInstance();
            bolusingEvent.status = String.format(MainApp.gs(R.string.bolusdelivering), deliveredInsulin);
            bolusingEvent.t = t;
            bolusingEvent.percent = Math.min((int) (deliveredInsulin / amount * 100), 100);
            done = true;
            MainApp.bus().post(bolusingEvent);
        }

        if (Config.logDanaMessageDetail)
            log.debug("Delivered insulin: " + deliveredInsulin);
    }

    @Override
    public String getFriendlyName() {
        return "NOTIFY__DELIVERY_COMPLETE";
    }
}
