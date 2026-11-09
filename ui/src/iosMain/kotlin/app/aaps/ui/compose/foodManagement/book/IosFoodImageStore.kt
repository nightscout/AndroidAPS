package app.aaps.ui.compose.foodManagement.book

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import org.jetbrains.skia.Image

/** PROOF OF CONCEPT: iOS does not keep product photos yet. Foods are shown without them. */
@ContributesBinding(AppScope::class, binding = binding<FoodImageStore>())
@Inject
class IosFoodImageStore : NoFoodImageStore()

internal actual fun decodeFoodImage(bytes: ByteArray): ImageBitmap? =
    runCatching { Image.makeFromEncoded(bytes).toComposeImageBitmap() }.getOrNull()
