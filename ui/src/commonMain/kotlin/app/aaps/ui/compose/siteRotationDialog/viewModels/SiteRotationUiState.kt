package app.aaps.ui.compose.siteRotationDialog.viewModels

import app.aaps.core.data.model.TE
import app.aaps.core.ui.compose.siteRotation.BodyType

data class SiteRotationUiState(
    val entries: List<TE> = emptyList(),
    val isLoading: Boolean = false,
    val showBodyType: BodyType = BodyType.MAN,
    val selectedLocation: TE.Location = TE.Location.NONE,   // NONE = no filter
    val isEdited: Boolean = false,                          // Editor Only
    val showPumpSites: Boolean = true,
    val showCgmSites: Boolean = true,
    val editedTe: TE? = null                                // Editor Only
) {

    // filteredEntries dynamically filtered for siteEntryList
    val filteredEntries: List<TE>
        get() = entries.filter { te ->
            // type filter
            val typeMatch = when (te.type) {
                TE.Type.CANNULA_CHANGE -> showPumpSites
                TE.Type.SENSOR_CHANGE  -> showCgmSites
                else                   -> false
            }
            // location filter if selected
            val locationMatch = selectedLocation == TE.Location.NONE || te.location == selectedLocation

            // Always show the entry being edited
            val isEditedEntry = editedTe != null && te.timestamp == editedTe.timestamp

            (typeMatch && locationMatch) || isEditedEntry
        }

    // filteredLocationColor dynamically filtered for Location Color calculation
    val filteredLocationColor: List<TE>
        get() = entries.filter { te ->
            when (te.type) {
                TE.Type.CANNULA_CHANGE -> showPumpSites
                TE.Type.SENSOR_CHANGE  -> showCgmSites
                else                   -> false
            }
        }
}
