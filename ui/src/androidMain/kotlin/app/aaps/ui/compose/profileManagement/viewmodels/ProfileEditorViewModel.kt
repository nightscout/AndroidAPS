package app.aaps.ui.compose.profileManagement.viewmodels

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.ui.UiStrings
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.data.Block
import app.aaps.core.data.model.data.TargetBlock
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileErrorType
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileRepository
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.profile.PureProfile
import app.aaps.core.interfaces.profile.SingleProfile
import app.aaps.core.interfaces.protection.ProtectionCheck
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.HardLimits
import app.aaps.core.objects.extensions.toPureProfile
import app.aaps.core.objects.profile.ProfileSealed
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.RoundingMode

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
    val usingDynamicIsf: Boolean = false,
    val usingDynamicIc: Boolean = false,
    /** Basal range comes from the active pump, so there is no constant to default to. */
    val basalRange: ClosedFloatingPointRange<Double> = 0.01..10.0,
    /** IC range is age dependent ([HardLimits.icRange]), so it has no fixed constant. */
    val icRange: ClosedFloatingPointRange<Double> = 0.5..100.0,
    val isfRange: ClosedFloatingPointRange<Double> = HardLimits.LIMIT_ISF,
    /**
     * Low and high target have different hard limits, so each picker gets its own range.
     * Defaults are the mg/dL limits; [ProfileEditorViewModel.loadState] converts them for mmol/L.
     */
    val targetLowRange: ClosedFloatingPointRange<Double> = HardLimits.LIMIT_MIN_BG,
    val targetHighRange: ClosedFloatingPointRange<Double> = HardLimits.LIMIT_MAX_BG,
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

// Registers itself: @ViewModelKey infers the key from the class. No graph entry, and deliberately
// unscoped so each screen gets its own.
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
@ViewModelKey
@Stable
class ProfileEditorViewModel @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val rh: ResourceHelper,
    private val profileRepository: ProfileRepository,
    private val profileFunction: ProfileFunction,
    private val activePlugin: ActivePlugin,
    private val profileUtil: ProfileUtil,
    private val hardLimits: HardLimits,
    val dateUtil: DateUtil,
    private val protectionCheck: ProtectionCheck
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    // VM-local edit state. [SingleProfile] is immutable, so this is simply the repo's version at
    // selection time with the user's edits applied on top via copy(); it commits through
    // [profileRepository.replace]. Nothing is shared with the repository, so no clone is needed.
    private var editingIndex: Int = 0
    private var editingProfile: SingleProfile? = null
    private var locallyEdited: Boolean = false

    // True while editing a not-yet-persisted "new profile" draft. In this mode [editingIndex] is a
    // sentinel (-1): the profile has no row in the store, so [saveProfile] appends via the repo's
    // add() and the external-change subscriber must never reload from the (nonexistent) index.
    private var isNewDraft: Boolean = false

    // The last profile this editor saved, used to recognise the echo of our own save. It is NOT a
    // one-shot flag on purpose: one save can produce two emits — the local write, and then (on a
    // paired client) the master's authoritative copy coming back through the sync channel a moment
    // later. A flag would be consumed by the first and let the second wipe edits the user typed
    // meanwhile; comparing content survives both.
    private var lastSaved: SingleProfile? = null

    init {
        viewModelScope.launch { loadState() }
        subscribeToEvents()
    }

    private fun subscribeToEvents() {
        // Watch [revision], not [profiles]. Profiles are compared structurally now, so a mutation
        // that lands on an identical list does not emit there — and "reset while the stored profile
        // happens to match what is on screen" is exactly such a case. Reset must still reload.
        // Drop the StateFlow's replayed initial value; we only care about subsequent changes.
        profileRepository.revision.drop(1)
            .onEach {
                aapsLogger.debug(LTag.PROFILE, "profileRepository changed")
                if (isNewDraft) {
                    // Uncommitted draft — its index doesn't exist in the persisted list. Never
                    // reload (that would wipe the draft); just refresh derived UI state.
                    loadState()
                    return@onEach
                }
                val stored = profileRepository.profiles.value.getOrNull(editingIndex)
                if (lastSaved != null && stored == lastSaved) {
                    // The emit carries exactly what we saved — our own write, or the master echoing
                    // it back after applying it. The working copy is correct as-is (and may already
                    // hold newer typing). Skip the reload, just refresh the UI.
                    loadState()
                } else {
                    // Somebody else changed this profile (NS push, master edit, editor reset).
                    // Reload — discards in-flight edits.
                    editingProfile = stored
                    locallyEdited = false
                    lastSaved = null
                    loadState()
                }
            }.launchIn(viewModelScope)
    }

    fun loadState() {
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
            .filter { it.type != ProfileErrorType.NAME || it.message != rh.gs(CoreUiStrings.profile_name_contains_dot) }
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
                usingDynamicIsf = aps?.usingDynamicIsf() == true,
                usingDynamicIc = aps?.supportsDynamicIc() == true,
                basalRange = pumpDescription.basalMinimumRate..pumpDescription.basalMaximumRate.coerceAtMost(10.0),
                icRange = hardLimits.icRange(),
                isfRange = HardLimits.LIMIT_ISF.inDisplayUnits(isMgdl),
                targetLowRange = HardLimits.LIMIT_MIN_BG.inDisplayUnits(isMgdl),
                targetHighRange = HardLimits.LIMIT_MAX_BG.inDisplayUnits(isMgdl),
                tabErrors = tabErrors,
                pumpIncompatible = pumpIncompatible,
                editedProfile = editedPureProfile,
                basalSum = basalSum
            )
        }
    }

    /**
     * Take a hard limit range given in mg/dL and return it in the unit the editor shows.
     *
     * For mg/dL the range is used as is. For mmol/L both ends are converted and then rounded
     * **inward** to one decimal place: the editor shows and steps mmol values with one decimal, so
     * a plain conversion can give a bound like 11.1111 that displays as "11.1" but converts back to
     * 200.17 mg/dL, above the limit. Rounding inward keeps every value the editor offers inside the
     * mg/dL limit after it is converted back in
     * [app.aaps.core.interfaces.profile.ProfileRepository.validateStructured].
     */
    private fun ClosedFloatingPointRange<Double>.inDisplayUnits(isMgdl: Boolean): ClosedFloatingPointRange<Double> {
        if (isMgdl) return this
        val low = profileUtil.fromMgdlToUnits(start, GlucoseUnit.MMOL).toBigDecimal().setScale(1, RoundingMode.UP).toDouble()
        val high = profileUtil.fromMgdlToUnits(endInclusive, GlucoseUnit.MMOL).toBigDecimal().setScale(1, RoundingMode.DOWN).toDouble()
        return low..high
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
        editingProfile = profileRepository.profiles.value.getOrNull(index)
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

    fun updateProfileName(name: String) = editProfile { it.copy(name = name) }

    fun updateIcEntry(index: Int, timeValue: TimeValue) =
        editProfile { it.copy(ic = it.ic.timeValues().updateAt(index, timeValue).toBlocks()) }

    fun updateIsfEntry(index: Int, timeValue: TimeValue) =
        editProfile { it.copy(isf = it.isf.timeValues().updateAt(index, timeValue).toBlocks()) }

    fun updateBasalEntry(index: Int, timeValue: TimeValue) =
        editProfile { it.copy(basal = it.basal.timeValues().updateAt(index, timeValue).toBlocks()) }

    fun updateTargetEntry(index: Int, low: TimeValue, high: TimeValue) =
        editProfile {
            it.copy(
                target = toTargetBlocks(
                    it.target.lowTimeValues().updateAt(index, low),
                    it.target.highTimeValues().updateAt(index, high)
                )
            )
        }

    fun addIcEntry(afterIndex: Int) = editProfile { it.copy(ic = it.ic.timeValues().addAfter(afterIndex).toBlocks()) }

    fun addIsfEntry(afterIndex: Int) = editProfile { it.copy(isf = it.isf.timeValues().addAfter(afterIndex).toBlocks()) }

    fun addBasalEntry(afterIndex: Int) = editProfile { it.copy(basal = it.basal.timeValues().addAfter(afterIndex).toBlocks()) }

    fun addTargetEntry(afterIndex: Int) =
        editProfile {
            it.copy(
                target = toTargetBlocks(
                    it.target.lowTimeValues().addAfter(afterIndex),
                    it.target.highTimeValues().addAfter(afterIndex)
                )
            )
        }

    fun removeIcEntry(index: Int) = editProfile { it.copy(ic = it.ic.timeValues().removeAt(index).toBlocks()) }

    fun removeIsfEntry(index: Int) = editProfile { it.copy(isf = it.isf.timeValues().removeAt(index).toBlocks()) }

    fun removeBasalEntry(index: Int) = editProfile { it.copy(basal = it.basal.timeValues().removeAt(index).toBlocks()) }

    fun removeTargetEntry(index: Int) =
        editProfile {
            it.copy(
                target = toTargetBlocks(
                    it.target.lowTimeValues().removeAt(index),
                    it.target.highTimeValues().removeAt(index)
                )
            )
        }

    /**
     * Apply [transform] to the working copy and refresh the UI.
     *
     * An edit that changes nothing is dropped, so re-picking the value that is already there no
     * longer lights up "unsaved changes". That check needs structural equality, which arrived with
     * [SingleProfile] becoming immutable data; the previous mutate-in-place code could not tell the
     * difference and always marked the profile edited.
     */
    private fun editProfile(transform: (SingleProfile) -> SingleProfile) {
        val current = editingProfile ?: return
        val updated = transform(current)
        if (updated == current) return
        editingProfile = updated
        markEdited()
    }

    // -----------------------------------------------------------------------------------------
    // Blocks <-> rows. A [Block] carries a DURATION, while the editor shows a START TIME per row,
    // so a row's duration depends on the row after it. These two conversions are the only place
    // that relationship is expressed.
    // -----------------------------------------------------------------------------------------

    private fun List<Block>.timeValues(): List<TimeValue> {
        var start = 0
        return map { block -> TimeValue(start, block.amount).also { start += T.msecs(block.duration).secs().toInt() } }
    }

    private fun List<TargetBlock>.lowTimeValues(): List<TimeValue> = timeValues { it.lowTarget }

    private fun List<TargetBlock>.highTimeValues(): List<TimeValue> = timeValues { it.highTarget }

    private fun List<TargetBlock>.timeValues(select: (TargetBlock) -> Double): List<TimeValue> {
        var start = 0
        return map { block -> TimeValue(start, select(block)).also { start += T.msecs(block.duration).secs().toInt() } }
    }

    private fun List<TimeValue>.toBlocks(): List<Block> =
        mapIndexed { index, row -> Block(durationMs(index, row), row.value) }

    /** Low and high always share their times, so the low side drives the durations. */
    private fun toTargetBlocks(low: List<TimeValue>, high: List<TimeValue>): List<TargetBlock> =
        low.mapIndexed { index, row ->
            TargetBlock(low.durationMs(index, row), row.value, high.getOrNull(index)?.value ?: row.value)
        }

    /** A row lasts until the next row starts; the last one runs to midnight. */
    private fun List<TimeValue>.durationMs(index: Int, row: TimeValue): Long =
        ((getOrNull(index + 1)?.timeSeconds ?: DAY_SECONDS) - row.timeSeconds) * 1000L

    // -----------------------------------------------------------------------------------------
    // Row operations. Each returns a new list and keeps the previous JSON-based limits: at most 24
    // rows, a new row starts one hour after the one it follows, and row 0 can never be removed
    // because the schedule has to start at midnight.
    // -----------------------------------------------------------------------------------------

    private fun List<TimeValue>.updateAt(index: Int, row: TimeValue): List<TimeValue> =
        if (index in indices) toMutableList().also { it[index] = row } else this

    private fun List<TimeValue>.addAfter(afterIndex: Int): List<TimeValue> {
        if (size >= 24) return this
        val previous = getOrNull(afterIndex)
        val newTime = previous?.let { it.timeSeconds + 3600 } ?: 0
        if (newTime >= DAY_SECONDS) return this
        // The new row inherits the value of the row it follows, so adding a row alone never changes
        // what the profile delivers.
        return toMutableList().also { it.add((afterIndex + 1).coerceIn(0, it.size), TimeValue(newTime, previous?.value ?: 0.0)) }
    }

    private fun List<TimeValue>.removeAt(index: Int): List<TimeValue> =
        if (size > 1 && index > 0) toMutableList().also { it.removeAt(index) } else this

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
            // Remember what we are about to persist so the resulting emit(s) are recognised as ours.
            lastSaved = profile
            if (isNewDraft) {
                // Commit the draft as a new profile. No clone needed - SingleProfile is immutable, so
                // the editor keeping its own reference cannot reach into the store.
                profileRepository.add(profile)
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
                profileRepository.replace(editingIndex, profile)
                    .onSuccess {
                        // replace() already emitted on profileRepository.profiles (via snapshot())
                        // BEFORE returning; on Main.immediate that synchronously ran the subscriber's
                        // loadState() while locallyEdited was still true, latching isEdited=true.
                        // Clear it and re-run loadState() here so isEdited reflects the saved state
                        // and the Save/Reset actions disappear on the FIRST save — mirrors the
                        // isNewDraft branch above.
                        locallyEdited = false
                        loadState()
                    }
                    .onFailure { error ->
                        // Forget the expected echo so the NEXT external event isn't mis-attributed to
                        // this failed save. Surface the error in the log; the user keeps their unsaved
                        // edits visible in the editor (locallyEdited stays true).
                        lastSaved = null
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

    private fun SingleProfile.toState(): SingleProfileState =
        SingleProfileState(
            name = name,
            mgdl = mgdl,
            ic = ic.timeValues(),
            isf = isf.timeValues(),
            basal = basal.timeValues(),
            targetLow = target.lowTimeValues(),
            targetHigh = target.highTimeValues()
        )

    private companion object {

        const val DAY_SECONDS = 24 * 3600
    }
}
