package app.aaps.ui.compose.alertKinds

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.IntentCompat

/** Android's ringtone picker: every sound of the phone (ringtones, notification sounds, alarms), no "default" or "silent". */
@Composable
internal actual fun rememberRingtonePicker(onPicked: (uri: String, title: String) -> Unit): ((current: String?) -> Unit)? {
    val context = LocalContext.current
    val picked by rememberUpdatedState(onPicked)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val data = result.data ?: return@rememberLauncherForActivityResult
        val uri = IntentCompat.getParcelableExtra(data, RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
            ?: return@rememberLauncherForActivityResult
        // The name as the phone shows it now; kept with the choice, so the list does not open the file to show it
        val title = RingtoneManager.getRingtone(context, uri)?.getTitle(context) ?: uri.lastPathSegment ?: uri.toString()
        picked(uri.toString(), title)
    }
    return { current ->
        launcher.launch(
            Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALL)
                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, false)
                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                current?.let { putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(it)) }
            }
        )
    }
}
