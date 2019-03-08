package info.nightscout.androidaps.plugins.general.nsclient.data;

import android.content.Intent;
import android.content.pm.ResolveInfo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.utils.ToastUtils;

/**
 * Created by mike on 02.07.2016.
 */
public class DbLogger {
    private static Logger log = LoggerFactory.getLogger(L.NSCLIENT);

    public static void dbAdd(Intent intent, String data) {
        List<ResolveInfo> q = MainApp.instance().getApplicationContext().getPackageManager().queryBroadcastReceivers(intent, 0);
        if (q.size() < 1) {
            ToastUtils.showToastInUiThread(MainApp.instance().getApplicationContext(), MainApp.gs(R.string.nsclientnotinstalled));
            log.error("DBADD No receivers");
        } else if (L.isEnabled(L.NSCLIENT)) {
            if (L.isEnabled(L.NSCLIENT))
                log.debug("DBADD dbAdd " + q.size() + " receivers " + data);
        }
    }

    public static void dbRemove(Intent intent, String data) {
        List<ResolveInfo> q = MainApp.instance().getApplicationContext().getPackageManager().queryBroadcastReceivers(intent, 0);
        if (q.size() < 1) {
            ToastUtils.showToastInUiThread(MainApp.instance().getApplicationContext(), MainApp.gs(R.string.nsclientnotinstalled));
            log.error("DBREMOVE No receivers");
        } else if (L.isEnabled(L.NSCLIENT)) {
            if (L.isEnabled(L.NSCLIENT))
                log.debug("DBREMOVE dbRemove " + q.size() + " receivers " + data);
        }
    }
}
