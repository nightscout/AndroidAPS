package info.nightscout.androidaps.plugins.pump.danaRS.comm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;

import com.cozmo.danar.util.BleCommandUtil;

import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.bus.RxBus;
import info.nightscout.androidaps.plugins.treatments.Treatment;
import info.nightscout.androidaps.plugins.general.overview.events.EventOverviewBolusProgress;

public class DanaRS_Packet_Bolus_Set_Step_Bolus_Stop extends DanaRS_Packet {
    private Logger log = LoggerFactory.getLogger(L.PUMPCOMM);
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
        if (L.isEnabled(L.PUMPCOMM))
            log.debug("Stop bolus: amount: " + amount + " treatment: " + t.toString());
    }

    @Override
    public void handleMessage(byte[] data) {
        int result = intFromBuff(data, 0, 1);
        if (L.isEnabled(L.PUMPCOMM)) {
            if (result == 0)
                log.debug("Result OK");
            else {
                log.error("Result Error: " + result);
                failed = true;
            }
        }

        EventOverviewBolusProgress bolusingEvent = EventOverviewBolusProgress.INSTANCE;
        stopped = true;
        if (!forced) {
            t.insulin = amount;
            bolusingEvent.setStatus(MainApp.gs(R.string.overview_bolusprogress_delivered));
            bolusingEvent.setPercent(100);
        } else {
            bolusingEvent.setStatus(MainApp.gs(R.string.overview_bolusprogress_stoped));
        }
        RxBus.INSTANCE.send(bolusingEvent);
    }

    @Override
    public String getFriendlyName() {
        return "BOLUS__SET_STEP_BOLUS_STOP";
    }
}
