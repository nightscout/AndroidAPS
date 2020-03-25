package info.nightscout.androidaps.utils;

import android.app.backup.BackupAgentHelper;
import android.app.backup.SharedPreferencesBackupHelper;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import info.nightscout.androidaps.MainApp;

public class SPBackupAgent extends BackupAgentHelper {

    // API 24
    //static final String PREFS = PreferenceManager.getDefaultSharedPreferencesName(MainApp.instance().getApplicationContext());
    static final String PREFS = MainApp.instance().getApplicationContext().getPackageName() + "_preferences";

    static final String PREFS_BACKUP_KEY = "SP";

    @Override
    public void onCreate() {
        SharedPreferencesBackupHelper helper =
                new SharedPreferencesBackupHelper(this, PREFS);
        addHelper(PREFS_BACKUP_KEY, helper);
    }
}