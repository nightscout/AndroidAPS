package app.aaps.ui.compose.foodManagement

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Not done yet on this platform (proof of concept): no photo is taken, and none is kept. */
internal actual val foodPhotoCameraAvailable: Boolean = false

@Composable
internal actual fun FoodPhotoCamera(onPhoto: (ByteArray) -> Unit, modifier: Modifier) = Unit
