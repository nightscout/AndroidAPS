package app.aaps.ui.compose.alertKinds

import androidx.compose.runtime.Composable

/**
 * The phone's own ringtone picker (`_docs/ALARMS.md`, step 2e), or null where there is none: iOS lets an app play only
 * the sounds it ships, and the desktop has no alerts. The returned function opens the picker, with the ringtone chosen
 * now marked; [onPicked] gets the ringtone's uri and its name.
 */
@Composable
internal expect fun rememberRingtonePicker(onPicked: (uri: String, title: String) -> Unit): ((current: String?) -> Unit)?
