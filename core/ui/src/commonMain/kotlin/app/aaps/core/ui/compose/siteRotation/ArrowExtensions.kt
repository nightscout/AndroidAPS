package app.aaps.core.ui.compose.siteRotation

import androidx.compose.ui.graphics.vector.ImageVector
import app.aaps.core.data.model.TE.Arrow
import app.aaps.core.ui.compose.icons.IcArrowFlat
import app.aaps.core.ui.compose.icons.IcArrowFortyfiveDown
import app.aaps.core.ui.compose.icons.IcArrowFortyfiveUp
import app.aaps.core.ui.compose.icons.IcArrowLeft
import app.aaps.core.ui.compose.icons.IcArrowLeftDown
import app.aaps.core.ui.compose.icons.IcArrowLeftUp
import app.aaps.core.ui.compose.icons.IcArrowNone
import app.aaps.core.ui.compose.icons.IcArrowSimpleDown
import app.aaps.core.ui.compose.icons.IcArrowSimpleUp
import app.aaps.core.ui.compose.icons.library.unused.IcArrowCenter

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
