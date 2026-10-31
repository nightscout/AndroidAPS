package app.aaps.ui.compose.overview.statusLights

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.data.model.TE
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.interfaces.concurrent.aapsIoDispatcher
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.nsclient.ProcessedDeviceStatusData
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventConfigBuilderChange
import app.aaps.core.interfaces.rx.events.EventInitializationChanged
import app.aaps.core.interfaces.rx.events.EventNsClientStatusUpdated
import app.aaps.core.interfaces.source.CgmSensorStatusProvider
import app.aaps.core.interfaces.source.CgmWarmupProvider
import app.aaps.core.interfaces.source.CgmWarmupStatus
import app.aaps.core.interfaces.source.PromotionRejectReason
import app.aaps.core.interfaces.source.PromotionResult
import app.aaps.core.interfaces.source.StagingState
import app.aaps.core.interfaces.stats.TddCalculator
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.interfaces.IntPreferenceKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.ui.CoreUiStrings
import app.aaps.ui.UiStrings
import app.aaps.core.ui.compose.StatusLevel
import app.aaps.core.ui.compose.icons.IcCannulaChange
import app.aaps.core.ui.compose.icons.IcCgmInsert
import app.aaps.core.ui.compose.icons.IcGenericCgm
import app.aaps.core.ui.compose.icons.IcPatchPump
import app.aaps.core.ui.compose.icons.IcPumpBattery
import app.aaps.core.ui.compose.icons.IcPumpCartridge
import app.aaps.core.ui.compose.pump.tickerFlow
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Registers itself: @ViewModelKey infers the key from the class. No graph entry, and deliberately
// unscoped so each screen gets its own.
@OptIn(ExperimentalCoroutinesApi::class)
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
@ViewModelKey
@Stable
@Inject
class StatusViewModel(
    private val rh: TextResolver,
    private val activePlugin: ActivePlugin,
    private val profileFunction: ProfileFunction,
    private val config: Config,
    private val persistenceLayer: PersistenceLayer,
    private val dateUtil: DateUtil,
    private val rxBus: RxBus,
    private val preferences: Preferences,
    private val tddCalculator: TddCalculator,
    private val decimalFormatter: DecimalFormatter,
    private val processedDeviceStatusData: ProcessedDeviceStatusData,
    private val aapsLogger: AAPSLogger
) : ViewModel() {

    private val _uiState = MutableStateFlow(StatusUiState())
    val uiState: StateFlow<StatusUiState> = _uiState.asStateFlow()

    init {
        // Refresh only while a screen shows the status lights. Every screen collects uiState with
        // collectAsStateWithLifecycle, so in the background there is no subscriber and nothing here runs.
        // The ticker emits at once, so a screen that starts collecting gets a fresh state straight away.
        val visible = _uiState.subscriptionCount
            .map { it > 0 }
            .distinctUntilChanged()
        visible
            .flatMapLatest { shown -> if (shown) refreshTriggers() else emptyFlow() }
            .onEach { refreshState() }
            .launchIn(viewModelScope)
        // A CGM warm-up and a second sensor move by the minute, so the 60 s tick is too slow for them.
        // This tick only rebuilds those two rows from the source's StateFlows, with no database read.
        // It does nothing while no source reports either of them and no such row is shown.
        visible
            .flatMapLatest { shown -> if (shown) tickerFlow(CGM_TICK_MS) else emptyFlow() }
            .onEach { if (cgmRowsMayChange()) refreshCgmRows() }
            .launchIn(viewModelScope)
    }

    private fun refreshTriggers(): Flow<Unit> = merge(
        // The ages move with time, and the cannula usage grows with every bolus and temp basal
        tickerFlow(60_000L),
        rxBus.toFlow(EventInitializationChanged::class).map { },
        rxBus.toFlow(EventConfigBuilderChange::class).map { },
        persistenceLayer.observeChanges(TE::class).map { },
        persistenceLayer.databaseClearedFlow.map { },
        // AAPSCLIENT: the reservoir comes from the NS device status
        rxBus.toFlow(EventNsClientStatusUpdated::class).map { },
        pumpLevelChanges()
    )

    /**
     * Reservoir and battery level are the only values here that come from the pump. Both are StateFlows,
     * so they emit only when the value really changes. Pump status events were used before, and they came
     * many times per connection with nothing changed (#5246). Selecting another pump gives other flows,
     * so the collection starts again on [EventConfigBuilderChange].
     */
    private fun pumpLevelChanges(): Flow<Unit> =
        rxBus.toFlow(EventConfigBuilderChange::class)
            .map { }
            .onStart { emit(Unit) }
            .flatMapLatest {
                val pump = activePlugin.activePump
                // drop(1): a StateFlow sends its current value first, which is not a change
                merge(pump.reservoirLevel.drop(1), pump.batteryLevel.drop(1)).map { }
            }

    /** The refresh that is still running, if any. */
    private var refreshJob: Job? = null

    private fun refreshState() {
        // Several triggers can come close together. Each call used to start its own cannula usage
        // calculation and none was ever cancelled, so they piled up on the database until the IOB
        // calculation and the pump status read stalled. Only the newest refresh matters, so the one
        // still running is cancelled.
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            val pump = activePlugin.activePump
            val pumpDescription = pump.pumpDescription
            val isInitialized = pump.isInitialized()
            val isPatchPump = pumpDescription.isPatchPump

            // Build status items (without expensive TDD calculation)
            val sensorStatus = buildSensorStatus()
            val warmUpStatus = buildWarmUpStatus()
            val secondSensorStatus = buildSecondSensorStatus()
            val insulinStatus = buildInsulinStatus(isPatchPump, pumpDescription.maxReservoirReading.toDouble())
            val cannulaChange = withContext(aapsIoDispatcher) {
                persistenceLayer.getLastTherapyRecordUpToNow(TE.Type.CANNULA_CHANGE)
            }
            val cannulaStatus = buildCannulaStatus(cannulaChange, isPatchPump, includeTddCalculation = false)
            val batteryStatus = if (!isPatchPump || pumpDescription.useHardwareLink) {
                buildBatteryStatus()
            } else null

            _uiState.update { state ->
                state.copy(
                    sensorStatus = sensorStatus,
                    warmUpStatus = warmUpStatus,
                    secondSensorStatus = secondSensorStatus,
                    canPromoteSecondSensor = secondSensorStatus != null && canPromoteSecondSensor(),
                    insulinStatus = insulinStatus,
                    // Preserve previous cannula level while TDD recalculates
                    cannulaStatus = state.cannulaStatus?.let { prev ->
                        cannulaStatus.copy(
                            level = prev.level,
                            levelStatus = prev.levelStatus,
                            levelPercent = prev.levelPercent
                        )
                    } ?: cannulaStatus,
                    batteryStatus = batteryStatus,
                    showFill = pumpDescription.isRefillingCapable && isInitialized,
                    showPumpBatteryChange = pumpDescription.isBatteryReplaceable || pump.isBatteryChangeLoggingEnabled(),
                    isPatchPump = isPatchPump
                )
            }

            // Calculate cannula usage (expensive operation). The items above are already shown, and the
            // usage is part of this job, so a newer refresh cancels it too.
            val cannulaStatusWithUsage = buildCannulaStatus(cannulaChange, isPatchPump, includeTddCalculation = true)
            _uiState.update { state ->
                state.copy(cannulaStatus = cannulaStatusWithUsage)
            }
        }
    }

    private suspend fun buildSensorStatus(): StatusItem {
        val event = withContext(aapsIoDispatcher) {
            persistenceLayer.getLastTherapyRecordUpToNow(TE.Type.SENSOR_CHANGE)
        }
        val bgSource = activePlugin.activeBgSource
        // Sensor battery: not shown in Overview (compact), shown in Actions (expanded) unless AAPSCLIENT
        val hasBattery = !config.AAPSCLIENT && bgSource.sensorBatteryLevel != -1
        val level = if (hasBattery) rh.gs(CoreUiStrings.units_format_percent, bgSource.sensorBatteryLevel) else null
        val levelPercent = if (hasBattery) bgSource.sensorBatteryLevel / 100f else -1f

        return StatusItem(
            label = rh.gs(CoreUiStrings.sensor_label),
            age = event?.let { formatAge(it.timestamp) } ?: "-",
            ageStatus = event?.let { getAgeStatus(it.timestamp, IntKey.OverviewSageWarning, IntKey.OverviewSageCritical) } ?: StatusLevel.UNSPECIFIED,
            agePercent = event?.let { getAgePercent(it.timestamp, IntKey.OverviewSageCritical) } ?: 0f,
            level = level,
            levelStatus = if (levelPercent >= 0) getLevelStatus((levelPercent * 100).toDouble(), IntKey.OverviewSbatWarning, IntKey.OverviewSbatCritical) else StatusLevel.UNSPECIFIED,
            levelPercent = if (levelPercent >= 0) 1f - levelPercent else -1f,
            icon = IcCgmInsert,
            compactLevel = false // Overview: sensor battery not shown
        )
    }

    /**
     * True while the active source reports a warm-up or a second sensor, or while one of the two rows
     * is still shown. The last part makes the row go away on the next tick once the source stops
     * reporting it, instead of staying until the next full refresh.
     */
    private fun cgmRowsMayChange(): Boolean {
        val source = activePlugin.activeBgSource
        val warming = (source as? CgmWarmupProvider)?.warmupStatus?.value?.active == true
        val staging = (source as? CgmSensorStatusProvider)?.stagingState?.value.let { it != null && it != StagingState.ABSENT }
        val shown = _uiState.value.let { it.warmUpStatus != null || it.secondSensorStatus != null }
        return warming || staging || shown
    }

    /** Rebuilds only the two CGM rows. They come from StateFlows of the source, so this is cheap. */
    internal fun refreshCgmRows() {
        val warmUpStatus = buildWarmUpStatus()
        val secondSensorStatus = buildSecondSensorStatus()
        val canPromote = secondSensorStatus != null && canPromoteSecondSensor()
        _uiState.update {
            it.copy(warmUpStatus = warmUpStatus, secondSensorStatus = secondSensorStatus, canPromoteSecondSensor = canPromote)
        }
    }

    /**
     * Countdown while the CGM warms up, for any source that reports it. Null for every other source
     * and as soon as the warm-up ends, so the row goes away.
     *
     * The time left comes from the wall-clock end time when the source gives one, because a stored
     * countdown is only updated when the sensor talks, and so goes stale between two radio windows.
     *
     * A source can also report a connection phase as active. Without a warm-up end time still ahead,
     * that is a radio reconnect and not a warm-up, so no row is shown for it.
     */
    internal fun buildWarmUpStatus(): StatusItem? {
        val status = (activePlugin.activeBgSource as? CgmWarmupProvider)?.warmupStatus?.value ?: return null
        if (!status.active) return null
        val now = dateUtil.now()
        val endsAt = status.endsAtEpochMs
        val isWarmUp = status.phase == CgmWarmupStatus.Phase.WARMING || (endsAt != null && endsAt > now)
        if (!isWarmUp) return null

        val remainingMs = endsAt?.let { (it - now).coerceAtLeast(0L) } ?: status.remainingMs?.coerceAtLeast(0L)
        // Rounded up, so it reads "1 min" until the time is really over and never "0 min" while warming.
        val age = remainingMs
            ?.takeIf { it > 0L }
            ?.let { rh.gs(CoreUiStrings.format_mins, ((it + MINUTE_MS - 1L) / MINUTE_MS).toInt()) }
            ?: rh.gs(UiStrings.overview_cgm_warmup_waiting)
        // Part of the warm-up that is done, so the bar fills up as the sensor gets ready.
        val total = status.totalMs
        val percent = if (total != null && total > 0L && remainingMs != null) {
            ((total - remainingMs).toFloat() / total).coerceIn(0f, 1f)
        } else -1f

        return StatusItem(
            label = rh.gs(UiStrings.overview_cgm_warmup_label),
            age = age,
            ageStatus = StatusLevel.UNSPECIFIED,
            agePercent = percent,
            icon = Icons.Filled.HourglassTop,
            compactLevel = false
        )
    }

    /**
     * A second sensor that warms up next to the one in use, for any source that supports it.
     * Shows how far it is, and how many valid readings it has sent: the readings are the only sign
     * that a sensor whose values do not go to the loop is really working.
     *
     * The source owns the rule for when the sensor may be used and publishes the time left. This
     * screen has no copy of that rule, so it cannot show a different time than the one that counts.
     */
    internal fun buildSecondSensorStatus(): StatusItem? {
        val source = activePlugin.activeBgSource as? CgmSensorStatusProvider ?: return null
        val state = source.stagingState.value
        val settleRemainingMs = source.stagingSettleRemainingMs.value?.takeIf { it > 0L }
        val age = when (state) {
            StagingState.ABSENT   -> return null
            StagingState.WARMUP   -> rh.gs(UiStrings.overview_second_sensor_warmup)
            StagingState.READY    -> rh.gs(UiStrings.overview_second_sensor_ready)
            // Whole hours, rounded up: "ready in 1 h" until the last minute, never "ready in 0 h".
            // With no time left the source still waits for enough readings.
            StagingState.SETTLING ->
                settleRemainingMs
                    ?.let { rh.gs(UiStrings.overview_second_sensor_ready_in, rh.gs(CoreUiStrings.units_format_hours, ((it + HOUR_MS - 1L) / HOUR_MS).toInt())) }
                    ?: rh.gs(UiStrings.overview_second_sensor_waiting_readings)
        }
        val ageMs = source.stagingLifecycle.value?.ageMs
        val percent = if (state == StagingState.SETTLING && settleRemainingMs != null && ageMs != null && ageMs >= 0L) {
            (ageMs.toFloat() / (ageMs + settleRemainingMs)).coerceIn(0f, 1f)
        } else -1f

        return StatusItem(
            label = rh.gs(UiStrings.overview_second_sensor_label),
            age = age,
            ageStatus = if (state == StagingState.READY) StatusLevel.NORMAL else StatusLevel.UNSPECIFIED,
            agePercent = percent,
            level = source.stagingEvidence.value?.let { rh.gs(UiStrings.overview_second_sensor_readings, it.validCount) },
            icon = IcGenericCgm,
            compactLevel = false
        )
    }

    /** The second sensor is ready, so the loop may switch to it. */
    internal fun canPromoteSecondSensor(): Boolean =
        (activePlugin.activeBgSource as? CgmSensorStatusProvider)?.stagingState?.value == StagingState.READY

    /** The switch that is still running, if any. A second tap must not start another one. */
    private var promotionJob: Job? = null

    /**
     * Switches the loop to the second sensor. This is the only action on this card that changes
     * where the loop gets its glucose from. The result is put into [StatusUiState.promotionMessage],
     * so it can be shown in a dialog.
     *
     * The UI shows the button only when the source says READY ([StatusUiState.canPromoteSecondSensor])
     * and `commandsAllowed` is true. This function does not check either again. The source checks
     * its own rules again (soak time, readings) and may refuse.
     *
     * Never an early switch: that has its own warning on the source's own screen.
     */
    fun promoteSecondSensor() {
        if (promotionJob?.isActive == true) return
        val source = activePlugin.activeBgSource as? CgmSensorStatusProvider
        if (source == null) {
            // The source was changed while the dialog was open.
            _uiState.update { it.copy(promotionMessage = promotionMessage(PromotionResult.Rejected(PromotionRejectReason.STAGING_ABSENT))) }
            refreshCgmRows()
            return
        }
        promotionJob = viewModelScope.launch {
            val message = try {
                promotionMessage(withContext(aapsIoDispatcher) { source.promoteStagingToProduction(allowEarly = false) })
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // The source did not say what happened, so the user is sent to check, not told it failed.
                aapsLogger.error(LTag.UI, "Switch to the second CGM sensor threw", e)
                rh.gs(UiStrings.overview_second_sensor_switch_error)
            }
            _uiState.update { it.copy(promotionMessage = message) }
            refreshCgmRows()
        }
    }

    /**
     * What to tell the user after a switch: done, done with a problem, or why it was refused.
     * Every result has its own text: a refusal the user cannot understand is worse than no button.
     */
    internal fun promotionMessage(result: PromotionResult): String = rh.gs(promotionMessageRef(result))

    private fun promotionMessageRef(result: PromotionResult): TextRef = when (result) {
        PromotionResult.Ok                  -> UiStrings.overview_second_sensor_switch_ok
        PromotionResult.OkBoundFailed       -> UiStrings.overview_second_sensor_switch_ok_calibration_failed
        is PromotionResult.OkFollowUpFailed ->
            if (result.productionIdentityPresent) UiStrings.overview_second_sensor_switch_ok_connect_failed
            else UiStrings.overview_second_sensor_switch_ok_sensor_unknown

        is PromotionResult.Rejected         -> when (result.reason) {
            PromotionRejectReason.STAGING_ABSENT            -> UiStrings.overview_second_sensor_switch_refused_absent
            PromotionRejectReason.STAGING_NOT_SETTLED       -> UiStrings.overview_second_sensor_switch_refused_not_settled
            PromotionRejectReason.STAGING_NO_VALID_GLUCOSE  -> UiStrings.overview_second_sensor_switch_refused_no_readings
            PromotionRejectReason.STAGING_NO_RECENT_GLUCOSE -> UiStrings.overview_second_sensor_switch_refused_no_recent_reading
        }
    }

    /** Removes the switch result once the user has read it. */
    fun clearPromotionMessage() {
        _uiState.update { it.copy(promotionMessage = null) }
    }

    private suspend fun buildInsulinStatus(isPatchPump: Boolean, maxReading: Double): StatusItem {
        val event = withContext(aapsIoDispatcher) {
            persistenceLayer.getLastTherapyRecordUpToNow(TE.Type.INSULIN_CHANGE)
        }
        // AAPSCLIENT: local activePump is VirtualPump with a stale hardcoded reservoir.
        // The followed pump's real reservoir arrives via NS device status (already in display units).
        val reservoirLevel = if (config.AAPSCLIENT) {
            processedDeviceStatusData.pumpData?.reservoir ?: 0.0
        } else {
            // Concentration comes from the running profile, which owns the authoritative iCfg. With no
            // profile there is no IU conversion to make, so the reservoir reads as unavailable — 0.0
            // takes the existing "-" / UNSPECIFIED branch below rather than showing a mis-scaled figure.
            profileFunction.getProfile()?.let { activePlugin.activePump.reservoirLevel.value.iU(it.insulinConcentration()) } ?: 0.0
        }
        val level: String? = if (reservoirLevel > 0) {
            if (!config.AAPSCLIENT && isPatchPump && reservoirLevel >= maxReading) {
                rh.gs(CoreUiStrings.value_with_unit, decimalFormatter.to0Decimal(maxReading) + "+", rh.gs(CoreUiStrings.insulin_unit_shortname))
            } else {
                rh.gs(CoreUiStrings.value_with_unit, decimalFormatter.to0Decimal(reservoirLevel), rh.gs(CoreUiStrings.insulin_unit_shortname))
            }
        } else null

        return StatusItem(
            label = rh.gs(CoreUiStrings.insulin_label),
            age = event?.let { formatAge(it.timestamp) } ?: "-",
            ageStatus = event?.let { getAgeStatus(it.timestamp, IntKey.OverviewIageWarning, IntKey.OverviewIageCritical) } ?: StatusLevel.UNSPECIFIED,
            agePercent = event?.let { getAgePercent(it.timestamp, IntKey.OverviewIageCritical) } ?: 0f,
            level = level,
            levelStatus = if (reservoirLevel > 0) getLevelStatus(reservoirLevel, IntKey.OverviewResWarning, IntKey.OverviewResCritical) else StatusLevel.UNSPECIFIED,
            levelPercent = -1f, // No progress bar - reservoir sizes vary by pump
            icon = IcPumpCartridge,
            compactAge = !isPatchPump, // Overview: insulin age hidden for patch pumps
        )
    }

    private suspend fun buildCannulaStatus(event: TE?, isPatchPump: Boolean, includeTddCalculation: Boolean): StatusItem {
        // Calculate usage since last cannula change (expensive - can be deferred)
        val usage = if (includeTddCalculation && event != null) {
            withContext(aapsIoDispatcher) {
                tddCalculator.calculateIntervalWithCachedDays(event.timestamp, dateUtil.now(), allowMissingData = false)?.totalAmount ?: 0.0
            }
        } else 0.0

        val label = if (isPatchPump) rh.gs(CoreUiStrings.patch_pump) else rh.gs(CoreUiStrings.cannula)
        val icon = if (isPatchPump) IcPatchPump else IcCannulaChange

        return StatusItem(
            label = label,
            age = event?.let { formatAge(it.timestamp) } ?: "-",
            ageStatus = event?.let { getAgeStatus(it.timestamp, IntKey.OverviewCageWarning, IntKey.OverviewCageCritical) } ?: StatusLevel.UNSPECIFIED,
            agePercent = event?.let { getAgePercent(it.timestamp, IntKey.OverviewCageCritical) } ?: 0f,
            level = if (usage > 0) rh.gs(CoreUiStrings.value_with_unit, decimalFormatter.to0Decimal(usage), rh.gs(CoreUiStrings.insulin_unit_shortname)) else null,
            levelStatus = StatusLevel.UNSPECIFIED, // Usage doesn't have warning thresholds
            levelPercent = -1f,
            icon = icon,
            compactLevel = false // Overview: cannula usage not shown
        )
    }

    private suspend fun buildBatteryStatus(): StatusItem? {
        val pump = activePlugin.activePump
        val hasAge = pump.pumpDescription.isBatteryReplaceable || pump.isBatteryChangeLoggingEnabled()

        // Eros doesn't report battery itself, but RileyLink alternatives may
        val erosBatteryLinkAvailable = pump.model() == PumpType.OMNIPOD_EROS && pump.isUseRileyLinkBatteryLevel()
        val batteryLevelValue = pump.batteryLevel.value?.toDouble()
        val hasLevel = batteryLevelValue != null && (pump.model().supportBatteryLevel || erosBatteryLinkAvailable)

        // If neither age nor level can be shown, skip entirely
        if (!hasAge && !hasLevel) return null

        val event = if (hasAge) withContext(aapsIoDispatcher) {
            persistenceLayer.getLastTherapyRecordUpToNow(TE.Type.PUMP_BATTERY_CHANGE)
        } else null

        // AAPSCLIENT: followed pump's battery is shown in the NSClient status card,
        // so suppress it here (no "n/a" placeholder cluttering the pill).
        val showLevel = !config.AAPSCLIENT && hasLevel

        // Overview compact: pbLevel.visibility based on pump model only (Eros OR not Combo/Dash)
        val useBatteryLevel = pump.model() == PumpType.OMNIPOD_EROS
            || (pump.model() != PumpType.ACCU_CHEK_COMBO && pump.model() != PumpType.OMNIPOD_DASH)

        return StatusItem(
            label = rh.gs(CoreUiStrings.pb_label),
            age = event?.let { formatAge(it.timestamp) } ?: "-",
            ageStatus = event?.let { getAgeStatus(it.timestamp, IntKey.OverviewBageWarning, IntKey.OverviewBageCritical) } ?: StatusLevel.UNSPECIFIED,
            agePercent = event?.let { getAgePercent(it.timestamp, IntKey.OverviewBageCritical) } ?: 0f,
            level = if (showLevel) rh.gs(CoreUiStrings.units_format_percent, batteryLevelValue.toInt()) else null,
            levelStatus = if (showLevel) getLevelStatus(batteryLevelValue, IntKey.OverviewBattWarning, IntKey.OverviewBattCritical) else StatusLevel.UNSPECIFIED,
            levelPercent = if (showLevel) 1f - (batteryLevelValue.toFloat() / 100f) else -1f,
            icon = IcPumpBattery,
            compactAge = hasAge, // Overview: pbAge shown only if replaceable/logging
            compactLevel = showLevel && useBatteryLevel, // hidden when no level value (e.g., AAPSCLIENT)
            expandedLevel = showLevel
        )
    }

    // Always the short form, on every screen size: a status row is read at a glance, and the long
    // words ("3 days 4 hours") pushed the row's own name out of its place on a narrow screen.
    // The days part is left out while there is none, so a fresh sensor reads "4h" and not "0d4h".
    private fun formatAge(timestamp: Long): String {
        val diff = dateUtil.computeDiff(timestamp, dateUtil.now())
        return if (diff.days == 0L) rh.gs(UiStrings.status_age_short_hours, diff.hours)
        else rh.gs(UiStrings.status_age_short, diff.days, diff.hours)
    }

    private fun getAgeStatus(timestamp: Long, warnKey: IntPreferenceKey, urgentKey: IntPreferenceKey): StatusLevel {
        val warnHours = preferences.get(warnKey)
        val urgentHours = preferences.get(urgentKey)
        val ageHours = (dateUtil.now() - timestamp) / (1000 * 60 * 60)
        return when {
            ageHours >= urgentHours -> StatusLevel.CRITICAL
            ageHours >= warnHours   -> StatusLevel.WARNING
            else                    -> StatusLevel.NORMAL
        }
    }

    private fun getAgePercent(timestamp: Long, urgentKey: IntPreferenceKey): Float {
        val urgentHours = preferences.get(urgentKey)
        if (urgentHours <= 0) return 0f
        val ageHours = (dateUtil.now() - timestamp) / (1000.0 * 60 * 60)
        return (ageHours / urgentHours).coerceIn(0.0, 1.0).toFloat()
    }

    private fun getLevelStatus(level: Double, warnKey: IntKey, criticalKey: IntKey): StatusLevel {
        val warn = preferences.get(warnKey)
        val critical = preferences.get(criticalKey)
        return when {
            level <= critical -> StatusLevel.CRITICAL
            level <= warn     -> StatusLevel.WARNING
            else              -> StatusLevel.NORMAL
        }
    }

    private companion object {

        /** How often the CGM warm-up and second sensor rows are rebuilt while one of them is shown. */
        const val CGM_TICK_MS = 10_000L
        const val MINUTE_MS = 60_000L
        const val HOUR_MS = 60L * MINUTE_MS
    }
}