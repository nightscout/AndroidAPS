package app.aaps.ui.compose.profileManagement.viewmodels

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.data.model.EPS
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.ICfg
import app.aaps.core.data.model.TT
import app.aaps.core.data.time.T
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.db.observeChanges
import app.aaps.core.interfaces.insulin.Insulin
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.LocalProfileManager
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.profile.ProfileErrorType
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.profile.ProfileValidationError
import app.aaps.core.interfaces.profile.PureProfile
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventLocalProfileChanged
import app.aaps.core.interfaces.rx.events.EventProfileStoreChanged
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.interfaces.utils.HardLimits
import app.aaps.core.keys.BooleanNonKey
import app.aaps.core.keys.UnitDoubleKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.extensions.pureProfileFromJson
import app.aaps.core.objects.profile.ProfileSealed
import app.aaps.core.ui.R
import app.aaps.core.ui.compose.ScreenMode
import app.aaps.ui.compose.profileManagement.ProfileCompareData
import app.aaps.ui.compose.profileManagement.buildProfileCompareData
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * ViewModel for ProfileManagementScreen managing profile list and operations.
 */
@HiltViewModel
@Stable
class ProfileManagementViewModel @Inject constructor(
    private val localProfileManager: LocalProfileManager,
    private val profileFunction: ProfileFunction,
    private val rxBus: RxBus,
    val rh: ResourceHelper,
    val dateUtil: DateUtil,
    private val aapsLogger: AAPSLogger,
    private val activePlugin: ActivePlugin,
    private val insulin: Insulin,
    val profileUtil: ProfileUtil,
    val decimalFormatter: DecimalFormatter,
    private val persistenceLayer: PersistenceLayer,
    private val config: Config,
    private val hardLimits: HardLimits,
    private val notificationManager: NotificationManager,
    private val preferences: Preferences
) : ViewModel() {

    val uiState: StateFlow<ProfileManagementUiState>
        field = MutableStateFlow(ProfileManagementUiState())

    fun setScreenMode(mode: ScreenMode) {
        uiState.update { it.copy(screenMode = mode) }
    }

    init {
        loadData()
        observeProfileChanges()
    }

    /**
     * Load profiles from LocalProfileManager and active profile state
     */
    fun loadData() {
        viewModelScope.launch {
            try {
                val profiles = localProfileManager.profiles
                val now = dateUtil.now()
                val activeEps = persistenceLayer.getEffectiveProfileSwitchActiveAt(now)
                val activeProfileName = activeEps?.originalProfileName

                // Navigate to active profile on initial load or when active profile changes
                val activeIndex = profiles.indexOfFirst { it.name == activeProfileName }
                val previousActiveProfileName = uiState.value.activeProfileName
                val activeProfileChanged = previousActiveProfileName != null && previousActiveProfileName != activeProfileName
                val currentIndex = if (activeIndex >= 0 && (uiState.value.isLoading || activeProfileChanged)) {
                    // First load or active profile changed - scroll to active profile
                    localProfileManager.currentProfileIndex = activeIndex
                    activeIndex
                } else {
                    localProfileManager.currentProfileIndex
                }

                // Calculate remaining time for active profile
                val remainingTime = activeEps?.let { eps ->
                    if (eps.originalDuration > 0) {
                        val endTime = eps.timestamp + eps.originalDuration
                        if (endTime > now) endTime - now else 0L
                    } else null
                }

                // Get the profile that will be active after current one ends (use PS as EPS doesn't exist yet)
                val nextProfileName = activeEps?.let { eps ->
                    if (eps.originalDuration > 0) {
                        val afterEnd = eps.timestamp + eps.originalDuration + 1
                        persistenceLayer.getProfileSwitchActiveAt(afterEnd)?.profileName
                    } else null
                }

                // Build profile names list
                val profileNames = profiles.map { it.name }

                // Calculate basal sum for each profile
                val basalSums = profiles.mapIndexed { _, singleProfile ->
                    toPureProfile(singleProfile)?.let { pureProfile ->
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

                // Validate each profile with structured errors
                val profileErrors = profiles.indices.map { index ->
                    val savedIndex = localProfileManager.currentProfileIndex
                    localProfileManager.currentProfileIndex = index
                    val errors = localProfileManager.validateProfileStructured()
                        .filter { it.type != ProfileErrorType.NAME || it.message != rh.gs(R.string.profile_name_contains_dot) }
                    localProfileManager.currentProfileIndex = savedIndex
                    errors
                }

                // Get selected profile as Profile for viewer
                var compareData: ProfileCompareData? = null
                val selectedProfile = if (currentIndex in profiles.indices) {
                    val isActive = profiles[currentIndex].name == activeProfileName
                    if (isActive) {
                        val pct = activeEps.originalPercentage
                        val tsMs = activeEps.originalTimeshift
                        val hasModifications = pct != 100 || tsMs != 0L

                        // Effective: actual running profile from EPS
                        val effectiveProfile = ProfileSealed.EPS(activeEps, activePlugin)
                        // Base: current local profile (SingleProfile) without modifications
                        val baseProfile = toPureProfile(profiles[currentIndex])?.let { ProfileSealed.Pure(it, activePlugin) }?.also { it.iCfg = effectiveProfile.iCfg }

                        // Detect if underlying profile has changed since activation
                        // Apply same pct/ts to local profile so we compare apples-to-apples
                        // Cannot use isEqual() here because Pure.profileName is always ""
                        // which causes a false mismatch with EPS.originalProfileName
                        val baseChanged = baseProfile?.let {
                            val compareLocal = toPureProfile(profiles[currentIndex])?.let { pure ->
                                ProfileSealed.Pure(pure, activePlugin).apply {
                                    this.pct = pct
                                    this.ts = T.msecs(tsMs).hours().toInt()
                                }
                            }
                            compareLocal != null && !compareLocal.isEqual(effectiveProfile, ignoreName = true)
                        } ?: false

                        if (baseChanged) {
                            // Profile was edited after activation — show "Running" vs current
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
                            compareData = buildProfileCompareData(
                                profile1 = effectiveProfile,
                                profile2 = baseProfile,
                                profileName1 = runningLabel,
                                profileName2 = profileName,
                                rh = rh,
                                dateUtil = dateUtil,
                                profileUtil = profileUtil,
                                profileFunction = profileFunction
                            )
                            effectiveProfile
                        } else if (hasModifications) {
                            // Only pct/ts modifications — show base vs effective
                            if (baseProfile != null) {
                                val profileName = profiles[currentIndex].name
                                val tsHours = (tsMs / 3600000).toInt()
                                val effectiveLabel = buildString {
                                    append(profileName)
                                    append(" (")
                                    append("$pct%")
                                    if (tsHours != 0) append(", ${if (tsHours > 0) "+" else ""}${tsHours}h")
                                    append(")")
                                }
                                compareData = buildProfileCompareData(
                                    profile1 = baseProfile,
                                    profile2 = effectiveProfile,
                                    profileName1 = profileName,
                                    profileName2 = effectiveLabel,
                                    rh = rh,
                                    dateUtil = dateUtil,
                                    profileUtil = profileUtil,
                                    profileFunction = profileFunction
                                )
                            }
                            effectiveProfile
                        } else {
                            // Active, no modifications, base unchanged — show current local profile
                            baseProfile
                        }
                    } else {
                        // Not active — show current local profile
                        toPureProfile(profiles[currentIndex])?.let { ProfileSealed.Pure(it, activePlugin) }
                    }
                } else null

                uiState.update {
                    it.copy(
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
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                aapsLogger.error(LTag.UI, "Failed to load profiles", e)
                uiState.update {
                    it.copy(isLoading = false)
                }
            }
        }
    }

    /**
     * Convert SingleProfile to PureProfile
     */
    private fun toPureProfile(singleProfile: LocalProfileManager.SingleProfile): PureProfile? {
        val profile = JSONObject().apply {
            put("carbratio", singleProfile.ic)
            put("sens", singleProfile.isf)
            put("basal", singleProfile.basal)
            put("target_low", singleProfile.targetLow)
            put("target_high", singleProfile.targetHigh)
            put("units", if (singleProfile.mgdl) GlucoseUnit.MGDL.asText else GlucoseUnit.MMOL.asText)
            put("timezone", TimeZone.getDefault().id)
        }
        return pureProfileFromJson(profile, dateUtil)
    }

    /**
     * Subscribe to profile change events
     */
    private fun observeProfileChanges() {
        rxBus.toFlow(EventLocalProfileChanged::class.java)
            .onEach { loadData() }.launchIn(viewModelScope)
        rxBus.toFlow(EventProfileStoreChanged::class.java)
            .onEach { loadData() }.launchIn(viewModelScope)
        persistenceLayer.observeChanges<EPS>()
            .onEach { loadData() }.launchIn(viewModelScope)
    }

    /**
     * Select a profile by index
     */
    fun selectProfile(index: Int) {
        if (index in 0 until localProfileManager.numOfProfiles) {
            localProfileManager.currentProfileIndex = index
            loadData()
        }
    }

    /**
     * Add a new empty profile
     */
    fun addNewProfile() {
        localProfileManager.addNewProfile()
        localProfileManager.notifyProfileChanged()
        loadData()
    }

    /**
     * Clone the profile at the given index
     */
    fun cloneProfile(index: Int) {
        val previousIndex = localProfileManager.currentProfileIndex
        localProfileManager.currentProfileIndex = index
        localProfileManager.cloneProfile()
        localProfileManager.currentProfileIndex = previousIndex
        localProfileManager.notifyProfileChanged()
        loadData()
    }

    /**
     * Remove the profile at the given index
     */
    fun removeProfile(index: Int) {
        val previousIndex = localProfileManager.currentProfileIndex
        localProfileManager.currentProfileIndex = index
        localProfileManager.removeCurrentProfile()
        // Adjust index if needed
        if (previousIndex >= localProfileManager.numOfProfiles) {
            localProfileManager.currentProfileIndex = localProfileManager.numOfProfiles - 1
        } else if (previousIndex > index) {
            localProfileManager.currentProfileIndex = previousIndex - 1
        } else {
            localProfileManager.currentProfileIndex = previousIndex
        }
        localProfileManager.notifyProfileChanged()
        loadData()
    }

    // Profile viewer formatting helpers
    fun getIcList(profile: Profile): String = profile.getIcList(rh, dateUtil)
    fun getIsfList(profile: Profile): String = profile.getIsfList(rh, dateUtil)
    fun getBasalList(profile: Profile): String = profile.getBasalList(rh, dateUtil)
    fun getTargetList(profile: Profile): String = profile.getTargetList(rh, dateUtil)
    fun formatIcfg(iCfg: ICfg): String = iCfg.insulinLabel
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
        val profileStore = localProfileManager.profile ?: run {
            aapsLogger.error(LTag.UI, "No profile store available")
            return false
        }

        // Validate profile before activation
        val pureProfile = profileStore.getSpecificProfile(profileName) ?: run {
            aapsLogger.error(LTag.UI, "Profile not found in store: $profileName")
            return false
        }

        val profileSealed = ProfileSealed.Pure(pureProfile, activePlugin)
        val validity = profileSealed.isValid(
            rh.gs(R.string.careportal_profileswitch),
            activePlugin.activePump,
            config,
            rh,
            notificationManager,
            hardLimits,
            false
        )

        if (!validity.isValid) {
            aapsLogger.error(LTag.UI, "Profile validation failed: ${validity.reasons}")
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
            iCfg = insulin.iCfg
        )

        if (success != null) {
            // Track objectives progress
            if (percentage == 90 && durationMinutes == 10) {
                preferences.put(BooleanNonKey.ObjectivesProfileSwitchUsed, true)
            }

            if (withTT && durationMinutes > 0 && percentage < 100) {
                // Create Activity TT
                val target = preferences.get(UnitDoubleKey.OverviewActivityTarget)
                val units = profileFunction.getUnits()
                viewModelScope.launch {
                    persistenceLayer.insertAndCancelCurrentTemporaryTarget(
                        TT(
                            timestamp = timestamp + 10000, // Add ten secs for proper NSCv1 sync
                            duration = TimeUnit.MINUTES.toMillis(durationMinutes.toLong()),
                            reason = TT.Reason.ACTIVITY,
                            lowTarget = profileUtil.convertToMgdl(target, units),
                            highTarget = profileUtil.convertToMgdl(target, units)
                        ),
                        action = Action.TT,
                        source = Sources.TTDialog,
                        note = null,
                        listValues = listOfNotNull(
                            ValueWithUnit.Timestamp(timestamp).takeIf { timeChanged },
                            ValueWithUnit.TETTReason(TT.Reason.ACTIVITY),
                            ValueWithUnit.fromGlucoseUnit(target, units),
                            ValueWithUnit.Minute(durationMinutes)
                        )
                    )
                }
            }

            loadData() // Refresh UI after activation
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
