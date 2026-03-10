package app.aaps.core.objects.extensions

import app.aaps.core.data.model.TE.Arrow
import app.aaps.core.objects.R

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