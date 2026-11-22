package app.aaps.ui.compose.alertKinds

import androidx.compose.runtime.Composable

/** None: iOS lets an app play only the sounds it ships. */
@Composable
internal actual fun rememberRingtonePicker(onPicked: (uri: String, title: String) -> Unit): ((current: String?) -> Unit)? = null
