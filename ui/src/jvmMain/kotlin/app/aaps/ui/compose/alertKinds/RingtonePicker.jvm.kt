package app.aaps.ui.compose.alertKinds

import androidx.compose.runtime.Composable

/** None: the desktop has only the system beep. */
@Composable
internal actual fun rememberRingtonePicker(onPicked: (uri: String, title: String) -> Unit): ((current: String?) -> Unit)? = null
