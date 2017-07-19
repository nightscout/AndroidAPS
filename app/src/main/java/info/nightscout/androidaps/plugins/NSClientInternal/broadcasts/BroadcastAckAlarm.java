package info.nightscout.androidaps.plugins.NSClientInternal.broadcasts;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.os.Bundle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import info.nightscout.androidaps.Services.Intents;
import info.nightscout.androidaps.plugins.NSClientInternal.data.NSAlarm;
import info.nightscout.utils.SP;

/**
 * Created by mike on 11.06.2017.
 */

public class BroadcastAckAlarm {
    private static Logger log = LoggerFactory.getLogger(BroadcastAckAlarm.class);

    public static void handleClearAlarm(NSAlarm originalAlarm, Context context, long silenceTimeInMsec) {
        if(!SP.getBoolean("nsclient_localbroadcasts", true)) return;
        Bundle bundle = new Bundle();
        bundle.putInt("level", originalAlarm.getLevel());
        bundle.putString("group", originalAlarm.getGroup());
        bundle.putLong("silenceTime", silenceTimeInMsec);
        Intent intent = new Intent(Intents.ACTION_ACK_ALARM);
        intent.putExtras(bundle);
        intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
        context.sendBroadcast(intent);
    }

}
