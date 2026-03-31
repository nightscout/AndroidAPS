package app.aaps.ui.compose.main

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.data.iob.InMemoryGlucoseValue
import app.aaps.core.data.model.RM
import app.aaps.core.data.model.TT
import app.aaps.core.data.time.T
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.interfaces.automation.Automation
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.configuration.ExternalOptions
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.insulin.Insulin
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.overview.graph.OverviewDataCache
import app.aaps.core.interfaces.overview.graph.TempTargetState
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.LocalProfileManager
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.pump.DetailedBolusInfo
import app.aaps.core.interfaces.pump.Pump
import app.aaps.core.interfaces.queue.Callback
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.pump.defs.determineCorrectBolusStepSize
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.ui.IconsProvider
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.StringNonKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.constraints.ConstraintObject
import app.aaps.core.objects.wizard.QuickWizard
import app.aaps.core.objects.wizard.QuickWizardEntry
import app.aaps.core.objects.wizard.QuickWizardMode
import app.aaps.ui.compose.alertDialogs.AboutDialogData
import app.aaps.ui.compose.quickLaunch.QuickLaunchResolver
import app.aaps.ui.compose.quickLaunch.QuickLaunchSerializer
import app.aaps.ui.compose.quickLaunch.ResolvedQuickLaunchItem
import app.aaps.ui.compose.tempTarget.toTTPresets
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.abs

@HiltViewModel
@Stable
class MainViewModel @Inject constructor(
    private val activePlugin: ActivePlugin,
    private val insulin: Insulin,
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
    private val automation: Automation,
    private val persistenceLayer: PersistenceLayer,
    private val localProfileManager: LocalProfileManager,
    private val aapsLogger: AAPSLogger,
    private val quickLaunchResolver: QuickLaunchResolver,
    private val commandQueue: CommandQueue,
    private val uiInteraction: UiInteraction,
    private val uel: UserEntryLogger
) : ViewModel() {

    val uiState: StateFlow<MainUiState>
        field = MutableStateFlow(MainUiState())

    /** Toolbar items as a separate StateFlow to avoid unnecessary recompositions of the main UI */
    val quickLaunchItems: StateFlow<List<ResolvedQuickLaunchItem>>
        field = MutableStateFlow(emptyList())

    /** Pending confirmation dialog (automation/TT preset actions) */
    val actionConfirmation: StateFlow<ActionConfirmation?>
        field = MutableStateFlow(null)

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
        preferences.observe(BooleanKey.GeneralSimpleMode)
            .onEach { simple -> uiState.update { it.copy(isSimpleMode = simple) } }
            .launchIn(viewModelScope)
        observeTempTargetAndProfile()
        observeQuickLaunch()
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

    private suspend fun computeQuickWizardItems(runningMode: RM.Mode?): List<QuickWizardItem> {
        val activeEntries = quickWizard.list().filter { it.isActive() }
        if (activeEntries.isEmpty()) return emptyList()

        val lastBG = iobCobCalculator.ads.lastBg()
        val profile = profileFunction.getProfile()
        val profileName = profileFunction.getProfileName()
        val pump = activePlugin.activePump

        return activeEntries.map { entry ->
            when (entry.mode()) {
                QuickWizardMode.INSULIN -> computeInsulinItem(entry, pump, runningMode)
                QuickWizardMode.CARBS   -> computeCarbsItem(entry)
                QuickWizardMode.WIZARD  -> computeWizardItem(entry, lastBG, profile, profileName, pump, runningMode)
            }
        }
    }

    private fun computeInsulinItem(entry: QuickWizardEntry, pump: Pump, runningMode: RM.Mode?): QuickWizardItem {
        val buttonText = entry.buttonText()
        val guid = entry.guid()
        val detail = rh.gs(app.aaps.core.ui.R.string.format_insulin_units, entry.insulin())

        val disabledReason = when {
            !pump.isInitialized()                    -> rh.gs(app.aaps.core.ui.R.string.pump_not_initialized_profile_not_set)
            pump.isSuspended()                       -> rh.gs(app.aaps.core.ui.R.string.pumpsuspended)
            runningMode == RM.Mode.DISCONNECTED_PUMP -> rh.gs(app.aaps.core.ui.R.string.pump_disconnected)
            else                                     -> null
        }
        if (disabledReason != null)
            return QuickWizardItem(guid = guid, buttonText = buttonText, mode = entry.mode().value, detail = detail, disabledReason = disabledReason)

        val insulinAfterConstraints = constraintChecker.applyBolusConstraints(ConstraintObject(entry.insulin(), aapsLogger)).value()
        val minStep = pump.pumpDescription.pumpType.determineCorrectBolusStepSize(insulinAfterConstraints)
        if (abs(insulinAfterConstraints - entry.insulin()) >= minStep)
            return QuickWizardItem(guid = guid, buttonText = buttonText, mode = entry.mode().value, detail = detail, disabledReason = rh.gs(app.aaps.ui.R.string.insulin_constraint_violation))

        return QuickWizardItem(guid = guid, buttonText = buttonText, mode = entry.mode().value, detail = detail, isEnabled = true)
    }

    private fun computeCarbsItem(entry: QuickWizardEntry): QuickWizardItem {
        val buttonText = entry.buttonText()
        val guid = entry.guid()
        val detail = rh.gs(app.aaps.core.objects.R.string.format_carbs, entry.carbs())

        val carbsAfterConstraints = constraintChecker.applyCarbsConstraints(ConstraintObject(entry.carbs(), aapsLogger)).value()
        if (carbsAfterConstraints != entry.carbs())
            return QuickWizardItem(guid = guid, buttonText = buttonText, mode = entry.mode().value, detail = detail, disabledReason = rh.gs(app.aaps.ui.R.string.carbs_constraint_violation))

        return QuickWizardItem(guid = guid, buttonText = buttonText, mode = entry.mode().value, detail = detail, isEnabled = true)
    }

    private suspend fun computeWizardItem(
        entry: QuickWizardEntry,
        lastBG: InMemoryGlucoseValue?,
        profile: Profile?,
        profileName: String,
        pump: Pump,
        runningMode: RM.Mode?
    ): QuickWizardItem {
        val buttonText = entry.buttonText()
        val guid = entry.guid()

        val globalReason = when {
            lastBG == null                           -> rh.gs(app.aaps.core.ui.R.string.wizard_no_actual_bg)
            profile == null                          -> rh.gs(app.aaps.core.ui.R.string.noprofile)
            !pump.isInitialized()                    -> rh.gs(app.aaps.core.ui.R.string.pump_not_initialized_profile_not_set)
            pump.isSuspended()                       -> rh.gs(app.aaps.core.ui.R.string.pumpsuspended)
            runningMode == RM.Mode.DISCONNECTED_PUMP -> rh.gs(app.aaps.core.ui.R.string.pump_disconnected)
            else                                     -> null
        }
        if (globalReason != null)
            return QuickWizardItem(guid = guid, buttonText = buttonText, mode = entry.mode().value, disabledReason = globalReason)

        val wizard = entry.doCalc(profile!!, profileName, lastBG!!)
        if (wizard.calculatedTotalInsulin <= 0.0)
            return QuickWizardItem(guid = guid, buttonText = buttonText, mode = entry.mode().value, disabledReason = rh.gs(app.aaps.ui.R.string.wizard_no_insulin_required))

        val detail = rh.gs(app.aaps.core.objects.R.string.format_carbs, entry.carbs()) +
            " " + rh.gs(app.aaps.core.ui.R.string.format_insulin_units, wizard.calculatedTotalInsulin)

        val carbsAfterConstraints = constraintChecker.applyCarbsConstraints(ConstraintObject(entry.carbs(), aapsLogger)).value()
        if (carbsAfterConstraints != entry.carbs())
            return QuickWizardItem(guid = guid, buttonText = buttonText, mode = entry.mode().value, detail = detail, disabledReason = rh.gs(app.aaps.ui.R.string.carbs_constraint_violation))
        val minStep = pump.pumpDescription.pumpType.determineCorrectBolusStepSize(wizard.insulinAfterConstraints)
        if (abs(wizard.insulinAfterConstraints - wizard.calculatedTotalInsulin) >= minStep)
            return QuickWizardItem(guid = guid, buttonText = buttonText, mode = entry.mode().value, detail = detail, disabledReason = rh.gs(app.aaps.ui.R.string.insulin_constraint_violation))

        return QuickWizardItem(guid = guid, buttonText = buttonText, mode = entry.mode().value, detail = detail, isEnabled = true)
    }

    /**
     * Execute QuickWizard by GUID: re-validates at execution time and calls confirmAndExecute.
     * Needs Activity context for the confirmation dialog.
     */
    fun executeQuickWizard(context: android.content.Context, guid: String) {
        viewModelScope.launch {
            val entry = quickWizard.get(guid) ?: return@launch
            if (!entry.isActive()) return@launch
            when (entry.mode()) {
                QuickWizardMode.WIZARD  -> executeQuickWizardMode(context, entry)
                QuickWizardMode.INSULIN -> executeInsulinMode(context, entry)
                QuickWizardMode.CARBS   -> executeCarbsMode(context, entry)
            }
        }
    }

    private suspend fun executeQuickWizardMode(context: android.content.Context, entry: QuickWizardEntry) {
        val bg = iobCobCalculator.ads.actualBg() ?: return
        val profile = profileFunction.getProfile() ?: return
        val profileName = profileFunction.getProfileName()
        val wizard = entry.doCalc(profile, profileName, bg)
        if (wizard.calculatedTotalInsulin > 0.0 && entry.carbs() > 0) {
            wizard.confirmAndExecute(context, entry)
        }
    }

    private fun executeInsulinMode(context: android.content.Context, entry: QuickWizardEntry) {
        val pump = activePlugin.activePump
        if (!pump.isInitialized() || pump.isSuspended()) return

        val insulin = entry.insulin()
        val insulinAfterConstraints = constraintChecker.applyBolusConstraints(
            ConstraintObject(insulin, aapsLogger)
        ).value()
        if (insulinAfterConstraints <= 0.0) return

        val message = buildString {
            append(rh.gs(app.aaps.core.ui.R.string.bolus) + ": ")
            append(rh.gs(app.aaps.core.ui.R.string.format_insulin_units, insulinAfterConstraints))
            if (abs(insulinAfterConstraints - insulin) > pump.pumpDescription.pumpType.determineCorrectBolusStepSize(insulinAfterConstraints)) {
                append("<br/>")
                append(rh.gs(app.aaps.core.ui.R.string.bolus_constraint_applied_warn, insulin, insulinAfterConstraints))
            }
        }

        uiInteraction.showOkCancelDialog(
            context = context,
            title = entry.buttonText(),
            message = message,
            ok = {
                uel.log(
                    Action.BOLUS, Sources.QuickWizard,
                    entry.buttonText(),
                    ValueWithUnit.Insulin(insulinAfterConstraints)
                )
                val detailedBolusInfo = DetailedBolusInfo().apply {
                    eventType = app.aaps.core.data.model.TE.Type.CORRECTION_BOLUS
                    this.insulin = insulinAfterConstraints
                    this.context = context
                }
                commandQueue.bolus(detailedBolusInfo, object : Callback() {
                    override fun run() {
                        if (!result.success) {
                            uiInteraction.runAlarm(result.comment, rh.gs(app.aaps.core.ui.R.string.treatmentdeliveryerror), app.aaps.core.ui.R.raw.boluserror)
                        }
                    }
                })
                entry.markAsUsed()
            }
        )
    }

    private fun executeCarbsMode(context: android.content.Context, entry: QuickWizardEntry) {
        val carbs = entry.carbs()
        if (carbs <= 0) return

        val message = buildString {
            append(rh.gs(app.aaps.core.ui.R.string.carbs) + ": ${carbs}g")
        }

        uiInteraction.showOkCancelDialog(
            context = context,
            title = entry.buttonText(),
            message = message,
            ok = {
                uel.log(
                    Action.CARBS, Sources.QuickWizard,
                    entry.buttonText(),
                    ValueWithUnit.Gram(carbs)
                )
                val detailedBolusInfo = DetailedBolusInfo().apply {
                    eventType = app.aaps.core.data.model.TE.Type.CARBS_CORRECTION
                    this.carbs = carbs.toDouble()
                    this.context = context
                    carbsTimestamp = dateUtil.now()
                }
                commandQueue.bolus(detailedBolusInfo, object : Callback() {
                    override fun run() {
                        if (!result.success) {
                            uiInteraction.runAlarm(result.comment, rh.gs(app.aaps.core.ui.R.string.treatmentdeliveryerror), app.aaps.core.ui.R.raw.boluserror)
                        }
                    }
                })
                entry.markAsUsed()
            }
        )
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

    fun setShowAuthFailedDialog(show: Boolean) {
        uiState.update { it.copy(showAuthFailedDialog = show) }
    }

    // Build about dialog data
    fun buildAboutDialogData(appName: String): AboutDialogData {
        var message = "Build: ${config.BUILD_VERSION}\n"
        message += "Flavor: ${config.FLAVOR}${config.BUILD_TYPE}\n"
        message += "${rh.gs(app.aaps.core.ui.R.string.configbuilder_nightscoutversion_label)} ${activePlugin.activeNsClient?.detectedNsVersion() ?: rh.gs(app.aaps.core.ui.R.string.not_available_full)}"
        if (!fabricPrivacy.fabricEnabled()) message += "\n${rh.gs(app.aaps.core.ui.R.string.fabric_upload_disabled)}"
        val enabledOptions = ExternalOptions.entries.filter { config.isEnabled(it) }
        message += rh.gs(app.aaps.core.ui.R.string.about_link_urls)

        return AboutDialogData(
            title = "$appName ${config.VERSION}",
            message = message,
            icon = iconsProvider.getIcon(),
            enabledOptions = enabledOptions
        )
    }

    // ── Toolbar ──

    private fun observeQuickLaunch() {
        preferences.observe(StringNonKey.QuickLaunchActions)
            .onEach { refreshQuickLaunch(it) }
            .launchIn(viewModelScope)
    }

    /**
     * Load toolbar actions from preferences, validate dynamic entries, and resolve display info.
     * Call this on init and whenever relevant data changes (preferences, automations, profiles, etc.)
     */
    fun refreshQuickLaunch(json: String = preferences.get(StringNonKey.QuickLaunchActions)) {
        val actions = QuickLaunchSerializer.fromJson(json)

        // Validate dynamic actions and collect valid ones
        val validated = actions.filter { action -> quickLaunchResolver.isValid(action) }

        // If validation removed items, persist the cleaned list
        if (validated.size != actions.size) {
            preferences.put(StringNonKey.QuickLaunchActions, QuickLaunchSerializer.toJson(validated))
        }

        // Resolve display properties
        quickLaunchItems.update { validated.map { quickLaunchResolver.resolveItem(it) } }
    }

    fun requestAutomationConfirmation(automationId: String) {
        val event = automation.findEventById(automationId) ?: return
        val message = event.actionsDescription().joinToString("\n") { "• $it" }
        actionConfirmation.update {
            ActionConfirmation(
                title = event.title,
                message = message,
                onConfirmAction = ConfirmableAction.ExecuteAutomation(automationId)
            )
        }
    }

    fun requestTempTargetPresetConfirmation(presetId: String) {
        val presets = preferences.get(StringNonKey.TempTargetPresets).toTTPresets()
        val preset = presets.find { it.id == presetId } ?: return
        val name = preset.name ?: preset.nameRes?.let { rh.gs(it) } ?: "?"
        val durationMin = (preset.duration / 60000L).toInt()
        val message = "$name\n${rh.gs(app.aaps.core.ui.R.string.format_mins, durationMin)}"
        actionConfirmation.update {
            ActionConfirmation(
                title = rh.gs(app.aaps.core.ui.R.string.temp_target_management),
                message = message,
                onConfirmAction = ConfirmableAction.ActivateTempTargetPreset(presetId)
            )
        }
    }

    fun requestProfileConfirmation(profileName: String, percentage: Int, durationMinutes: Int) {
        val details = buildString {
            append(profileName)
            if (percentage != 100) append("\n${rh.gs(app.aaps.ui.R.string.quick_launch_profile_confirm_pct, percentage)}")
            if (durationMinutes > 0) append("\n${rh.gs(app.aaps.ui.R.string.quick_launch_profile_confirm_dur, durationMinutes)}")
            else append("\n${rh.gs(app.aaps.ui.R.string.quick_launch_profile_permanent)}")
        }
        actionConfirmation.update {
            ActionConfirmation(
                title = rh.gs(app.aaps.ui.R.string.activate_profile),
                message = details,
                onConfirmAction = ConfirmableAction.ActivateProfile(profileName, percentage, durationMinutes)
            )
        }
    }

    fun dismissActionConfirmation() {
        actionConfirmation.update { null }
    }

    fun executeConfirmableAction(action: ConfirmableAction) = viewModelScope.launch {
        actionConfirmation.update { null }
        when (action) {
            is ConfirmableAction.ExecuteAutomation        -> {
                val event = automation.findEventById(action.automationId) ?: return@launch
                viewModelScope.launch { automation.processEvent(event) }
            }

            is ConfirmableAction.ActivateTempTargetPreset -> {
                val presets = preferences.get(StringNonKey.TempTargetPresets).toTTPresets()
                val preset = presets.find { it.id == action.presetId } ?: return@launch
                viewModelScope.launch {
                    val tempTarget = TT(
                        timestamp = dateUtil.now(),
                        duration = preset.duration,
                        reason = preset.reason,
                        lowTarget = preset.targetValue,
                        highTarget = preset.targetValue
                    )
                    persistenceLayer.insertAndCancelCurrentTemporaryTarget(
                        temporaryTarget = tempTarget,
                        action = Action.TT,
                        source = Sources.TTDialog,
                        note = null,
                        listValues = listOf(
                            ValueWithUnit.Mgdl(preset.targetValue),
                            ValueWithUnit.Minute((preset.duration / 60000L).toInt())
                        )
                    )
                }
            }

            is ConfirmableAction.ActivateProfile          -> {
                val store = localProfileManager.profile ?: return@launch
                profileFunction.createProfileSwitch(
                    profileStore = store,
                    profileName = action.profileName,
                    durationInMinutes = action.durationMinutes,
                    percentage = action.percentage,
                    timeShiftInHours = 0,
                    timestamp = dateUtil.now(),
                    action = Action.PROFILE_SWITCH,
                    source = Sources.ProfileSwitchDialog,
                    note = null,
                    listValues = listOf(
                        ValueWithUnit.SimpleString(action.profileName),
                        ValueWithUnit.Percent(action.percentage),
                        ValueWithUnit.Minute(action.durationMinutes)
                    ),
                    iCfg = insulin.iCfg
                )
            }
        }
    }

}
