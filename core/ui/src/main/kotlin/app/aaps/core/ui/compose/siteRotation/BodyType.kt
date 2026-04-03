package app.aaps.core.ui.compose.siteRotation

import android.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import app.aaps.core.data.model.TE
import app.aaps.core.ui.compose.icons.library.ChildBack
import app.aaps.core.ui.compose.icons.library.ChildBackPaths
import app.aaps.core.ui.compose.icons.library.ChildFront
import app.aaps.core.ui.compose.icons.library.ChildFrontPaths
import app.aaps.core.ui.compose.icons.library.ManBack
import app.aaps.core.ui.compose.icons.library.ManBackPaths
import app.aaps.core.ui.compose.icons.library.ManFront
import app.aaps.core.ui.compose.icons.library.ManFrontPaths
import app.aaps.core.ui.compose.icons.library.WomanBack
import app.aaps.core.ui.compose.icons.library.WomanBackPaths
import app.aaps.core.ui.compose.icons.library.WomanFront
import app.aaps.core.ui.compose.icons.library.WomanFrontPaths

enum class BodyType(
    val value: Int,
    val sizeRatio: Float,
    val frontImage: ImageVector,
    val backImage: ImageVector,
    val frontZones: List<Pair<TE.Location, Path>>,
    val backZones: List<Pair<TE.Location, Path>>
) {

    MAN(0, 1f, ManFront, ManBack, ManFrontPaths.zones, ManBackPaths.zones),
    WOMAN(1, 0.95f, WomanFront, WomanBack, WomanFrontPaths.zones, WomanBackPaths.zones),
    CHILD(2, 0.60f, ChildFront, ChildBack, ChildFrontPaths.zones, ChildBackPaths.zones);

    companion object {

        fun fromPref(pref: Int): BodyType = entries.firstOrNull { it.value == pref } ?: MAN
    }
}
