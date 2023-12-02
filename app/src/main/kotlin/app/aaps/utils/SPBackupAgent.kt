package app.aaps.utils

import android.app.backup.BackupAgentHelper
import android.app.backup.SharedPreferencesBackupHelper

@Suppress("LocalVariableName", "unused")
class SPBackupAgent : BackupAgentHelper() {

    override fun onCreate() {
        // API 24
        val PREFS = applicationContext.packageName + "_preferences"
        val PREFS_BACKUP_KEY = "SP"
        val helper = SharedPreferencesBackupHelper(this, PREFS)
        addHelper(PREFS_BACKUP_KEY, helper)
    }
}