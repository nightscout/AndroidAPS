package info.nightscout.androidaps.plugins.DanaR.comm;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.Services.Intents;
import info.nightscout.androidaps.plugins.Overview.events.EventOverviewBolusProgress;
import info.nightscout.client.data.DbLogger;
import info.nightscout.utils.DateUtil;

public class MsgOcclusion extends MessageBase {
    private static Logger log = LoggerFactory.getLogger(MsgOcclusion.class);

    public MsgOcclusion() {
        SetCommand(0x0601);
    }

    @Override
    public void handleMessage(byte[] bytes) {
        if (Config.logDanaMessageDetail)
            log.debug("Oclusion detected");
        EventOverviewBolusProgress bolusingEvent = EventOverviewBolusProgress.getInstance();
        MsgBolusStop.stopped = true;
        bolusingEvent.status = MainApp.sResources.getString(R.string.overview_bolusiprogress_occlusion);
        MainApp.bus().post(bolusingEvent);
        MainApp.getConfigBuilder().uploadDanaROcclusion();
    }

}
