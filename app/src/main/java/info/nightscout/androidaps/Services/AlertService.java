package info.nightscout.androidaps.Services;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.preference.PreferenceManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;

import info.nightscout.androidaps.Config;

public class AlertService extends Service {
    private static Logger log = LoggerFactory.getLogger(AlertService.class);

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (Config.logFunctionCalls)
            log.debug("onStartCommand");

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        long lastAlert = preferences.getLong("lastAlert", 0);
        long currentTime = new Date().getTime();

        //if (!PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getBoolean("masterSwitch", false)) {
        //    stopSelf(startId);
        //    log.debug("Alert posponed - master switch disabled");
        //} else
        //if ((currentTime - lastAlert) < 15 * 60 * 1000) {
        //    stopSelf(startId);
        //    log.debug("Alert posponed");
        //} else
        {

            AlertMessage alert = new AlertMessage(getApplicationContext());

            if (intent != null) {
                String alertText = intent.getStringExtra("alertText");
                if (alertText != null) {
                    alert.setText(alertText);
                }

                alert.setOnDismiss(new Runnable() {

                    @Override
                    public void run() {
                        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
                        SharedPreferences.Editor editor = preferences.edit();
                        editor.putLong("lastAlert", new Date().getTime());
                        editor.commit();
                        AlertService.this.stopSelf();
                    }
                });

                alert.showMessage();
            }
        }
        if (Config.logFunctionCalls)
            log.debug("onStartCommand end");
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

}
