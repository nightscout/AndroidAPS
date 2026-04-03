package app.aaps.ui.compose.overview.statusLights

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.data.model.TE
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.insulin.Insulin
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventInitializationChanged
import app.aaps.core.interfaces.rx.events.EventPumpStatusChanged
import app.aaps.core.interfaces.stats.TddCalculator
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.interfaces.IntPreferenceKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.R
import app.aaps.core.ui.compose.StatusLevel
import app.aaps.core.ui.compose.icons.IcCannulaChange
import app.aaps.core.ui.compose.icons.IcCgmInsert
import app.aaps.core.ui.compose.icons.IcPatchPump
import app.aaps.core.ui.compose.icons.IcPumpBattery
import app.aaps.core.ui.compose.icons.IcPumpCartridge
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltViewModel
@Stable
class StatusViewModel @Inject constructor(
    private val rh: ResourceHelper,
    private val activePlugin: ActivePlugin,
    private val insulin: Insulin,
    private val config: Config,
    private val persistenceLayer: PersistenceLayer,
    private val dateUtil: DateUtil,
    private val rxBus: RxBus,
    private val preferences: Preferences,
    private val tddCalculator: TddCalculator,
    private val decimalFormatter: DecimalFormatter
) : ViewModel() {

    val uiState: StateFlow<StatusUiState>
        field = MutableStateFlow(StatusUiState())

    init {
        setupEventListeners()
        refreshState()
    }

    private fun setupEventListeners() {
        rxBus.toFlow(EventInitializationChanged::class.java)
            .onEach { refreshState() }.launchIn(viewModelScope)
        persistenceLayer.observeChanges(TE::class.java)
            .onEach { refreshState() }.launchIn(viewModelScope)
        rxBus.toFlow(EventPumpStatusChanged::class.java)
            .onEach { refreshState() }.launchIn(viewModelScope)
    }

    fun refreshState() {
        viewModelScope.launch {
            val pump = activePlugin.activePump
            val pumpDescription = pump.pumpDescription
            val isInitialized = pump.isInitialized()
            val isPatchPump = pumpDescription.isPatchPump

            // Build status items (without expensive TDD calculation)
            val sensorStatus = buildSensorStatus()
            val insulinStatus = buildInsulinStatus(isPatchPump, pumpDescription.maxReservoirReading.toDouble())
            val cannulaStatus = buildCannulaStatus(isPatchPump, includeTddCalculation = false)
            val batteryStatus = if (!isPatchPump || pumpDescription.useHardwareLink) {
                buildBatteryStatus()
            } else null

            uiState.update { state ->
                state.copy(
                    sensorStatus = sensorStatus,
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

            // Calculate cannula usage in background (expensive operation)
            viewModelScope.launch {
                val cannulaStatusWithUsage = buildCannulaStatus(isPatchPump, includeTddCalculation = true)
                uiState.update { state ->
                    state.copy(cannulaStatus = cannulaStatusWithUsage)
                }
            }
        }
    }

    private suspend fun buildSensorStatus(): StatusItem {
        val event = withContext(Dispatchers.IO) {
            persistenceLayer.getLastTherapyRecordUpToNow(TE.Type.SENSOR_CHANGE)
        }
        val bgSource = activePlugin.activeBgSource
        // Sensor battery: not shown in Overview (compact), shown in Actions (expanded) unless AAPSCLIENT
        val hasBattery = !config.AAPSCLIENT && bgSource.sensorBatteryLevel != -1
        val level = if (hasBattery) "${bgSource.sensorBatteryLevel}%" else null
        val levelPercent = if (hasBattery) bgSource.sensorBatteryLevel / 100f else -1f

        return StatusItem(
            label = rh.gs(R.string.sensor_label),
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

    private suspend fun buildInsulinStatus(isPatchPump: Boolean, maxReading: Double): StatusItem {
        val event = withContext(Dispatchers.IO) {
            persistenceLayer.getLastTherapyRecordUpToNow(TE.Type.INSULIN_CHANGE)
        }
        val pump = activePlugin.activePump
        val reservoirLevel = pump.reservoirLevel.value.iU(insulin.iCfg.concentration)
        val insulinUnit = rh.gs(R.string.insulin_unit_shortname)

        val level: String? = if (reservoirLevel > 0) {
            if (isPatchPump && reservoirLevel >= maxReading) {
                "${decimalFormatter.to0Decimal(maxReading)}+ $insulinUnit"
            } else {
                decimalFormatter.to0Decimal(reservoirLevel, insulinUnit)
            }
        } else null

        return StatusItem(
            label = rh.gs(R.string.insulin_label),
            age = event?.let { formatAge(it.timestamp) } ?: "-",
            ageStatus = event?.let { getAgeStatus(it.timestamp, IntKey.OverviewIageWarning, IntKey.OverviewIageCritical) } ?: StatusLevel.UNSPECIFIED,
            agePercent = event?.let { getAgePercent(it.timestamp, IntKey.OverviewIageCritical) } ?: 0f,
            level = level,
            levelStatus = if (reservoirLevel > 0) getLevelStatus(reservoirLevel, IntKey.OverviewResWarning, IntKey.OverviewResCritical) else StatusLevel.UNSPECIFIED,
            levelPercent = -1f, // No progress bar - reservoir sizes vary by pump
            icon = IcPumpCartridge,
            compactAge = !isPatchPump, // Overview: insulin age hidden for patch pumps
            expandedLevel = !config.AAPSCLIENT // Actions: AAPSCLIENT suppresses reservoir level
        )
    }

    private suspend fun buildCannulaStatus(isPatchPump: Boolean, includeTddCalculation: Boolean = true): StatusItem {
        val event = withContext(Dispatchers.IO) {
            persistenceLayer.getLastTherapyRecordUpToNow(TE.Type.CANNULA_CHANGE)
        }
        val insulinUnit = rh.gs(R.string.insulin_unit_shortname)

        // Calculate usage since last cannula change (expensive - can be deferred)
        val usage = if (includeTddCalculation && event != null) {
            withContext(Dispatchers.IO) {
                tddCalculator.calculateInterval(event.timestamp, dateUtil.now(), allowMissingData = false)?.totalAmount ?: 0.0
            }
        } else 0.0

        val label = if (isPatchPump) rh.gs(R.string.patch_pump) else rh.gs(R.string.cannula)
        val icon = if (isPatchPump) IcPatchPump else IcCannulaChange

        return StatusItem(
            label = label,
            age = event?.let { formatAge(it.timestamp) } ?: "-",
            ageStatus = event?.let { getAgeStatus(it.timestamp, IntKey.OverviewCageWarning, IntKey.OverviewCageCritical) } ?: StatusLevel.UNSPECIFIED,
            agePercent = event?.let { getAgePercent(it.timestamp, IntKey.OverviewCageCritical) } ?: 0f,
            level = if (usage > 0) decimalFormatter.to0Decimal(usage, insulinUnit) else null,
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

        val event = if (hasAge) withContext(Dispatchers.IO) {
            persistenceLayer.getLastTherapyRecordUpToNow(TE.Type.PUMP_BATTERY_CHANGE)
        } else null

        // AAPSCLIENT: handler never calls handleLevel, so level value is suppressed
        val showLevel = !config.AAPSCLIENT && hasLevel
        val level = if (showLevel) {
            "${batteryLevelValue!!.toInt()}%"
        } else {
            rh.gs(R.string.value_unavailable_short)
        }

        // Overview compact: pbLevel.visibility based on pump model only (Eros OR not Combo/Dash)
        val useBatteryLevel = pump.model() == PumpType.OMNIPOD_EROS
            || (pump.model() != PumpType.ACCU_CHEK_COMBO && pump.model() != PumpType.OMNIPOD_DASH)

        return StatusItem(
            label = rh.gs(R.string.pb_label),
            age = event?.let { formatAge(it.timestamp) } ?: "-",
            ageStatus = event?.let { getAgeStatus(it.timestamp, IntKey.OverviewBageWarning, IntKey.OverviewBageCritical) } ?: StatusLevel.UNSPECIFIED,
            agePercent = event?.let { getAgePercent(it.timestamp, IntKey.OverviewBageCritical) } ?: 0f,
            level = level,
            levelStatus = if (showLevel) getLevelStatus(batteryLevelValue!!, IntKey.OverviewBattWarning, IntKey.OverviewBattCritical) else StatusLevel.UNSPECIFIED,
            levelPercent = if (showLevel) 1f - (batteryLevelValue!!.toFloat() / 100f) else -1f,
            icon = IcPumpBattery,
            compactAge = hasAge, // Overview: pbAge shown only if replaceable/logging
            compactLevel = useBatteryLevel, // Overview: pbLevel visibility based on pump model only
            expandedLevel = !config.AAPSCLIENT // Actions: AAPSCLIENT suppresses battery level
        )
    }

    private fun formatAge(timestamp: Long): String {
        val diff = dateUtil.computeDiff(timestamp, System.currentTimeMillis())
        val days = diff[TimeUnit.DAYS] ?: 0
        val hours = diff[TimeUnit.HOURS] ?: 0
        return if (rh.shortTextMode()) {
            "${days}${rh.gs(app.aaps.core.interfaces.R.string.shortday)}${hours}${rh.gs(app.aaps.core.interfaces.R.string.shorthour)}"
        } else {
            "$days ${rh.gs(app.aaps.core.interfaces.R.string.days)} $hours ${rh.gs(app.aaps.core.interfaces.R.string.hours)}"
        }
    }

    private fun getAgeStatus(timestamp: Long, warnKey: IntPreferenceKey, urgentKey: IntPreferenceKey): StatusLevel {
        val warnHours = preferences.get(warnKey)
        val urgentHours = preferences.get(urgentKey)
        val ageHours = (System.currentTimeMillis() - timestamp) / (1000 * 60 * 60)
        return when {
            ageHours >= urgentHours -> StatusLevel.CRITICAL
            ageHours >= warnHours   -> StatusLevel.WARNING
            else                    -> StatusLevel.NORMAL
        }
    }

    private fun getAgePercent(timestamp: Long, urgentKey: IntPreferenceKey): Float {
        val urgentHours = preferences.get(urgentKey)
        if (urgentHours <= 0) return 0f
        val ageHours = (System.currentTimeMillis() - timestamp) / (1000.0 * 60 * 60)
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
}