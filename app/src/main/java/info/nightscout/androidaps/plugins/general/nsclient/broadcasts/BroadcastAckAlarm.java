package info.nightscout.androidaps.plugins.general.nsclient.broadcasts;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.services.Intents;
import info.nightscout.androidaps.plugins.general.nsclient.data.NSAlarm;
import info.nightscout.androidaps.utils.SP;

/**
 * Created by mike on 11.06.2017.
 */

public class BroadcastAckAlarm {

    public static void handleClearAlarm(NSAlarm originalAlarm, Context context, long silenceTimeInMsec) {

        Bundle bundle = new Bundle();
        bundle.putInt("level", originalAlarm.getLevel());
        bundle.putString("group", originalAlarm.getGroup());
        bundle.putLong("silenceTime", silenceTimeInMsec);
        Intent intent = new Intent(Intents.ACTION_ACK_ALARM);
        intent.putExtras(bundle);
        intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
        LocalBroadcastManager.getInstance(MainApp.instance()).sendBroadcast(intent);

        if(SP.getBoolean(R.string.key_nsclient_localbroadcasts, false)) {
            bundle = new Bundle();
            bundle.putInt("level", originalAlarm.getLevel());
            bundle.putString("group", originalAlarm.getGroup());
            bundle.putLong("silenceTime", silenceTimeInMsec);
            intent = new Intent(Intents.ACTION_ACK_ALARM);
            intent.putExtras(bundle);
            intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
            context.sendBroadcast(intent);
        }
    }

}
