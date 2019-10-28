package info.nightscout.androidaps.plugins.pump.danaR.comm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.bus.RxBus;
import info.nightscout.androidaps.plugins.general.overview.events.EventOverviewBolusProgress;
import info.nightscout.androidaps.plugins.general.nsclient.NSUpload;

public class MsgError extends MessageBase {
    private static Logger log = LoggerFactory.getLogger(L.PUMPCOMM);

    public MsgError() {
        SetCommand(0x0601);
        if (L.isEnabled(L.PUMPCOMM))
            log.debug("New message");
    }

    @Override
    public void handleMessage(byte[] bytes) {
        int errorCode = intFromBuff(bytes, 0, 1);
        String errorString = "";

        switch (errorCode) {
            case 1:
            case 2:
            case 3: // Pump error
                errorString = MainApp.gs(R.string.pumperror) + " " + errorCode;
                break;
            case 4: // Shutdown
                errorString = MainApp.gs(R.string.pumpshutdown);
                break;
            case 5: // Occlusion
                errorString = MainApp.gs(R.string.occlusion);
                break;
            case 7: // Low Battery
                errorString = MainApp.gs(R.string.lowbattery);
                break;
            case 8: // Battery 0%
                errorString = MainApp.gs(R.string.batterydischarged);
                break;
        }

        if (errorCode < 8) { // bolus delivering stopped
            EventOverviewBolusProgress bolusingEvent = EventOverviewBolusProgress.INSTANCE;
            MsgBolusStop.stopped = true;
            bolusingEvent.setStatus(errorString);
            RxBus.INSTANCE.send(bolusingEvent);
            failed=true;
        }
        if (L.isEnabled(L.PUMPCOMM))
            log.debug("Error detected: " + errorString);
        NSUpload.uploadError(errorString);
    }

}
