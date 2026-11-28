package app.aaps.ui.compose.foodManagement

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * True where [FoodPhotoCamera] can take a photo. Where it is false, the editor does not offer to
 * take one.
 */
internal expect val foodPhotoCameraAvailable: Boolean

/**
 * Shows the camera with a button to take a photo of a food. [onPhoto] gets a small JPEG (about
 * [FOOD_PHOTO_MAX_SIZE] pixels on its longest side), turned the right way up.
 *
 * PROOF OF CONCEPT.
 */
@Composable
internal expect fun FoodPhotoCamera(onPhoto: (ByteArray) -> Unit, modifier: Modifier)

/** A food photo is a small picture: a backup with many of them stays small. */
internal const val FOOD_PHOTO_MAX_SIZE = 512
