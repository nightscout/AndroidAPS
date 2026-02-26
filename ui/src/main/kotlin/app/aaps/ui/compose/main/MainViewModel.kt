package app.aaps.ui.compose.main

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.data.iob.InMemoryGlucoseValue
import app.aaps.core.data.model.RM
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.overview.graph.OverviewDataCache
import app.aaps.core.interfaces.overview.graph.TempTargetState
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.pump.Pump
import app.aaps.core.interfaces.pump.defs.determineCorrectBolusStepSize
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.ui.IconsProvider
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.constraints.ConstraintObject
import app.aaps.core.objects.wizard.QuickWizard
import app.aaps.core.objects.wizard.QuickWizardEntry
import app.aaps.ui.compose.alertDialogs.AboutDialogData
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import kotlin.math.abs

@Stable
class MainViewModel @Inject constructor(
    private val activePlugin: ActivePlugin,
    val config: Config,
    val preferences: Preferences,
    private val fabricPrivacy: FabricPrivacy,
    private val iconsProvider: IconsProvider,
    private val rh: ResourceHelper,
    private val dateUtil: DateUtil,
    private val overviewDataCache: OverviewDataCache,
    private val iobCobCalculator: IobCobCalculator,
    private val profileFunction: ProfileFunction,
    private val constraintChecker: ConstraintsChecker,
    private val quickWizard: QuickWizard,
    private val aapsLogger: AAPSLogger
) : ViewModel() {

    val uiState: StateFlow<MainUiState>
        field = MutableStateFlow(MainUiState())

    val versionName: String get() = config.VERSION_NAME
    val appIcon: Int get() = iconsProvider.getIcon()
    val calcProgressFlow: StateFlow<Int> = overviewDataCache.calcProgressFlow

    // Ticker for time-based progress updates (every 30 seconds)
    private val progressTicker = flow {
        while (true) {
            emit(dateUtil.now())
            delay(30_000L)
        }
    }

    init {
        uiState.update { it.copy(isSimpleMode = preferences.simpleMode) }
        observeTempTargetAndProfile()
    }

    /**
     * Observe TempTarget, Profile, and RunningMode from cache, combined with ticker for progress updates.
     * Progress and display text are computed from raw timestamp/duration data on each tick.
     */
    private fun observeTempTargetAndProfile() {
        combine(
            overviewDataCache.tempTargetFlow,
            overviewDataCache.profileFlow,
            overviewDataCache.runningModeFlow,
            progressTicker
        ) { ttData, profileData, rmData, now ->
            // Detect expired TT: cache still says ACTIVE but time has passed
            val ttExpired = ttData != null && ttData.state == TempTargetState.ACTIVE && ttData.duration > 0
                && now >= ttData.timestamp + ttData.duration
            if (ttExpired) {
                overviewDataCache.refreshTempTarget()
            }

            // Compute TT progress and display text from raw timing data
            val ttProgress = if (ttData != null && ttData.duration > 0 && !ttExpired) {
                val elapsed = now - ttData.timestamp
                (elapsed.toFloat() / ttData.duration.toFloat()).coerceIn(0f, 1f)
            } else 0f

            val ttText = if (ttData != null && !ttExpired) {
                if (ttData.state == TempTargetState.ACTIVE && ttData.duration > 0) {
                    "${ttData.targetRangeText} ${dateUtil.untilString(ttData.timestamp + ttData.duration, rh)}"
                } else {
                    ttData.targetRangeText
                }
            } else ""

            // Compute profile progress and display text from raw timing data
            val profileProgress = if (profileData != null && profileData.duration > 0) {
                val elapsed = now - profileData.timestamp
                (elapsed.toFloat() / profileData.duration.toFloat()).coerceIn(0f, 1f)
            } else 0f

            val profileText = if (profileData != null && profileData.profileName.isNotEmpty()) {
                if (profileData.duration > 0) {
                    "${profileData.profileName} ${dateUtil.untilString(profileData.timestamp + profileData.duration, rh)}"
                } else {
                    profileData.profileName
                }
            } else ""

            // Compute running mode progress and display text from raw timing data
            // Duration >= 30 days is effectively permanent (e.g. loop disabled uses Int.MAX_VALUE minutes)
            val rmIsFinite = rmData != null && rmData.duration > 0 && rmData.duration < T.days(30).msecs()
            val rmProgress = if (rmIsFinite) {
                val elapsed = now - rmData.timestamp
                (elapsed.toFloat() / rmData.duration.toFloat()).coerceIn(0f, 1f)
            } else 0f

            val rmText = if (rmData != null) {
                val modeName = getModeNameString(rmData.mode)
                if (rmData.mode.mustBeTemporary() && rmIsFinite) {
                    "$modeName ${dateUtil.untilString(rmData.timestamp + rmData.duration, rh)}"
                } else {
                    modeName
                }
            } else ""

            // QuickWizard state
            val qwItems = computeQuickWizardItems(rmData?.mode)

            uiState.update {
                it.copy(
                    // TempTarget state
                    tempTargetText = ttText,
                    tempTargetState = if (ttExpired) TempTargetChipState.None
                    else ttData?.state?.toChipState() ?: TempTargetChipState.None,
                    tempTargetProgress = ttProgress,
                    tempTargetReason = if (ttExpired) null else ttData?.reason,
                    // Profile state
                    isProfileLoaded = profileData?.isLoaded ?: false,
                    profileName = profileText,
                    isProfileModified = profileData?.isModified ?: false,
                    profileProgress = profileProgress,
                    // Running mode state
                    runningMode = rmData?.mode ?: RM.Mode.DISABLED_LOOP,
                    runningModeText = rmText,
                    runningModeProgress = rmProgress,
                    // QuickWizard state
                    quickWizardItems = qwItems
                )
            }
        }.launchIn(viewModelScope)
    }

    private fun computeQuickWizardItems(runningMode: RM.Mode?): List<QuickWizardItem> {
        val activeEntries = quickWizard.list().filter { it.isActive() }
        if (activeEntries.isEmpty()) return emptyList()

        val lastBG = iobCobCalculator.ads.lastBg()
        val profile = profileFunction.getProfile()
        val profileName = profileFunction.getProfileName()
        val pump = activePlugin.activePump

        // Global disable reason (applies to all entries)
        val globalReason = when {
            lastBG == null                           -> rh.gs(app.aaps.core.ui.R.string.wizard_no_actual_bg)
            profile == null                          -> rh.gs(app.aaps.core.ui.R.string.noprofile)
            !pump.isInitialized()                    -> rh.gs(app.aaps.core.ui.R.string.pump_not_initialized_profile_not_set)
            pump.isSuspended()                       -> rh.gs(app.aaps.core.ui.R.string.pumpsuspended)
            runningMode == RM.Mode.DISCONNECTED_PUMP -> rh.gs(app.aaps.core.ui.R.string.pump_disconnected)
            else                                     -> null
        }

        if (globalReason != null) {
            return activeEntries.map { entry ->
                QuickWizardItem(guid = entry.guid(), buttonText = entry.buttonText(), disabledReason = globalReason)
            }
        }

        // Per-entry calculation (profile and lastBG guaranteed non-null here)
        return activeEntries.map { entry ->
            computeSingleQuickWizardItem(entry, profile!!, profileName, lastBG!!, pump)
        }
    }

    private fun computeSingleQuickWizardItem(
        entry: QuickWizardEntry,
        profile: Profile,
        profileName: String,
        lastBG: InMemoryGlucoseValue,
        pump: Pump
    ): QuickWizardItem {
        val buttonText = entry.buttonText()
        val guid = entry.guid()

        val wizard = entry.doCalc(profile, profileName, lastBG)
        if (wizard.calculatedTotalInsulin <= 0.0)
            return QuickWizardItem(guid = guid, buttonText = buttonText, disabledReason = rh.gs(app.aaps.ui.R.string.wizard_no_insulin_required))

        val detail = rh.gs(app.aaps.core.objects.R.string.format_carbs, entry.carbs()) +
            " " + rh.gs(app.aaps.core.ui.R.string.format_insulin_units, wizard.calculatedTotalInsulin)

        val carbsAfterConstraints = constraintChecker.applyCarbsConstraints(ConstraintObject(entry.carbs(), aapsLogger)).value()
        if (carbsAfterConstraints != entry.carbs())
            return QuickWizardItem(guid = guid, buttonText = buttonText, detail = detail, disabledReason = rh.gs(app.aaps.ui.R.string.carbs_constraint_violation))
        val minStep = pump.pumpDescription.pumpType.determineCorrectBolusStepSize(wizard.insulinAfterConstraints)
        if (abs(wizard.insulinAfterConstraints - wizard.calculatedTotalInsulin) >= minStep)
            return QuickWizardItem(guid = guid, buttonText = buttonText, detail = detail, disabledReason = rh.gs(app.aaps.ui.R.string.insulin_constraint_violation))

        return QuickWizardItem(guid = guid, buttonText = buttonText, detail = detail, isEnabled = true)
    }

    /**
     * Execute QuickWizard by GUID: re-validates at execution time and calls confirmAndExecute.
     * Needs Activity context for the confirmation dialog.
     */
    fun executeQuickWizard(context: android.content.Context, guid: String) {
        val entry = quickWizard.get(guid) ?: return
        if (!entry.isActive()) return
        val bg = iobCobCalculator.ads.actualBg() ?: return
        val profile = profileFunction.getProfile() ?: return
        val profileName = profileFunction.getProfileName()
        val wizard = entry.doCalc(profile, profileName, bg)
        if (wizard.calculatedTotalInsulin > 0.0 && entry.carbs() > 0) {
            wizard.confirmAndExecute(context, entry)
        }
    }

    /**
     * Get localized name string for running mode
     */
    private fun getModeNameString(mode: RM.Mode): String = when (mode) {
        RM.Mode.CLOSED_LOOP       -> rh.gs(app.aaps.core.ui.R.string.closedloop)
        RM.Mode.CLOSED_LOOP_LGS   -> rh.gs(app.aaps.core.ui.R.string.lowglucosesuspend)
        RM.Mode.OPEN_LOOP         -> rh.gs(app.aaps.core.ui.R.string.openloop)
        RM.Mode.DISABLED_LOOP     -> rh.gs(app.aaps.core.ui.R.string.disabled_loop)
        RM.Mode.SUPER_BOLUS       -> rh.gs(app.aaps.core.ui.R.string.superbolus)
        RM.Mode.DISCONNECTED_PUMP -> rh.gs(app.aaps.core.ui.R.string.pump_disconnected)
        RM.Mode.SUSPENDED_BY_PUMP -> rh.gs(app.aaps.core.ui.R.string.pump_suspended)
        RM.Mode.SUSPENDED_BY_USER -> rh.gs(app.aaps.core.ui.R.string.loopsuspended)
        RM.Mode.SUSPENDED_BY_DST  -> rh.gs(app.aaps.core.ui.R.string.loop_suspended_by_dst)
        RM.Mode.RESUME            -> rh.gs(app.aaps.core.ui.R.string.resumeloop)
    }

    // Map cache state to UI chip state
    private fun TempTargetState.toChipState(): TempTargetChipState = when (this) {
        TempTargetState.NONE     -> TempTargetChipState.None
        TempTargetState.ACTIVE   -> TempTargetChipState.Active
        TempTargetState.ADJUSTED -> TempTargetChipState.Adjusted
    }

    // Drawer state
    fun openDrawer() {
        uiState.update { it.copy(isDrawerOpen = true) }
    }

    fun closeDrawer() {
        uiState.update { it.copy(isDrawerOpen = false) }
    }

    // About dialog state
    fun setShowAboutDialog(show: Boolean) {
        uiState.update { it.copy(showAboutDialog = show) }
    }

    fun setShowMaintenanceSheet(show: Boolean) {
        uiState.update { it.copy(showMaintenanceSheet = show) }
    }

    // Build about dialog data
    fun buildAboutDialogData(appName: String): AboutDialogData {
        var message = "Build: ${config.BUILD_VERSION}\n"
        message += "Flavor: ${config.FLAVOR}${config.BUILD_TYPE}\n"
        message += "${rh.gs(app.aaps.core.ui.R.string.configbuilder_nightscoutversion_label)} ${activePlugin.activeNsClient?.detectedNsVersion() ?: rh.gs(app.aaps.core.ui.R.string.not_available_full)}"
        if (config.isEngineeringMode()) message += "\n${rh.gs(app.aaps.core.ui.R.string.engineering_mode_enabled)}"
        if (config.isUnfinishedMode()) message += "\nUnfinished mode enabled"
        if (!fabricPrivacy.fabricEnabled()) message += "\n${rh.gs(app.aaps.core.ui.R.string.fabric_upload_disabled)}"
        message += rh.gs(app.aaps.core.ui.R.string.about_link_urls)

        return AboutDialogData(
            title = "$appName ${config.VERSION}",
            message = message,
            icon = iconsProvider.getIcon()
        )
    }
}
