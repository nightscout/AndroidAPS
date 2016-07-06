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

public class AlarmService extends Service {
    private static Logger log = LoggerFactory.getLogger(AlarmService.class);

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (Config.logFunctionCalls)
            log.debug("onStartCommand");

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        long lastAlarm = preferences.getLong("lastAlarm", 0);
        long currentTime = new Date().getTime();

        //if (!PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getBoolean("masterSwitch", false)) {
        //    stopSelf(startId);
        //    log.debug("Alarm posponed - master switch disabled");
        //} else
        //if ((currentTime - lastAlarm) < 15 * 60 * 1000) {
        //    stopSelf(startId);
        //    log.debug("Alarm posponed");
        //} else
        {

            AlarmMessage alarm = new AlarmMessage(getApplicationContext());

            if (intent != null) {
                String alarmText = intent.getStringExtra("alarmText");
                if (alarmText != null) {
                    alarm.setText(alarmText);
                }

                alarm.setOnDismiss(new Runnable() {

                    @Override
                    public void run() {
                        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
                        SharedPreferences.Editor editor = preferences.edit();
                        editor.putLong("lastAlarm", new Date().getTime());
                        editor.commit();
                        AlarmService.this.stopSelf();
                    }
                });

                alarm.showMessage();
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
