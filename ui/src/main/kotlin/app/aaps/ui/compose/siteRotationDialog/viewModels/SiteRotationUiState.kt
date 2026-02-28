package app.aaps.ui.compose.siteRotationDialog.viewModels

import androidx.compose.ui.graphics.vector.ImageVector
import app.aaps.core.data.model.TE
import app.aaps.core.ui.compose.icons.library.ChildBack
import app.aaps.core.ui.compose.icons.library.ChildFront
import app.aaps.core.ui.compose.icons.library.ManBack
import app.aaps.core.ui.compose.icons.library.ManFront
import app.aaps.core.ui.compose.icons.library.WomanBack
import app.aaps.core.ui.compose.icons.library.WomanFront

enum class BodyType(val value: Int, val frontImage: ImageVector, val backImage: ImageVector) {
    MAN(0, ManFront, ManBack),
    WOMAN(1, WomanFront, WomanBack),
    CHILD(2, ChildFront, ChildBack);

    companion object {
        fun fromPref(pref: Int): BodyType = entries.firstOrNull { it.value == pref } ?: MAN
    }
}

data class SiteRotationUiState(
    val entries: List<TE> = emptyList(),
    val isLoading: Boolean = false,
    val showFrontView: Boolean = true,
    val showBackView: Boolean = true,
    val showBodyType: BodyType = BodyType.MAN,
    val selectedLocation: TE.Location = TE.Location.NONE,   // NONE = no filter
    val selectedArrow: TE.Arrow = TE.Arrow.NONE,            // Editor Only
    val selectedNote: String = "",                           // Editor Only
    val isEdited: Boolean = false,                           // Editor Only
    val showPumpSites: Boolean = true,
    val showCgmSites: Boolean = true
) {
    // filtererEntries dynamically filtered
    val filteredEntries: List<TE>
        get() = entries.filter { te ->
            // type filter
            val typeMatch = when (te.type) {
                TE.Type.CANNULA_CHANGE -> showPumpSites
                TE.Type.SENSOR_CHANGE -> showCgmSites
                else -> false
            }
            // location filter if selected
            val locationMatch = selectedLocation == TE.Location.NONE || te.location == selectedLocation
            typeMatch && locationMatch
        }
}
