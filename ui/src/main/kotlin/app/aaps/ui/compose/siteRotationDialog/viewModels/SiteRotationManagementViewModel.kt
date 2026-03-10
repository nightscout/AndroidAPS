package app.aaps.ui.compose.siteRotationDialog.viewModels

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.data.model.TE
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.Translator
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.ui.compose.siteRotationDialog.SiteEntryDisplayData
import app.aaps.ui.compose.siteRotationDialog.toDisplayData
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

    init {
        setupEventListeners()
        loadEntries()
    }

    private fun setupEventListeners() {
        persistenceLayer.observeChanges(TE::class.java)
            .onEach { loadEntries() }
            .launchIn(viewModelScope)
    }

    fun loadEntries() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
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
}