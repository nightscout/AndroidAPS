package app.aaps.ui.compose.profileManagement.viewmodels

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.interfaces.insulin.Insulin
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileErrorType
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileRepository
import app.aaps.core.interfaces.profile.PureProfile
import app.aaps.core.interfaces.profile.SingleProfile
import app.aaps.core.interfaces.protection.ProtectionCheck
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.HardLimits
import app.aaps.core.objects.extensions.toPureProfile
import app.aaps.core.objects.profile.ProfileSealed
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

data class TimeValue(
    val timeSeconds: Int,
    val value: Double
)

@Immutable
data class SingleProfileState(
    val name: String = "",
    val mgdl: Boolean = true,
    val dia: Double = 5.0,
    val ic: List<TimeValue> = listOf(TimeValue(0, 0.0)),
    val isf: List<TimeValue> = listOf(TimeValue(0, 0.0)),
    val basal: List<TimeValue> = listOf(TimeValue(0, 0.0)),
    val targetLow: List<TimeValue> = listOf(TimeValue(0, 0.0)),
    val targetHigh: List<TimeValue> = listOf(TimeValue(0, 0.0))
)

@Immutable
data class ProfileUiState(
    val profiles: List<String> = emptyList(),
    val currentProfileIndex: Int = 0,
    val currentProfile: SingleProfileState? = null,
    val isEdited: Boolean = false,
    val isValid: Boolean = true,
    val isLocked: Boolean = false,
    val selectedTab: Int = 0,
    val units: String = GlucoseUnit.MGDL.displayLabel,
    val supportsDynamicIsf: Boolean = false,
    val supportsDynamicIc: Boolean = false,
    val basalMin: Double = 0.01,
    val basalMax: Double = 10.0,
    val icMin: Double = 0.5,
    val icMax: Double = 100.0,
    val isfMin: Double = 2.0,
    val isfMax: Double = 1000.0,
    val targetMin: Double = 72.0,
    val targetMax: Double = 180.0,
    /** Map of error type to error message for tabs with validation errors */
    val tabErrors: Map<ProfileErrorType, String> = emptyMap(),
    /**
     * True when the basal schedule isn't deliverable by the currently active pump. Non-blocking:
     * shown as a warning, does not affect [isValid] / the Save gate. Pump compatibility is enforced
     * only at activation.
     */
    val pumpIncompatible: Boolean = false,
    /** Currently-edited profile as a [PureProfile] for graph rendering; null if not yet computed. */
    val editedProfile: PureProfile? = null,
    /** Sum of basal rates over 24h, derived from [editedProfile]. */
    val basalSum: Double = 0.0
)

@HiltViewModel
@Stable
class ProfileEditorViewModel @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val rh: ResourceHelper,
    private val profileRepository: ProfileRepository,
    private val profileFunction: ProfileFunction,
    private val activePlugin: ActivePlugin,
    private val insulin: Insulin,
    private val hardLimits: HardLimits,
    val dateUtil: DateUtil,
    private val protectionCheck: ProtectionCheck
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    // VM-local edit state. The profile here is a deep clone of the repo's version at
    // selection time; edits mutate this clone and commit via [profileRepository.replace].
    private var editingIndex: Int = 0
    private var editingProfile: SingleProfile? = null
    private var locallyEdited: Boolean = false

    // True while editing a not-yet-persisted "new profile" draft. In this mode [editingIndex] is a
    // sentinel (-1): the profile has no row in the store, so [saveProfile] appends via the repo's
    // add() and the external-change subscriber must never re-clone from the (nonexistent) index.
    private var isNewDraft: Boolean = false

    // Set while [saveProfile] is in flight so the [profileRepository.profiles] subscriber
    // doesn't drop the user's in-flight edits when the save triggers a StateFlow emit.
    // External changes (NS push, editor reset) still trigger a re-clone. Both writer
    // (saveProfile coroutine) and reader (StateFlow collector) run on viewModelScope
    // (Main.immediate by default), so no cross-thread access — plain Boolean is sufficient.
    private var savePending: Boolean = false

    init {
        viewModelScope.launch { loadState() }
        subscribeToEvents()
    }

    private fun subscribeToEvents() {
        // Drop the StateFlow's replayed initial value — we only care about subsequent changes.
        profileRepository.profiles.drop(1)
            .onEach {
                aapsLogger.debug(LTag.PROFILE, "profileRepository.profiles changed")
                if (isNewDraft) {
                    // Uncommitted draft — its index doesn't exist in the persisted list. Never
                    // re-clone (that would wipe the draft); just refresh derived UI state.
                    loadState()
                    return@onEach
                }
                if (savePending) {
                    // Our own save triggered the emit. The repo just persisted the snapshot we
                    // gave it (a deep clone of `editingProfile`); the live `editingProfile`
                    // reference is correct as-is. Skip the re-clone, just refresh the UI.
                    savePending = false
                    loadState()
                } else {
                    // External profile change (e.g. NS push, editor reset). Reload the clone
                    // — discards in-flight edits.
                    editingProfile = profileRepository.profiles.value.getOrNull(editingIndex)?.deepClone()
                    locallyEdited = false
                    loadState()
                }
            }.launchIn(viewModelScope)
    }

    suspend fun loadState() {
        val pumpDescription = activePlugin.activePump.pumpDescription
        val aps = activePlugin.activeAPS

        val profiles = profileRepository.profiles.value.map { it.name }
        val profile = editingProfile
        val isLocked = protectionCheck.isLocked(ProtectionCheck.Protection.PREFERENCES)

        val currentUnits = profile?.mgdl?.let { if (it) GlucoseUnit.MGDL else GlucoseUnit.MMOL } ?: profileFunction.getUnits()
        val isMgdl = currentUnits == GlucoseUnit.MGDL

        // Validate the local clone (with in-flight edits) — not the persisted snapshot.
        val validationErrors = profile?.let { profileRepository.validateStructured(it) } ?: emptyList()
        val tabErrors = validationErrors
            .filter { it.type != ProfileErrorType.NAME || it.message != rh.gs(app.aaps.core.ui.R.string.profile_name_contains_dot) }
            .associateBy({ it.type }, { it.message })

        // Pump compatibility is a non-blocking warning (does not feed [isValid]).
        val pumpIncompatible = profile?.let { profileRepository.validatePumpCompatibility(it).isNotEmpty() } ?: false

        // Build the PureProfile from the local clone too, so the graph preview reflects
        // unsaved edits. Shared extension keeps this in sync with ProfileManagementViewModel.
        val editedPureProfile = profile?.toPureProfile(dateUtil)
        val basalSum = editedPureProfile?.let { ProfileSealed.Pure(it, null).baseBasalSum() } ?: 0.0

        _uiState.update { state ->
            state.copy(
                profiles = profiles,
                currentProfileIndex = editingIndex,
                currentProfile = profile?.toState(),
                isEdited = locallyEdited,
                isValid = profile != null && tabErrors.isEmpty(),
                isLocked = isLocked,
                units = currentUnits.displayLabel,
                supportsDynamicIsf = aps?.supportsDynamicIsf() == true,
                supportsDynamicIc = aps?.supportsDynamicIc() == true,
                basalMin = pumpDescription.basalMinimumRate,
                basalMax = pumpDescription.basalMaximumRate.coerceAtMost(10.0),
                icMin = hardLimits.minIC(),
                icMax = hardLimits.maxIC(),
                isfMin = if (isMgdl) HardLimits.MIN_ISF else HardLimits.MIN_ISF / 18.0,
                isfMax = if (isMgdl) HardLimits.MAX_ISF else HardLimits.MAX_ISF / 18.0,
                targetMin = if (isMgdl) HardLimits.LIMIT_MIN_BG[0] else HardLimits.LIMIT_MIN_BG[0] / 18.0,
                targetMax = if (isMgdl) HardLimits.LIMIT_MAX_BG[1] else HardLimits.LIMIT_MAX_BG[1] / 18.0,
                tabErrors = tabErrors,
                pumpIncompatible = pumpIncompatible,
                editedProfile = editedPureProfile,
                basalSum = basalSum
            )
        }
    }

    fun selectTab(index: Int) {
        _uiState.update { it.copy(selectedTab = index) }
    }

    /**
     * Select a profile by index for editing. Called from the navigation graph after the
     * editor opens. Loads a deep clone of the profile at [index] into local edit state;
     * subsequent edits mutate the clone, and [saveProfile] commits the clone back via the repo.
     */
    fun selectProfile(index: Int) {
        isNewDraft = false
        editingIndex = index
        editingProfile = profileRepository.profiles.value.getOrNull(index)?.deepClone()
        locallyEdited = false
        viewModelScope.launch { loadState() }
    }

    /**
     * Begin editing a brand-new, unpersisted profile. The draft is empty (and therefore invalid, so
     * Save stays disabled until filled). It is added to the store only when the user saves a valid
     * profile — see [saveProfile]. Called from the navigation graph when the "new profile" route opens.
     */
    fun startNewProfileDraft() {
        isNewDraft = true
        editingIndex = -1
        editingProfile = profileRepository.newDraft()
        locallyEdited = false
        viewModelScope.launch { loadState() }
    }

    fun updateProfileName(name: String) {
        editingProfile?.name = name
        markEdited()
    }

    fun updateIcEntry(index: Int, timeValue: TimeValue) {
        editingProfile?.let { profile ->
            updateJsonArrayEntry(profile.ic, index, timeValue)
            markEdited()
        }
    }

    fun updateIsfEntry(index: Int, timeValue: TimeValue) {
        editingProfile?.let { profile ->
            updateJsonArrayEntry(profile.isf, index, timeValue)
            markEdited()
        }
    }

    fun updateBasalEntry(index: Int, timeValue: TimeValue) {
        editingProfile?.let { profile ->
            updateJsonArrayEntry(profile.basal, index, timeValue)
            markEdited()
        }
    }

    fun updateTargetEntry(index: Int, low: TimeValue, high: TimeValue) {
        editingProfile?.let { profile ->
            updateJsonArrayEntry(profile.targetLow, index, low)
            updateJsonArrayEntry(profile.targetHigh, index, high)
            markEdited()
        }
    }

    fun addIcEntry(afterIndex: Int) {
        editingProfile?.let { profile ->
            addJsonArrayEntry(profile.ic, afterIndex)
            markEdited()
        }
    }

    fun addIsfEntry(afterIndex: Int) {
        editingProfile?.let { profile ->
            addJsonArrayEntry(profile.isf, afterIndex)
            markEdited()
        }
    }

    fun addBasalEntry(afterIndex: Int) {
        editingProfile?.let { profile ->
            addJsonArrayEntry(profile.basal, afterIndex)
            markEdited()
        }
    }

    fun addTargetEntry(afterIndex: Int) {
        editingProfile?.let { profile ->
            addJsonArrayEntry(profile.targetLow, afterIndex)
            addJsonArrayEntry(profile.targetHigh, afterIndex)
            markEdited()
        }
    }

    fun removeIcEntry(index: Int) {
        editingProfile?.let { profile ->
            if (profile.ic.length() > 1 && index > 0) {
                profile.ic.remove(index)
                markEdited()
            }
        }
    }

    fun removeIsfEntry(index: Int) {
        editingProfile?.let { profile ->
            if (profile.isf.length() > 1 && index > 0) {
                profile.isf.remove(index)
                markEdited()
            }
        }
    }

    fun removeBasalEntry(index: Int) {
        editingProfile?.let { profile ->
            if (profile.basal.length() > 1 && index > 0) {
                profile.basal.remove(index)
                markEdited()
            }
        }
    }

    fun removeTargetEntry(index: Int) {
        editingProfile?.let { profile ->
            if (profile.targetLow.length() > 1 && index > 0) {
                profile.targetLow.remove(index)
                profile.targetHigh.remove(index)
                markEdited()
            }
        }
    }

    private fun updateJsonArrayEntry(array: JSONArray, index: Int, timeValue: TimeValue) {
        if (index < array.length()) {
            val obj = array.getJSONObject(index)
            val hour = timeValue.timeSeconds / 3600
            obj.put("time", String.format("%02d:00", hour))
            obj.put("timeAsSeconds", timeValue.timeSeconds)
            obj.put("value", timeValue.value)
        }
    }

    private fun addJsonArrayEntry(array: JSONArray, afterIndex: Int) {
        if (array.length() >= 24) return

        val prevObj = if (afterIndex >= 0 && afterIndex < array.length()) {
            array.getJSONObject(afterIndex)
        } else {
            null
        }

        val newTime = if (prevObj != null) {
            prevObj.getInt("timeAsSeconds") + 3600 // Add 1 hour after current entry
        } else {
            0
        }

        if (newTime >= 24 * 3600) return

        // Copy value from previous entry (the one before it)
        val inheritedValue = prevObj?.optDouble("value", 0.0) ?: 0.0

        val newObj = JSONObject().apply {
            val hour = newTime / 3600
            put("time", String.format("%02d:00", hour))
            put("timeAsSeconds", newTime)
            put("value", inheritedValue)
        }

        // Insert at position afterIndex + 1
        val insertPos = afterIndex + 1
        val tempList = mutableListOf<JSONObject>()
        for (i in 0 until array.length()) {
            tempList.add(array.getJSONObject(i))
        }
        tempList.add(insertPos.coerceIn(0, tempList.size), newObj)

        // Clear and rebuild array
        while (array.length() > 0) array.remove(0)
        tempList.forEach { array.put(it) }
    }

    private fun markEdited() {
        locallyEdited = true
        viewModelScope.launch { loadState() }
    }

    fun saveProfile() {
        viewModelScope.launch {
            val profile = editingProfile ?: return@launch
            // Never persist a semantically invalid profile. The top-bar Save button is already
            // disabled when invalid, but the unsaved-changes dialog's Save is not — guard here so
            // neither path can write invalid data (and a new draft is only committed once valid).
            if (!_uiState.value.isValid) {
                aapsLogger.debug(LTag.PROFILE, "saveProfile ignored: profile is invalid")
                return@launch
            }
            if (isNewDraft) {
                // Commit the draft as a new profile. deepClone so the stored copy is independent of
                // the editor's working reference (mirrors replace()'s defensive clone).
                profileRepository.add(profile.deepClone())
                    .onSuccess {
                        // Draft is now persisted: leave draft mode and point at its row so further
                        // saves go through replace().
                        isNewDraft = false
                        editingIndex = profileRepository.profiles.value.indexOfLast { it.name == profile.name }.coerceAtLeast(0)
                        locallyEdited = false
                        loadState()
                    }
                    .onFailure { error ->
                        aapsLogger.error(LTag.PROFILE, "saveProfile (new draft) failed", error)
                    }
            } else {
                // Flag set BEFORE the replace so the event the replace fires is recognised as ours.
                savePending = true
                profileRepository.replace(editingIndex, profile)
                    .onSuccess {
                        // replace() already emitted on profileRepository.profiles (via snapshot())
                        // BEFORE returning; on Main.immediate that synchronously ran the savePending
                        // subscriber's loadState() while locallyEdited was still true, latching
                        // isEdited=true. Clear the flag and re-run loadState() here so isEdited
                        // reflects the saved state and the Save/Reset actions disappear on the FIRST
                        // save — mirrors the isNewDraft branch above.
                        locallyEdited = false
                        loadState()
                    }
                    .onFailure { error ->
                        // Clear the flag so the NEXT external event isn't mis-attributed to this
                        // failed save. Surface the error in the log; the user keeps their unsaved
                        // edits visible in the editor (locallyEdited stays true).
                        savePending = false
                        aapsLogger.error(LTag.PROFILE, "saveProfile failed at index $editingIndex", error)
                    }
            }
        }
    }

    fun resetProfile() {
        if (isNewDraft) {
            // A draft has nothing persisted to reload — reset it back to a fresh empty profile.
            editingProfile = profileRepository.newDraft()
            locallyEdited = false
            viewModelScope.launch { loadState() }
            return
        }
        // repo.reset() reloads from preferences and emits a new list on
        // [profileRepository.profiles]; the subscriber re-clones editingProfile from the
        // freshly-loaded list and refreshes the UI. Drops any local edits, matching the
        // legacy "discard changes" semantics.
        viewModelScope.launch { profileRepository.reset() }
    }

    fun getActiveInsulin() = insulin

    private fun SingleProfile.toState(): SingleProfileState {
        return SingleProfileState(
            name = name,
            mgdl = mgdl,
            ic = ic.toTimeValueList(),
            isf = isf.toTimeValueList(),
            basal = basal.toTimeValueList(),
            targetLow = targetLow.toTimeValueList(),
            targetHigh = targetHigh.toTimeValueList()
        )
    }

    private fun JSONArray.toTimeValueList(): List<TimeValue> {
        val list = mutableListOf<TimeValue>()
        for (i in 0 until length()) {
            val obj = getJSONObject(i)
            list.add(
                TimeValue(
                    timeSeconds = obj.optInt("timeAsSeconds", 0),
                    value = obj.optDouble("value", 0.0)
                )
            )
        }
        return list
    }

    fun formatTime(seconds: Int): String {
        val hour = seconds / 3600
        return String.format("%02d:00", hour)
    }

}
