package app.aaps.ui.compose.alertKinds

import androidx.compose.runtime.Composable

/** None: the desktop has no alerts. */
@Composable
internal actual fun rememberRingtonePicker(onPicked: (uri: String, title: String) -> Unit): ((current: String?) -> Unit)? = null
