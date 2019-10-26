package info.nightscout.androidaps.plugins.general.nsclient.broadcasts;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.services.Intents;
import info.nightscout.androidaps.plugins.general.nsclient.data.NSSettingsStatus;
import info.nightscout.androidaps.plugins.general.nsclient.services.NSClientService;
import info.nightscout.androidaps.utils.SP;

/**
 * Created by mike on 24.02.2016.
 */
public class BroadcastStatus {
    private static Logger log = LoggerFactory.getLogger(L.NSCLIENT);

    public static void handleNewStatus(NSSettingsStatus status, Context context, boolean isDelta) {
        LocalBroadcastManager.getInstance(MainApp.instance())
                .sendBroadcast(createIntent(status, isDelta));

        if(SP.getBoolean(R.string.key_nsclient_localbroadcasts, false)) {
            context.sendBroadcast(createIntent(status, isDelta));
        }
    }

    private static Intent createIntent(NSSettingsStatus status, boolean isDelta) {
        Bundle bundle = new Bundle();

        try {
            bundle.putString("nsclientversionname", MainApp.instance().getPackageManager().getPackageInfo(MainApp.instance().getPackageName(), 0).versionName);
            bundle.putInt("nsclientversioncode", MainApp.instance().getPackageManager().getPackageInfo(MainApp.instance().getPackageName(), 0).versionCode);
        } catch (PackageManager.NameNotFoundException e) {
            log.error("Unhandled exception", e);
        }

        bundle.putString("nightscoutversionname", NSClientService.nightscoutVersionName);
        bundle.putInt("nightscoutversioncode", NSClientService.nightscoutVersionCode);
        bundle.putString("status", status.getData().toString());
        bundle.putBoolean("delta", isDelta);
        Intent intent = new Intent(Intents.ACTION_NEW_STATUS);
        intent.putExtras(bundle);
        intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);

        return intent;
    }
}
