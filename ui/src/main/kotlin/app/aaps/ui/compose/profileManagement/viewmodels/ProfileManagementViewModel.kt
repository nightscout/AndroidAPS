package app.aaps.ui.compose.profileManagement.viewmodels

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.data.model.EPS
import app.aaps.core.data.model.TT
import app.aaps.core.data.time.T
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.graph.profile.ProfileCompareData
import app.aaps.core.graph.profile.buildProfileCompareData
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.insulin.Insulin
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.profile.ProfileErrorType
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileRepository
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.profile.ProfileValidationError
import app.aaps.core.interfaces.profile.SingleProfile
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.tempTargets.ttTargetMgdl
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.keys.BooleanNonKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.extensions.toPureProfile
import app.aaps.core.objects.profile.ProfileSealed
import app.aaps.core.ui.R
import app.aaps.core.ui.compose.ScreenMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * ViewModel for ProfileManagementScreen managing profile list and operations.
 *
 * Architecture:
 *  - Profile list comes from [ProfileRepository.profiles] (StateFlow, mutex-guarded mutations)
 *  - Selection (`_selectedIndex`) is VM-owned state. The editor receives its index via the
 *    navigation graph (`profile_editor/{profileIndex}`) so no global `currentProfileIndex`
 *    coupling is needed.
 *  - [uiState] is a `combine` over profiles + selection + EPS changes + screen mode
 *  - Mutations route through the repo, returning [Result]; failures surface via snackbar
 */
@HiltViewModel
@Stable
class ProfileManagementViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val profileFunction: ProfileFunction,
    val rh: ResourceHelper,
    val dateUtil: DateUtil,
    private val aapsLogger: AAPSLogger,
    private val activePlugin: ActivePlugin,
    private val insulin: Insulin,
    val profileUtil: ProfileUtil,
    val decimalFormatter: DecimalFormatter,
    private val persistenceLayer: PersistenceLayer,
    private val preferences: Preferences
) : ViewModel() {

    // VM-owned selection state. The source of truth for "which profile is currently shown
    // on the management screen". Each VM tracks its own selection — there is no longer a
    // global currentProfileIndex to keep in sync.
    private val _selectedIndex = MutableStateFlow(0)

    private val _screenMode = MutableStateFlow(ScreenMode.EDIT)

    // SharedFlow (not StateFlow) so repeated identical messages still fire — important when
    // the user retries the same failing action twice in a row.
    private val _snackbarEvent = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val snackbarEvent: SharedFlow<String> = _snackbarEvent.asSharedFlow()

    fun setScreenMode(mode: ScreenMode) {
        _screenMode.value = mode
    }

    init {
        observeActiveProfileForAutoNavigation()
    }

    /**
     * Auto-navigate to the active profile on first load and whenever it changes externally
     * (NS push, automation profile-switch, etc.). Honors user manual selection between
     * external active-profile changes.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeActiveProfileForAutoNavigation() {
        persistenceLayer.observeChanges(EPS::class.java)
            .onStart { emit(emptyList()) }
            .mapLatest {
                persistenceLayer.getEffectiveProfileSwitchActiveAt(dateUtil.now())?.originalProfileName
            }
            .distinctUntilChanged()
            .onEach { activeName ->
                val idx = profileRepository.profiles.value.indexOfFirst { it.name == activeName }
                if (idx >= 0) _selectedIndex.value = idx
            }
            .launchIn(viewModelScope)
    }

    // ---------------------------------------------------------------------------------------------
    // Reactive UI state
    // ---------------------------------------------------------------------------------------------

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<ProfileManagementUiState> = combine(
        profileRepository.profiles,
        _selectedIndex,
        persistenceLayer.observeChanges(EPS::class.java).onStart { emit(emptyList()) },
        _screenMode
    ) { profiles, requestedIdx, _, screenMode ->
        UiInputs(profiles, requestedIdx, screenMode)
    }.mapLatest { inputs ->
        runCatching { buildUiState(inputs) }.getOrElse { e ->
            aapsLogger.error(LTag.UI, "Failed to compute uiState", e)
            ProfileManagementUiState(isLoading = false, screenMode = inputs.screenMode)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfileManagementUiState())

    private data class UiInputs(
        val profiles: List<SingleProfile>,
        val requestedIdx: Int,
        val screenMode: ScreenMode
    )

    private suspend fun buildUiState(inputs: UiInputs): ProfileManagementUiState {
        val (profiles, requestedIdx, screenMode) = inputs
        val now = dateUtil.now()
        val activeEps = persistenceLayer.getEffectiveProfileSwitchActiveAt(now)
        val activeProfileName = activeEps?.originalProfileName

        val currentIndex = requestedIdx.coerceIn(0, (profiles.size - 1).coerceAtLeast(0))

        val remainingTime = activeEps?.let { eps ->
            if (eps.originalDuration > 0) {
                val endTime = eps.timestamp + eps.originalDuration
                if (endTime > now) endTime - now else 0L
            } else null
        }

        val nextProfileName = activeEps?.let { eps ->
            if (eps.originalDuration > 0) {
                val afterEnd = eps.timestamp + eps.originalDuration + 1
                persistenceLayer.getProfileSwitchActiveAt(afterEnd)?.profileName
            } else null
        }

        val profileNames = profiles.map { it.name }

        val basalSums = profiles.map { singleProfile ->
            singleProfile.toPureProfile(dateUtil)?.let { pureProfile ->
                val sealed = ProfileSealed.Pure(pureProfile, activePlugin)
                val isActive = singleProfile.name == activeProfileName
                if (isActive) {
                    sealed.pct = activeEps.originalPercentage
                    sealed.ts = (activeEps.originalTimeshift / 3600000).toInt()
                    sealed.percentageBasalSum()
                } else {
                    sealed.baseBasalSum()
                }
            } ?: 0.0
        }

        val profileErrors = computeProfileErrors(profiles)

        val (selectedProfile, compareData) = computeSelectedProfileAndCompareData(
            profiles, currentIndex, activeEps, activeProfileName
        )

        return ProfileManagementUiState(
            profileNames = profileNames,
            currentProfileIndex = currentIndex,
            activeProfileName = activeProfileName,
            activeProfileSwitch = activeEps,
            nextProfileName = nextProfileName,
            remainingTimeMs = remainingTime,
            basalSums = basalSums,
            profileErrors = profileErrors,
            selectedProfile = selectedProfile,
            compareData = compareData,
            screenMode = screenMode,
            isLoading = false
        )
    }

    /**
     * Validate each profile via the repo. Each call passes the profile directly — no
     * global-state ping-pong, no per-call mutex acquisition needed.
     */
    private suspend fun computeProfileErrors(profiles: List<SingleProfile>): List<List<ProfileValidationError>> =
        profiles.map { profile ->
            profileRepository.validateStructured(profile)
                .filter { it.type != ProfileErrorType.NAME || it.message != rh.gs(R.string.profile_name_contains_dot) }
        }

    private fun computeSelectedProfileAndCompareData(
        profiles: List<SingleProfile>,
        currentIndex: Int,
        activeEps: EPS?,
        activeProfileName: String?
    ): Pair<Profile?, ProfileCompareData?> {
        if (currentIndex !in profiles.indices) return null to null

        val isActive = profiles[currentIndex].name == activeProfileName
        if (!isActive) {
            return profiles[currentIndex].toPureProfile(dateUtil)?.let { ProfileSealed.Pure(it, activePlugin) } to null
        }
        // From here on we know activeEps != null and the selected profile is the active one
        val eps = activeEps!!
        val pct = eps.originalPercentage
        val tsMs = eps.originalTimeshift
        val hasModifications = pct != 100 || tsMs != 0L

        val effectiveProfile = ProfileSealed.EPS(eps, activePlugin)
        val baseProfile = profiles[currentIndex].toPureProfile(dateUtil)
            ?.let { ProfileSealed.Pure(it, activePlugin) }
            ?.also { it.iCfg = effectiveProfile.iCfg }

        // Detect if underlying local profile was edited since activation. Cannot use isEqual()
        // here because Pure.profileName is always "" which causes a false mismatch with
        // EPS.originalProfileName — so we ignore name in the comparison.
        val baseChanged = baseProfile?.let {
            val compareLocal = profiles[currentIndex].toPureProfile(dateUtil)?.let { pure ->
                ProfileSealed.Pure(pure, activePlugin).apply {
                    this.pct = pct
                    this.ts = T.msecs(tsMs).hours().toInt()
                }
            }
            compareLocal != null && !compareLocal.isEqual(effectiveProfile, ignoreName = true)
        } ?: false

        return when {
            baseChanged                             -> {
                val profileName = profiles[currentIndex].name
                val runningLabel = buildString {
                    append(rh.gs(R.string.running))
                    if (hasModifications) {
                        val tsHours = (tsMs / 3600000).toInt()
                        append(" (")
                        append("$pct%")
                        if (tsHours != 0) append(", ${if (tsHours > 0) "+" else ""}${tsHours}h")
                        append(")")
                    }
                }
                val compareData = buildProfileCompareData(
                    profile1 = effectiveProfile,
                    profile2 = baseProfile,
                    profileName1 = runningLabel,
                    profileName2 = profileName,
                    rh = rh,
                    dateUtil = dateUtil,
                    profileUtil = profileUtil,
                    profileFunction = profileFunction
                )
                effectiveProfile to compareData
            }

            hasModifications && baseProfile != null -> {
                val profileName = profiles[currentIndex].name
                val tsHours = (tsMs / 3600000).toInt()
                val effectiveLabel = buildString {
                    append(profileName)
                    append(" (")
                    append("$pct%")
                    if (tsHours != 0) append(", ${if (tsHours > 0) "+" else ""}${tsHours}h")
                    append(")")
                }
                val compareData = buildProfileCompareData(
                    profile1 = baseProfile,
                    profile2 = effectiveProfile,
                    profileName1 = profileName,
                    profileName2 = effectiveLabel,
                    rh = rh,
                    dateUtil = dateUtil,
                    profileUtil = profileUtil,
                    profileFunction = profileFunction
                )
                effectiveProfile to compareData
            }

            else                                    -> baseProfile to null
        }
    }

    // ---------------------------------------------------------------------------------------------
    // User actions — all mutations route through the repository
    // ---------------------------------------------------------------------------------------------

    fun selectProfile(index: Int) {
        if (index in profileRepository.profiles.value.indices) {
            _selectedIndex.value = index
        }
    }

    fun addNewProfile() {
        viewModelScope.launch {
            profileRepository.addNew().onSuccess {
                _selectedIndex.value = (profileRepository.profiles.value.size - 1).coerceAtLeast(0)
            }
        }
    }

    fun cloneProfile(index: Int) {
        viewModelScope.launch {
            profileRepository.clone(index)
                .onSuccess {
                    _selectedIndex.value = (profileRepository.profiles.value.size - 1).coerceAtLeast(0)
                }
                .onFailure {
                    _snackbarEvent.tryEmit(rh.gs(app.aaps.ui.R.string.profile_no_longer_exists))
                }
        }
    }

    fun removeProfile(index: Int) {
        viewModelScope.launch {
            val prevSelected = _selectedIndex.value
            profileRepository.remove(index)
                .onSuccess {
                    val newSize = profileRepository.profiles.value.size
                    _selectedIndex.value = when {
                        newSize == 0                                     -> 0 // shouldn't happen — repo.remove() ensures non-empty
                        prevSelected == index && prevSelected >= newSize -> newSize - 1
                        prevSelected > index                             -> (prevSelected - 1).coerceAtLeast(0)
                        else                                             -> prevSelected.coerceIn(0, newSize - 1)
                    }
                }
                .onFailure {
                    _snackbarEvent.tryEmit(rh.gs(app.aaps.ui.R.string.profile_no_longer_exists))
                }
        }
    }

    // Profile viewer formatting helpers
    fun getIcList(profile: Profile): String = profile.getIcList(rh, dateUtil)
    fun getIsfList(profile: Profile): String = profile.getIsfList(rh, dateUtil)
    fun getBasalList(profile: Profile): String = profile.getBasalList(rh, dateUtil)
    fun getTargetList(profile: Profile): String = profile.getTargetList(rh, dateUtil)
    fun formatBasalSum(basalSum: Double): String = rh.gs(R.string.format_insulin_units, basalSum)

    /**
     * Get reuse values from current active profile if it has custom percentage/timeshift
     */
    fun getReuseValues(): Pair<Int, Int>? {
        val eps = uiState.value.activeProfileSwitch ?: return null
        val percentage = eps.originalPercentage
        val timeshiftHours = T.msecs(eps.originalTimeshift).hours().toInt()
        if (percentage != 100 || timeshiftHours != 0) {
            return Pair(percentage, timeshiftHours)
        }
        return null
    }

    /**
     * Activate a profile with optional percentage, timeshift, and duration.
     *
     * @param profileIndex Index of the profile to activate
     * @param durationMinutes Duration in minutes (0 = permanent)
     * @param percentage Percentage (100 = no change)
     * @param timeshiftHours Timeshift in hours (0 = no change)
     * @param withTT Whether to create an Activity TT
     * @param notes Optional notes
     * @param timestamp Timestamp for the profile switch (defaults to now)
     * @param timeChanged Whether the user modified the time from the default
     * @return true if activation was successful
     */
    suspend fun activateProfile(
        profileIndex: Int,
        durationMinutes: Int,
        percentage: Int,
        timeshiftHours: Int,
        withTT: Boolean,
        notes: String,
        timestamp: Long = dateUtil.now(),
        timeChanged: Boolean = false
    ): Boolean {
        val profileNames = uiState.value.profileNames
        if (profileIndex !in profileNames.indices) {
            aapsLogger.error(LTag.UI, "Invalid profile index: $profileIndex")
            return false
        }

        val profileName = profileNames[profileIndex]
        val profileStore = profileRepository.profile.value ?: run {
            aapsLogger.error(LTag.UI, "No profile store available")
            return false
        }

        profileStore.getSpecificProfile(profileName) ?: run {
            aapsLogger.error(LTag.UI, "Profile not found in store: $profileName")
            _snackbarEvent.tryEmit(rh.gs(R.string.profile_not_saved_activate))
            return false
        }

        val success = profileFunction.createProfileSwitch(
            profileStore = profileStore,
            profileName = profileName,
            durationInMinutes = durationMinutes,
            percentage = percentage,
            timeShiftInHours = timeshiftHours,
            timestamp = timestamp,
            action = Action.PROFILE_SWITCH,
            source = Sources.ProfileSwitchDialog,
            note = notes.ifBlank { null },
            listValues = listOfNotNull(
                ValueWithUnit.Timestamp(timestamp).takeIf { timeChanged },
                ValueWithUnit.SimpleString(profileName),
                ValueWithUnit.Percent(percentage),
                ValueWithUnit.Hour(timeshiftHours).takeIf { timeshiftHours != 0 },
                ValueWithUnit.Minute(durationMinutes).takeIf { durationMinutes != 0 }
            ),
            iCfg = profileFunction.getProfile()?.iCfg ?: insulin.iCfg
        )

        if (success == null) {
            aapsLogger.error(LTag.UI, "Profile activation failed (validation or DB write): $profileName")
            _snackbarEvent.tryEmit(rh.gs(R.string.profile_activation_failed))
        } else {
            if (percentage == 90 && durationMinutes == 10) {
                preferences.put(BooleanNonKey.ObjectivesProfileSwitchUsed, true)
            }

            if (withTT && durationMinutes > 0 && percentage < 100) {
                val targetMgdl = preferences.ttTargetMgdl(TT.Reason.ACTIVITY)
                viewModelScope.launch {
                    persistenceLayer.insertAndCancelCurrentTemporaryTarget(
                        TT(
                            timestamp = timestamp + 10000, // Add ten secs for proper NSCv1 sync
                            duration = TimeUnit.MINUTES.toMillis(durationMinutes.toLong()),
                            reason = TT.Reason.ACTIVITY,
                            lowTarget = targetMgdl,
                            highTarget = targetMgdl
                        ),
                        action = Action.TT,
                        source = Sources.TTDialog,
                        note = null,
                        listValues = listOfNotNull(
                            ValueWithUnit.Timestamp(timestamp).takeIf { timeChanged },
                            ValueWithUnit.TETTReason(TT.Reason.ACTIVITY),
                            ValueWithUnit.Mgdl(targetMgdl),
                            ValueWithUnit.Minute(durationMinutes)
                        )
                    )
                }
            }

            // EPS change triggers uiState refresh automatically via persistenceLayer.observeChanges
        }

        return success != null
    }
}

/**
 * UI state for ProfileManagementScreen
 */
@Immutable
data class ProfileManagementUiState(
    val profileNames: List<String> = emptyList(),
    val currentProfileIndex: Int = 0,
    val activeProfileName: String? = null,
    val activeProfileSwitch: EPS? = null,
    val nextProfileName: String? = null,
    val remainingTimeMs: Long? = null,
    val basalSums: List<Double> = emptyList(),
    val profileErrors: List<List<ProfileValidationError>> = emptyList(),
    val selectedProfile: Profile? = null,
    val compareData: ProfileCompareData? = null,
    val screenMode: ScreenMode = ScreenMode.EDIT,
    val isLoading: Boolean = true
)
