package app.aaps.ui.compose.profileManagement.viewmodels

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.data.model.EPS
import app.aaps.core.data.model.ICfg
import app.aaps.core.data.model.TT
import app.aaps.core.data.time.T
import app.aaps.core.data.ue.Sources
import app.aaps.core.graph.profile.ProfileCompareData
import app.aaps.core.graph.profile.buildProfileCompareData
import app.aaps.core.interfaces.bolus.BatchAction
import app.aaps.core.interfaces.bolus.BatchExecutor
import app.aaps.core.interfaces.clientcontrol.ActionProgress
import app.aaps.core.interfaces.clientcontrol.FailureReason
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.db.compensateForClockSkew
import app.aaps.core.interfaces.di.ApplicationScope
import app.aaps.core.interfaces.insulin.InsulinManager
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
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventShowDialog
import app.aaps.core.interfaces.sync.NsClient
import app.aaps.core.interfaces.tempTargets.ttTargetMgdl
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.keys.BooleanNonKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.extensions.toPureProfile
import app.aaps.core.objects.profile.ProfileSealed
import app.aaps.core.ui.R
import app.aaps.core.ui.clientcontrol.failText
import app.aaps.core.ui.compose.ScreenMode
import app.aaps.core.ui.compose.icons.IcProfile
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import app.aaps.core.interfaces.R as InterfacesR

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
// Registers itself: @ViewModelKey infers the key from the class. No graph entry, and deliberately
// unscoped so each screen gets its own.
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
@ViewModelKey
@Stable
class ProfileManagementViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val profileFunction: ProfileFunction,
    val rh: ResourceHelper,
    val dateUtil: DateUtil,
    private val aapsLogger: AAPSLogger,
    private val activePlugin: ActivePlugin,
    val profileUtil: ProfileUtil,
    val decimalFormatter: DecimalFormatter,
    private val persistenceLayer: PersistenceLayer,
    private val insulinManager: InsulinManager,
    private val preferences: Preferences,
    private val config: Config,
    private val nsClient: NsClient,
    private val batchExecutor: BatchExecutor,
    private val rxBus: RxBus,
    @ApplicationScope private val appScope: CoroutineScope
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

    /**
     * Whether this device can actually deliver a profile edit: it is the master, or it is a client that
     * is paired AND whose master is reachable and accepting commands. Anything else means the screen is
     * read-only rather than hidden — the profiles received from Nightscout are still worth showing.
     *
     * Both terms matter and they are different axes: pairing is the stable one (an unpaired client can
     * never send), reachability the transient one (master offline, or its remote control / websocket
     * off). The Manage sheet already gates its editors this way; this screen follows the same rule.
     *
     * Fail closed until the flows settle, and read it where the state is built rather than where the
     * mode is requested, so a change while the screen is open takes effect at once.
     */
    private val editingAllowed: Boolean get() = editingAllowedFlow.value

    private val editingAllowedFlow: StateFlow<Boolean> =
        combine(nsClient.masterOrPairedClientFlow, nsClient.masterReachable) { paired, reachable ->
            !config.AAPSCLIENT || (paired && reachable)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), !config.AAPSCLIENT)

    fun setScreenMode(mode: ScreenMode) {
        _screenMode.value = mode
    }

    init {
        observeActiveProfileForAutoNavigation()
        observeNewProfileSelection()
    }

    /**
     * When a profile is added (the editor's new-profile draft commits) or cloned, it is appended to
     * the end of the list — jump the carousel to it so the user lands on what they just created. A
     * full-list replacement (e.g. an NS push) is ignored here; that's [observeActiveProfileForAutoNavigation]'s job.
     */
    private fun observeNewProfileSelection() {
        var previousNames = profileRepository.profiles.value.map { it.name }
        profileRepository.profiles
            .onEach { profiles ->
                val names = profiles.map { it.name }
                if (names.size == previousNames.size + 1 && names.dropLast(1) == previousNames) {
                    _selectedIndex.value = names.size - 1
                }
                previousNames = names
            }
            .launchIn(viewModelScope)
    }

    /**
     * Auto-navigate to the active profile on first load and whenever it changes externally
     * (NS push, automation profile-switch, etc.). Honors user manual selection between
     * external active-profile changes.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeActiveProfileForAutoNavigation() {
        persistenceLayer.observeChanges(EPS::class)
            .compensateForClockSkew(config, dateUtil)
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
        persistenceLayer.observeChanges(EPS::class).compensateForClockSkew(config, dateUtil).onStart { emit(emptyList()) },
        _screenMode,
        // An input, not a snapshot: pairing/unpairing or the master going away while this screen is
        // open has to move it between editable and read-only right away.
        editingAllowedFlow
    ) { profiles, requestedIdx, _, screenMode, _ ->
        UiInputs(profiles, requestedIdx, screenMode)
    }.mapLatest { inputs ->
        runCatching { buildUiState(inputs) }.getOrElse { e ->
            aapsLogger.error(LTag.UI, "Failed to compute uiState", e)
            ProfileManagementUiState(
                isLoading = false,
                screenMode = if (editingAllowed) inputs.screenMode else ScreenMode.PLAY,
                commandsAllowed = editingAllowed
            )
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

        // Per-profile pump compatibility (basal deliverable by the active pump). Non-blocking —
        // surfaced as an amber "won't run on this pump" hint on the card, distinct from red errors.
        val pumpWarnings = profiles.map { profileRepository.validatePumpCompatibility(it).isNotEmpty() }

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
            pumpWarnings = pumpWarnings,
            selectedProfile = selectedProfile,
            compareData = compareData,
            // An unpaired client cannot leave PLAY, whatever mode was requested.
            screenMode = if (editingAllowed) screenMode else ScreenMode.PLAY,
            commandsAllowed = editingAllowed,
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

    // ---------------------------------------------------------------------------------------------
    // Reorder ("sort") mode
    // ---------------------------------------------------------------------------------------------

    /**
     * Working order while reorder mode is on, as `value[position] == originalIndex`; null when the
     * mode is off — which is also the flag the screen uses to decide it is sorting.
     *
     * Deliberately its own flow rather than part of [uiState]: a reorder step must not re-run
     * profile validation, and must not be read as the user selecting a different profile.
     */
    private val _reorderOrder = MutableStateFlow<List<Int>?>(null)
    val reorderOrder: StateFlow<List<Int>?> = _reorderOrder.asStateFlow()

    /**
     * Original index of the profile the carousel re-centres on when the mode is left: the last one
     * moved, or the selected profile if nothing was moved. Tracked as an *original* index so it
     * survives the permutation.
     */
    private var reorderAnchor: Int = 0

    /**
     * Profile names as they were when reorder mode was entered. The working order is a list of
     * indices, so it only means anything against the list it was derived from — a same-length
     * replacement (an NS push of a different profile set) would otherwise pass the repository's
     * permutation check and apply the user's intent to profiles they never saw.
     */
    private var reorderNames: List<String> = emptyList()

    fun enterReorderMode() {
        val profiles = profileRepository.profiles.value
        if (profiles.isEmpty()) return
        reorderAnchor = _selectedIndex.value.coerceIn(0, profiles.size - 1)
        reorderNames = profiles.map { it.name }
        _reorderOrder.value = List(profiles.size) { it }
    }

    fun cancelReorder() {
        _reorderOrder.value = null
    }

    /**
     * Apply one move as remove-then-insert, not a swap, so a non-adjacent move shifts the items in
     * between rather than scrambling them. The buttons only ever step by one, but
     * `CarouselReorderConfig.onMove` is declared for arbitrary index pairs.
     *
     * @return true if the order changed. The carousel follows the moved card only on true, so a
     *         rejected move must not look like it succeeded.
     */
    fun moveReorderItem(from: Int, to: Int): Boolean {
        val current = _reorderOrder.value ?: return false
        if (from == to || from !in current.indices || to !in current.indices) return false
        reorderAnchor = current[from]
        _reorderOrder.value = current.toMutableList().apply { add(to, removeAt(from)) }
        return true
    }

    /**
     * Persist the working order and leave reorder mode.
     *
     * Commits once, here — not per move — because each persist bumps the profile-store timestamp
     * and provokes a full Nightscout upload.
     *
     * @return the carousel position to settle on, or null if the commit failed (the profile list
     *         was replaced underneath us, e.g. by an NS push mid-sort).
     */
    suspend fun commitReorder(): Int? {
        val order = _reorderOrder.value ?: return null
        // Selection is positional, so it must follow the profile through the permutation — otherwise
        // the screen silently ends up showing a different profile. Resolved for both branches, so an
        // undone move lands on the same card a committed one would.
        val anchorPosition = order.indexOf(reorderAnchor).takeIf { it >= 0 } ?: _selectedIndex.value
        // Nothing moved — skip the repository entirely so leaving the mode is free.
        if (order.withIndex().all { (position, original) -> position == original }) {
            _selectedIndex.value = anchorPosition
            _reorderOrder.value = null
            return anchorPosition
        }
        if (profileRepository.profiles.value.map { it.name } != reorderNames) {
            // The list changed under us; the indices in `order` no longer mean what they meant.
            return failReorder()
        }
        val expectedNames = order.map { reorderNames[it] }
        return profileRepository.reorder(order).fold(
            onSuccess = {
                _selectedIndex.value = anchorPosition
                // Hold the working order until the recomputed state actually carries the new list.
                // uiState re-validates every profile and hits the DB, so clearing immediately would
                // leave the cards rendering the pre-sort order at post-sort positions for a frame —
                // the moved profile visibly snapping back before jumping again.
                withTimeoutOrNull(UI_STATE_SETTLE_TIMEOUT_MS) {
                    uiState.first { it.profileNames == expectedNames }
                }
                _reorderOrder.value = null
                anchorPosition
            },
            onFailure = { failReorder() }
        )
    }

    private fun failReorder(): Int? {
        _snackbarEvent.tryEmit(rh.gs(app.aaps.ui.R.string.profile_no_longer_exists))
        _reorderOrder.value = null
        return null
    }

    // Profile viewer formatting helpers
    fun getIcList(profile: Profile): String = profile.getIcList(rh, dateUtil)
    fun getIsfList(profile: Profile): String = profile.getIsfList(rh, dateUtil)
    fun getBasalList(profile: Profile): String = profile.getBasalList(rh, dateUtil)
    fun getTargetList(profile: Profile): String = profile.getTargetList(rh, dateUtil)
    fun formatBasalSum(basalSum: Double): String = rh.gs(InterfacesR.string.format_insulin_units, basalSum)

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
    /**
     * Whether the profile at [profileIndex] can be activated on the current pump at [percentage].
     * Percentage-aware (basal scales with percentage), so the activation dialog can react live as
     * the user changes the percentage. Returns true when there is no profile at the index.
     */
    fun isPumpCompatible(profileIndex: Int, percentage: Int): Boolean {
        val profile = profileRepository.profiles.value.getOrNull(profileIndex) ?: return true
        return profileRepository.validatePumpCompatibility(profile, percentage).isEmpty()
    }

    /**
     * What the activation screen should offer for insulin.
     *
     * [choices] is empty in the normal case — something is running or pending, so the master resolves the
     * in-force insulin itself and the screen shows no picker. It is non-empty only when nothing is in force
     * (typically the first-ever switch): the switch still has to record an insulin, and a catalogue entry is
     * never substituted silently, so the user picks one and activation stays disabled until they do.
     */
    data class InsulinChoice(val choices: List<ICfg>, val preselected: ICfg?)

    suspend fun insulinChoice(): InsulinChoice {
        if (profileFunction.getRunningOrRequestedICfg() != null) return InsulinChoice(emptyList(), null)
        val catalogue = insulinManager.insulins.toList()
        // Suggest whatever the user last switched with. Matched by label, because the switch stores a value
        // snapshot of the config rather than a reference to the catalogue entry.
        val lastLabel = persistenceLayer.getProfileSwitches().maxByOrNull { it.timestamp }?.iCfg?.insulinLabel
        return InsulinChoice(catalogue, catalogue.firstOrNull { it.insulinLabel == lastLabel })
    }

    /**
     * Activate a named profile. Routes through the role-transparent [BatchExecutor] so a client relays the switch
     * to the master (which resolves the name in its own store); on the master it applies locally. The master-authored
     * confirmation lines are shown as the single confirm dialog, and an optional activity temp-target rides the same
     * atomic batch. Returns true when the switch was prepared (the confirm dialog is shown), false on a pre-check reject.
     * [onSuccess] is invoked on the main thread ONLY after the user confirms and the switch is actually committed
     * (ActionProgress.Applied) — so a caller can close the screen on real activation, not merely when the dialog appears.
     *
     * [iCfg] is the insulin the user picked when nothing was in force (see [insulinChoice]); null lets the master
     * resolve the in-force one, or refuse when there is none.
     *
     * Note: back-dating ([timestamp]/[timeChanged]) isn't carried through the batch path — the master stamps now().
     */
    @Suppress("UNUSED_PARAMETER")
    suspend fun activateProfile(
        profileIndex: Int,
        durationMinutes: Int,
        percentage: Int,
        timeshiftHours: Int,
        withTT: Boolean,
        notes: String,
        timestamp: Long = dateUtil.now(),
        timeChanged: Boolean = false,
        iCfg: ICfg? = null,
        onSuccess: () -> Unit = {}
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

        val actions = buildList {
            add(BatchAction.ProfileSwitch(percentage, timeshiftHours, durationMinutes, profileName = profileName, notes = notes.ifBlank { null }, iCfg = iCfg))
            // An activity temp-target rides the same batch (raising → applied first, atomically with the switch).
            if (withTT && durationMinutes > 0 && percentage < 100) {
                val targetMgdl = preferences.ttTargetMgdl(TT.Reason.ACTIVITY)
                add(BatchAction.TempTarget(TT.Reason.ACTIVITY.text, targetMgdl, targetMgdl, durationMinutes, 0))
            }
        }
        val label = rh.gs(R.string.careportal_profileswitch)
        return when (val prepared = batchExecutor.prepare(actions, Sources.ProfileSwitchDialog, label)) {
            is ActionProgress.Prepared -> {
                rxBus.send(
                    EventShowDialog.OkCancel(
                        title = label, message = "", confirmationLines = prepared.lines, icon = IcProfile,
                        onOk = {
                            appScope.launch {
                                when (val result = batchExecutor.commit(prepared.id, Sources.ProfileSwitchDialog, label)) {
                                    is ActionProgress.Applied  -> {
                                        if (percentage == 90 && durationMinutes == 10) preferences.put(BooleanNonKey.ObjectivesProfileSwitchUsed, true)
                                        withContext(Dispatchers.Main) { onSuccess() }
                                    }

                                    is ActionProgress.Rejected ->
                                        if (result.reason == FailureReason.NotReachable || result.reason == FailureReason.ControlDisabled)
                                            rxBus.send(EventShowDialog.Ok(title = label, message = rh.gs(result.reason.failText())))
                                        else result.detail?.let { detail ->
                                            rxBus.send(EventShowDialog.Ok(title = label, message = detail))
                                        }

                                    else                       -> Unit // Unconfirmed → app-level modal
                                }
                            }
                        }
                    )
                )
                true
            }

            // Master-local pre-check failure, or a client offline; a client round-trip failure already showed on the app modal.
            is ActionProgress.Rejected -> {
                if (prepared.reason == FailureReason.NotReachable || prepared.reason == FailureReason.ControlDisabled)
                    rxBus.send(EventShowDialog.Ok(title = label, message = rh.gs(prepared.reason.failText())))
                else prepared.detail?.let { detail ->
                    rxBus.send(EventShowDialog.Ok(title = label, message = detail))
                }
                false
            }

            else                       -> false // Unconfirmed → handled by the app-level pending modal
        }
    }

    companion object {

        /**
         * How long [commitReorder] waits for [uiState] to carry the committed order before giving up
         * and leaving reorder mode anyway. A safety net only — the recomputation normally lands in a
         * frame or two, and this exists so the mode cannot get stuck if nothing is collecting.
         */
        private const val UI_STATE_SETTLE_TIMEOUT_MS = 1_000L
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
    /** Per-profile flag: basal not deliverable by the current pump (non-blocking warning). */
    val pumpWarnings: List<Boolean> = emptyList(),
    val selectedProfile: Profile? = null,
    val compareData: ProfileCompareData? = null,
    val screenMode: ScreenMode = ScreenMode.EDIT,
    /**
     * False on an unpaired client: editing and activating are both commands for the master, and there
     * is no master to send them to. The profile list itself stays visible — only the affordances go
     * away. Same axis as `commandsAllowed` on the overview chips.
     */
    val commandsAllowed: Boolean = true,
    val isLoading: Boolean = true
)
