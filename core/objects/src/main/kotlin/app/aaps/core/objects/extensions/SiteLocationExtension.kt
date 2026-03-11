package app.aaps.core.objects.extensions

import androidx.compose.ui.graphics.vector.ImageVector
import app.aaps.core.data.model.TE.Arrow
import app.aaps.core.objects.R
import app.aaps.core.ui.compose.icons.IcArrowFlat
import app.aaps.core.ui.compose.icons.IcArrowFortyfiveDown
import app.aaps.core.ui.compose.icons.IcArrowFortyfiveUp
import app.aaps.core.ui.compose.icons.IcArrowInvalid
import app.aaps.core.ui.compose.icons.IcArrowLeft
import app.aaps.core.ui.compose.icons.IcArrowLeftDown
import app.aaps.core.ui.compose.icons.IcArrowLeftUp
import app.aaps.core.ui.compose.icons.IcArrowNone
import app.aaps.core.ui.compose.icons.IcArrowSimpleDown
import app.aaps.core.ui.compose.icons.IcArrowSimpleUp
import app.aaps.core.ui.compose.icons.library.unused.IcArrowCenter

fun Arrow.directionToIcon(): Int =
    when (this) {
        Arrow.UP         -> R.drawable.ic_up
        Arrow.UP_RIGHT   -> R.drawable.ic_up_right
        Arrow.RIGHT      -> R.drawable.ic_right
        Arrow.DOWN_RIGHT -> R.drawable.ic_down_right
        Arrow.DOWN       -> R.drawable.ic_down
        Arrow.DOWN_LEFT  -> R.drawable.ic_down_left
        Arrow.LEFT       -> R.drawable.ic_left
        Arrow.UP_LEFT    -> R.drawable.ic_up_left
        Arrow.CENTER     -> R.drawable.ic_center
        Arrow.NONE       -> R.drawable.ic_none
    }

fun Arrow.directionToComposeIcon(): ImageVector =
    when (this) {
        Arrow.UP         -> IcArrowSimpleUp
        Arrow.UP_RIGHT   -> IcArrowFortyfiveUp
        Arrow.RIGHT      -> IcArrowFlat
        Arrow.DOWN_RIGHT -> IcArrowFortyfiveDown
        Arrow.DOWN       -> IcArrowSimpleDown
        Arrow.DOWN_LEFT  -> IcArrowLeftDown
        Arrow.LEFT       -> IcArrowLeft
        Arrow.UP_LEFT    -> IcArrowLeftUp
        Arrow.CENTER     -> IcArrowCenter
        Arrow.NONE       -> IcArrowNone
    }

fun Arrow.Companion.fromIcon(drawRes: Int): Arrow = when (drawRes) {
    R.drawable.ic_up         -> Arrow.UP
    R.drawable.ic_up_right   -> Arrow.UP_RIGHT
    R.drawable.ic_right      -> Arrow.RIGHT
    R.drawable.ic_down_right -> Arrow.DOWN_RIGHT
    R.drawable.ic_down       -> Arrow.DOWN
    R.drawable.ic_down_left  -> Arrow.DOWN_LEFT
    R.drawable.ic_left       -> Arrow.LEFT
    R.drawable.ic_up_left    -> Arrow.UP_LEFT
    R.drawable.ic_center     -> Arrow.CENTER
    R.drawable.ic_none       -> Arrow.NONE
    else                     -> Arrow.NONE
}