package app.aaps.e2e

import android.content.Context
import app.aaps.settingsstore.AndroidStorage
import java.io.File

/**
 * Wipes the app's settings for a fresh start, since `pm clear` would kill the in-process test.
 *
 * Both kinds: the encrypted settings store, which holds the settings, and every SharedPreferences file,
 * which some pump drivers still use for their own state.
 */
fun clearAppSettings(context: Context) {
    AndroidStorage.open(context).store.edit(commit = true) { clear() }
    File(context.applicationInfo.dataDir, "shared_prefs").listFiles()?.forEach { f ->
        if (f.name.endsWith(".xml"))
            context.getSharedPreferences(f.name.removeSuffix(".xml"), Context.MODE_PRIVATE).edit().clear().commit()
    }
}
