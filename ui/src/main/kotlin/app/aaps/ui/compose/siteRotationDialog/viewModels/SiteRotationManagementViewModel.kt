package app.aaps.ui.compose.siteRotationDialog.viewModels

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.data.model.IDs
import app.aaps.core.data.model.TE
import app.aaps.core.data.time.T
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.Translator
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.compose.siteRotation.BodyType
import app.aaps.core.ui.compose.siteRotation.SiteEntryDisplayData
import app.aaps.core.ui.compose.siteRotation.toDisplayData
import app.aaps.ui.R
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
@Stable
class SiteRotationManagementViewModel @Inject constructor(
    private val rh: ResourceHelper,
    private val uel: UserEntryLogger,
    private val dateUtil: DateUtil,
    private val persistenceLayer: PersistenceLayer,
    private val preferences: Preferences,
    private val translator: Translator
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        SiteRotationUiState(
            isLoading = true,
            showBodyType = BodyType.fromPref(preferences.get(IntKey.SiteRotationUserProfile)),
            showPumpSites = preferences.get(BooleanKey.SiteRotationManagePump),
            showCgmSites = preferences.get(BooleanKey.SiteRotationManageCgm)
        )
    )
    val uiState: StateFlow<SiteRotationUiState> = _uiState.asStateFlow()
    private val millsToThePast = T.days(45).msecs()

    // Editing state tracking
    private var loadedNote: String? = null
    private var loadedLocation: TE.Location? = null
    private var loadedArrow: TE.Arrow? = null

    init {
        setupEventListeners()
        loadEntries(showLoading = true)
    }

    private fun setupEventListeners() {
        persistenceLayer.observeChanges(TE::class.java)
            .onEach { loadEntries() }
            .launchIn(viewModelScope)
    }

    fun loadEntries(showLoading: Boolean = false) {
        viewModelScope.launch {
            if (showLoading) _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val now = System.currentTimeMillis()
                val entries = persistenceLayer.getTherapyEventDataFromTime(now - millsToThePast, false)
                    .filter { te ->
                        te.type == TE.Type.CANNULA_CHANGE || te.type == TE.Type.SENSOR_CHANGE
                    }
                _uiState.value = _uiState.value.copy(entries = entries, isLoading = false)
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    fun formatDisplayEntries(entries: List<TE>): List<SiteEntryDisplayData> =
        entries.map { it.toDisplayData(dateUtil, translator) }

    fun formatDate(timestamp: Long): String = dateUtil.dateStringShort(timestamp)

    fun formatLocation(location: TE.Location?): String = translator.translate(location ?: TE.Location.NONE)

    fun selectLocation(location: TE.Location) {
        _uiState.value = _uiState.value.copy(selectedLocation = location)
    }

    fun setShowPumpSites(show: Boolean) {
        _uiState.value = _uiState.value.copy(showPumpSites = show)
    }

    fun setShowCgmSites(show: Boolean) {
        _uiState.value = _uiState.value.copy(showCgmSites = show)
    }

    fun setBodyType(bodyType: BodyType) {
        preferences.put(IntKey.SiteRotationUserProfile, bodyType.value)
        _uiState.value = _uiState.value.copy(showBodyType = bodyType)
    }

    fun setDefaultPumpSites(show: Boolean) {
        preferences.put(BooleanKey.SiteRotationManagePump, show)
        _uiState.value = _uiState.value.copy(showPumpSites = show)
    }

    fun setDefaultCgmSites(show: Boolean) {
        preferences.put(BooleanKey.SiteRotationManageCgm, show)
        _uiState.value = _uiState.value.copy(showCgmSites = show)
    }

    // --- Inline editing ---

    fun startEditing(timestamp: Long) {
        viewModelScope.launch {
            try {
                val editedTeList = persistenceLayer.getTherapyEventDataFromToTime(timestamp, timestamp)
                    .filter { te ->
                        te.type == TE.Type.CANNULA_CHANGE || te.type == TE.Type.SENSOR_CHANGE
                    }
                if (editedTeList.isNotEmpty()) {
                    val te = editedTeList[0]
                    loadedNote = te.note
                    loadedLocation = te.location
                    loadedArrow = te.arrow
                    _uiState.value = _uiState.value.copy(
                        editedTe = te,
                        isEdited = false,
                        selectedLocation = te.location ?: TE.Location.NONE
                    )
                }
            } catch (_: Exception) {
                // ignore
            }
        }
    }

    fun cancelEditing() {
        loadedNote = null
        loadedLocation = null
        loadedArrow = null
        _uiState.value = _uiState.value.copy(
            editedTe = null,
            isEdited = false,
            selectedLocation = TE.Location.NONE
        )
    }

    fun onZoneClick(location: TE.Location) {
        if (_uiState.value.editedTe != null) {
            updateEditLocation(location)
        } else {
            val toggle = if (location == _uiState.value.selectedLocation) TE.Location.NONE else location
            selectLocation(toggle)
        }
    }

    fun updateEditLocation(location: TE.Location) {
        _uiState.value = _uiState.value.copy(
            editedTe = _uiState.value.editedTe?.copy(location = location),
            selectedLocation = location,
            isEdited = location != loadedLocation ||
                _uiState.value.editedTe?.arrow != loadedArrow ||
                _uiState.value.editedTe?.note != loadedNote
        )
    }

    fun updateEditArrow(arrow: TE.Arrow) {
        _uiState.value = _uiState.value.copy(
            editedTe = _uiState.value.editedTe?.copy(arrow = arrow),
            isEdited = _uiState.value.editedTe?.location != loadedLocation ||
                arrow != loadedArrow ||
                _uiState.value.editedTe?.note != loadedNote
        )
    }

    fun updateEditNote(note: String) {
        val recNote = note.ifBlank { null }
        _uiState.value = _uiState.value.copy(
            editedTe = _uiState.value.editedTe?.copy(note = recNote),
            isEdited = _uiState.value.editedTe?.location != loadedLocation ||
                _uiState.value.editedTe?.arrow != loadedArrow ||
                recNote != loadedNote
        )
    }

    private var confirmedTe: TE? = null

    fun buildConfirmationSummary(): List<String> {
        val lines = mutableListOf<String>()
        val te = uiState.value.editedTe
        confirmedTe = te
        te?.let {
            if (it.location != loadedLocation)
                lines.add(rh.gs(R.string.record_site_location, translator.translate(it.location)))
            if (it.arrow != loadedArrow)
                lines.add(rh.gs(R.string.record_site_arrow, translator.translate(it.arrow)))
            if (it.note != loadedNote) {
                if (!it.note.isNullOrEmpty())
                    lines.add(rh.gs(R.string.record_site_note, it.note))
                else
                    lines.add(rh.gs(R.string.delete_site_note))
            }
        }
        return lines
    }

    fun confirmAndSave() {
        viewModelScope.launch {
            confirmedTe?.let { original ->
                val te = original.copy(ids = IDs())  // Force Upload in NS
                uel.log(
                    action = when (te.type) {
                        TE.Type.CANNULA_CHANGE -> Action.SITE_LOCATION
                        else                   -> Action.SENSOR_LOCATION
                    },
                    source = Sources.SiteRotationDialog,
                    note = te.note,
                    listOfNotNull(ValueWithUnit.Timestamp(te.timestamp), ValueWithUnit.TELocation(te.location ?: TE.Location.NONE), ValueWithUnit.TEArrow(te.arrow ?: TE.Arrow.NONE))
                )
                persistenceLayer.insertOrUpdateTherapyEvent(therapyEvent = te)
            }
            cancelEditing()
        }
    }
}
