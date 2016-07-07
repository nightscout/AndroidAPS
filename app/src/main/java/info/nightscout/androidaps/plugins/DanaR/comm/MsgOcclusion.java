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
import info.nightscout.androidaps.Services.Intents;
import info.nightscout.androidaps.plugins.DanaR.events.EventDanaRBolusProgress;
import info.nightscout.client.data.DbLogger;
import info.nightscout.utils.DateUtil;

public class MsgOcclusion extends DanaRMessage {
    private static Logger log = LoggerFactory.getLogger(MsgOcclusion.class);

    public MsgOcclusion() {
        SetCommand(0x0601);
    }

    @Override
    public void handleMessage(byte[] bytes) {
        if (Config.logDanaMessageDetail)
            log.debug("Oclusion detected");
        EventDanaRBolusProgress bolusingEvent = EventDanaRBolusProgress.getInstance();
        MsgBolusStop.stopped = true;
        bolusingEvent.sStatus = "Oclusion";
        MainApp.bus().post(bolusingEvent);
        sendToNSClient();
    }

    public void sendToNSClient() {
        Context context = MainApp.instance().getApplicationContext();
        Bundle bundle = new Bundle();
        bundle.putString("action", "dbAdd");
        bundle.putString("collection", "treatments");
        JSONObject data = new JSONObject();
        try {
            data.put("eventType", "Announcement");
            data.put("created_at", DateUtil.toISOString(new Date()));
            data.put("notes", "Occlusion detected");
            data.put("isAnnouncement", true);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        bundle.putString("data", data.toString());
        Intent intent = new Intent(Intents.ACTION_DATABASE);
        intent.putExtras(bundle);
        intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
        context.sendBroadcast(intent);
        DbLogger.dbAdd(intent, data.toString(), MsgOcclusion.class);
    }
}
