package app.aaps.ui.compose.siteRotationDialog.viewModels

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

enum class BodyType(val value: Int,
                    val sizeRatio: Float,
                    val frontImage: ImageVector,
                    val backImage: ImageVector,
                    val frontZones: List<Pair<TE.Location, Path>>,
                    val backZones: List<Pair<TE.Location, Path>>) {
    MAN(0, 1f, ManFront, ManBack, ManFrontPaths.zones, ManBackPaths.zones),
    WOMAN(1, 0.95f, WomanFront, WomanBack, WomanFrontPaths.zones, WomanBackPaths.zones),
    CHILD(2, 0.60f,ChildFront, ChildBack, ChildFrontPaths.zones, ChildBackPaths.zones);

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
    val isEdited: Boolean = false,                          // Editor Only
    val showPumpSites: Boolean = true,
    val showCgmSites: Boolean = true,
    val editedTe: TE? = null                                // Editor Only
) {
    // filtererEntries dynamically filtered for siteEntryList
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
    // filteredLocationColor dynamically filtered for Location Color calculation
    val filteredLocationColor: List<TE>
        get() = entries.filter { te ->
            when (te.type) {
                TE.Type.CANNULA_CHANGE -> showPumpSites
                TE.Type.SENSOR_CHANGE -> showCgmSites
                else -> false
            }
        }
}
