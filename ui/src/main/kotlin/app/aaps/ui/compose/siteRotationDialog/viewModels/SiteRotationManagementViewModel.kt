package app.aaps.ui.compose.siteRotationDialog.viewModels

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.data.model.TE
import app.aaps.core.data.time.T
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ui.ConfirmationLine
import app.aaps.core.data.ui.ConfirmationRole
import app.aaps.core.data.ui.confirmationLines
import app.aaps.core.interfaces.bolus.BatchAction
import app.aaps.core.interfaces.bolus.BatchExecutor
import app.aaps.core.interfaces.clientcontrol.ActionProgress
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.di.ApplicationScope
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.Translator
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.compose.siteRotation.BodyType
import app.aaps.core.ui.compose.siteRotation.SiteEntryDisplayData
import app.aaps.core.ui.compose.siteRotation.toDisplayData
import app.aaps.ui.R
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject
import app.aaps.core.ui.R as CoreUiR

@HiltViewModel
@Stable
class SiteRotationManagementViewModel @Inject constructor(
    private val rh: ResourceHelper,
    private val dateUtil: DateUtil,
    private val persistenceLayer: PersistenceLayer,
    private val batchExecutor: BatchExecutor,
    private val preferences: Preferences,
    private val translator: Translator,
    private val aapsLogger: AAPSLogger,
    @ApplicationScope private val appScope: CoroutineScope
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        SiteRotationUiState(
            isLoading = true,
            showBodyType = BodyType.fromPref(preferences.get(IntKey.SiteRotationUserProfile)),
            // Show all site history by default; the pump/CGM segmented buttons are manual view filters. Deliberately
            // NOT seeded from the Manage* enable-prefs — those only gate the Fill/Care site picker and default off on
            // a client, which would otherwise hide every synced record on this screen.
            showPumpSites = true,
            showCgmSites = true
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
        // The settings bottom sheet writes the body-type pref directly (generic preference renderer), so observe it
        // to keep the body diagram in sync — and to reflect a value pushed from a paired master. The pump/CGM list
        // filters are intentionally NOT bound to the Manage* enable-prefs (see initial state).
        preferences.observe(IntKey.SiteRotationUserProfile).drop(1)
            .onEach { _uiState.value = _uiState.value.copy(showBodyType = BodyType.fromPref(it)) }
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

    fun buildConfirmationSummary(): List<ConfirmationLine> {
        val te = uiState.value.editedTe
        confirmedTe = te
        return confirmationLines {
            te?.let {
                if (it.location != loadedLocation)
                    line(ConfirmationRole.NORMAL, rh.gs(R.string.record_site_location, translator.translate(it.location)))
                if (it.arrow != loadedArrow)
                    line(ConfirmationRole.NORMAL, rh.gs(R.string.record_site_arrow, translator.translate(it.arrow)))
                if (it.note != loadedNote) {
                    if (!it.note.isNullOrEmpty())
                        line(ConfirmationRole.NORMAL, rh.gs(R.string.record_site_note, it.note))
                    else
                        line(ConfirmationRole.NORMAL, rh.gs(R.string.delete_site_note))
                }
            }
        }
    }

    fun confirmAndSave() {
        confirmedTe?.let { te ->
            // Route the edit through the master via the client-control batch channel (RoleBranch): on a client a
            // signed round-trip, on a master a local update — the master is the SOLE writer. It locates its own copy
            // by timestamp+type and updates location/arrow/note in place; the change syncs back via NS treatments.
            // An OFFLINE client is rejected (not queued), consistent with bolus/scene/careportal.
            val action = BatchAction.TherapyEventEdit(
                teType = te.type,
                timestamp = te.timestamp,
                location = te.location,
                arrow = te.arrow,
                note = te.note,
                source = Sources.SiteRotationDialog
            )
            val label = rh.gs(CoreUiR.string.site_rotation)
            // appScope, not viewModelScope: the commit round-trip may outlive this edit's UI state (cleared below).
            appScope.launch {
                when (val prepared = batchExecutor.prepare(listOf(action), action.source, label)) {
                    is ActionProgress.Prepared -> {
                        val committed = batchExecutor.commit(prepared.id, action.source, label)
                        if (committed is ActionProgress.Rejected)
                            aapsLogger.warn(LTag.UI, "Site rotation edit commit rejected: ${committed.reason} ${committed.detail}")
                    }

                    is ActionProgress.Rejected -> aapsLogger.warn(LTag.UI, "Site rotation edit prepare rejected: ${prepared.reason} ${prepared.detail}")
                    else                       -> Unit
                }
            }
        }
        cancelEditing()
    }
}
