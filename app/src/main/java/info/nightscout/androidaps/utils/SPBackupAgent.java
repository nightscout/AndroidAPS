package info.nightscout.androidaps.utils;

import android.app.backup.BackupAgentHelper;
import android.app.backup.SharedPreferencesBackupHelper;

public class SPBackupAgent extends BackupAgentHelper {

    @Override
    public void onCreate() {
        // API 24
        final String PREFS = getApplicationContext().getPackageName() + "_preferences";
        final String PREFS_BACKUP_KEY = "SP";
        SharedPreferencesBackupHelper helper =
                new SharedPreferencesBackupHelper(this, PREFS);
        addHelper(PREFS_BACKUP_KEY, helper);
    }
}