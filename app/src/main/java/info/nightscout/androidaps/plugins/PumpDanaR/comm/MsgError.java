package info.nightscout.androidaps.plugins.PumpDanaR.comm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.plugins.Overview.events.EventOverviewBolusProgress;
import info.nightscout.utils.NSUpload;

public class MsgError extends MessageBase {
    private static Logger log = LoggerFactory.getLogger(MsgError.class);

    public MsgError() {
        SetCommand(0x0601);
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
            EventOverviewBolusProgress bolusingEvent = EventOverviewBolusProgress.getInstance();
            MsgBolusStop.stopped = true;
            bolusingEvent.status = errorString;
            MainApp.bus().post(bolusingEvent);
        }
        if (Config.logDanaMessageDetail)
            log.debug("Error detected: " + errorString);
        NSUpload.uploadError(errorString);
    }

}
