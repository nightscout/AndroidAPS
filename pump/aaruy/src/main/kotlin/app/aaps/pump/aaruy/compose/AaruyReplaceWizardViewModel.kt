package app.aaps.pump.aaruy.compose

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.data.model.ICfg
import app.aaps.core.data.model.TE
import app.aaps.core.data.model.labelOfInsulin
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.data.time.T
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.insulin.InsulinManager
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.compose.pump.switchToWizardInsulin
import app.aaps.core.ui.compose.siteRotation.BodyType
import app.aaps.core.ui.compose.siteRotation.SiteLocationStepHost
import app.aaps.pump.aaruy.AaruyPump
import app.aaps.pump.aaruy.R
import app.aaps.pump.aaruy.code.ConnectionState
import app.aaps.pump.aaruy.common.enums.AaruyPumpState
import app.aaps.pump.aaruy.common.util.AaruyConst
import app.aaps.pump.aaruy.services.AaruyBLE
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The order is the one a real pump went through: rewind, prime, fill the cannula, start basal. */
enum class AaruyReplaceStep {
    REWIND,
    ASSEMBLE,

    /** Only with more than one insulin to choose from. */
    SELECT_INSULIN,
    PRIME,
    CONNECT,

    /** Only when site rotation is on for pumps. */
    SITE_LOCATION,
    FILL_CANNULA,
    DONE
}

data class AaruyReplaceUiState(
    val step: AaruyReplaceStep = AaruyReplaceStep.REWIND,
    /** The steps of this run. The optional ones are left out when they do not apply. */
    val steps: List<AaruyReplaceStep> = AaruyReplaceStep.entries.filter { it != AaruyReplaceStep.SELECT_INSULIN && it != AaruyReplaceStep.SITE_LOCATION },
    /** The motor moves, or the pump is asked to. Nothing else may be sent, and the wizard may not be left. */
    val busy: Boolean = false,
    val primed: Boolean = false,
    /** 0 is a 6 mm cannula, 1 is 9 mm. */
    val needleType: Int = 0,
    val confirmDroplets: Boolean = false,
    val dropletsSeenByPump: Boolean = false,
    /** The cannula is filled. A second fill would go into the body, so only the basal start can be tried again. */
    val cannulaFilled: Boolean = false,
    val error: Int? = null
)

@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
@ViewModelKey
@Stable
@Inject
class AaruyReplaceWizardViewModel(
    private val aapsLogger: AAPSLogger,
    private val aaruyBle: AaruyBLE,
    private val aaruyPump: AaruyPump,
    private val pumpSync: PumpSync,
    private val dateUtil: DateUtil,
    private val rh: ResourceHelper,
    private val rxBus: RxBus,
    private val preferences: Preferences,
    private val profileFunction: ProfileFunction,
    private val insulinManager: InsulinManager,
    private val persistenceLayer: PersistenceLayer
) : ViewModel(), SiteLocationStepHost {

    private val _uiState = MutableStateFlow(AaruyReplaceUiState(needleType = aaruyPump.softNeedleType))
    val uiState: StateFlow<AaruyReplaceUiState> = _uiState

    private var primeTimeout: Job? = null

    val connected: StateFlow<Boolean> = aaruyPump.connectionStateFlow
        .map { it == ConnectionState.CONNECTED }
        .stateIn(viewModelScope, SharingStarted.Eagerly, aaruyPump.connectionState == ConnectionState.CONNECTED)

    // Insulin selection, as on the other pumps
    private val _availableInsulins = MutableStateFlow<List<ICfg>>(emptyList())
    val availableInsulins: StateFlow<List<ICfg>> = _availableInsulins
    private val _selectedInsulin = MutableStateFlow<ICfg?>(null)
    val selectedInsulin: StateFlow<ICfg?> = _selectedInsulin
    private val _activeInsulinLabel = MutableStateFlow<String?>(null)
    val activeInsulinLabel: StateFlow<String?> = _activeInsulinLabel
    val concentrationEnabled: Boolean get() = preferences.get(BooleanKey.GeneralInsulinConcentration)

    // Site location, as on the other pumps
    private val _siteLocation = MutableStateFlow(TE.Location.NONE)
    override val siteLocation: StateFlow<TE.Location> = _siteLocation
    private val _siteArrow = MutableStateFlow(TE.Arrow.NONE)
    override val siteArrow: StateFlow<TE.Arrow> = _siteArrow
    private var siteRotationEntries: List<TE> = emptyList()

    init {
        // UNDISPATCHED: the answer to a command must not be missed.
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            aaruyPump.errCode.collect { onPumpMessage(it) }
        }
        loadChoices()
    }

    /**
     * The view model lives as long as the pump screen, not as the wizard. Without this the next change of reservoir
     * starts where the last one ended.
     */
    fun reset() {
        primeTimeout?.cancel()
        _uiState.value = AaruyReplaceUiState(needleType = aaruyPump.softNeedleType)
        _siteLocation.value = TE.Location.NONE
        _siteArrow.value = TE.Arrow.NONE
        loadChoices()
    }

    private fun loadChoices() {
        viewModelScope.launch {
            val insulins = insulinManager.insulins.map { it.deepClone() }
            val activeLabel = insulins.labelOfInsulin(profileFunction.getProfile()?.iCfg)
            _availableInsulins.value = insulins
            _selectedInsulin.value = insulins.find { it.insulinLabel == activeLabel } ?: insulins.firstOrNull()
            _activeInsulinLabel.value = activeLabel
            val siteRotation = preferences.get(BooleanKey.SiteRotationManagePump)
            if (siteRotation) {
                siteRotationEntries = persistenceLayer.getTherapyEventDataFromTime(dateUtil.now() - T.days(45).msecs(), false)
                    .filter { it.type == TE.Type.CANNULA_CHANGE || it.type == TE.Type.SENSOR_CHANGE }
            }
            _uiState.update { state ->
                state.copy(steps = AaruyReplaceStep.entries.filter {
                    when (it) {
                        AaruyReplaceStep.SELECT_INSULIN -> insulins.size > 1
                        AaruyReplaceStep.SITE_LOCATION  -> siteRotation
                        else                            -> true
                    }
                })
            }
        }
    }

    /** Goes to the step that follows [from] in this run. */
    private fun next(from: AaruyReplaceStep) {
        _uiState.update { state ->
            val index = state.steps.indexOf(from)
            state.copy(step = state.steps.getOrElse(index + 1) { AaruyReplaceStep.DONE }, error = null)
        }
    }

    private fun onPumpMessage(code: Int) {
        if (code shr 8 != 0) return
        when ((code and 0xFF).toByte()) {
            AaruyConst.errCodeType.FLAG_LOCATE_POSITION             -> primeDone(droplets = false)
            AaruyConst.errCodeType.FLAG_LOCATE_POS_LIQUID_DISCHARGE -> primeDone(droplets = true)
            AaruyConst.errCodeType.DISCHARGE_LIQUID                 -> primeDone(droplets = false)
        }
    }

    private fun primeDone(droplets: Boolean) {
        if (_uiState.value.step != AaruyReplaceStep.PRIME) return
        _uiState.update { it.copy(busy = false, primed = true, dropletsSeenByPump = it.dropletsSeenByPump || droplets) }
    }

    private fun command(error: Int, block: suspend () -> Boolean) {
        if (_uiState.value.busy) return
        _uiState.update { it.copy(busy = true, error = null) }
        viewModelScope.launch(Dispatchers.IO) {
            val ok = block()
            aapsLogger.debug(LTag.PUMP, "Replace ${_uiState.value.step}: $ok")
            _uiState.update { it.copy(busy = false, error = if (ok) null else error) }
        }
    }

    /**
     * Sends a motor command and waits for the pump to pass through [during] and end in [end]. Seen on a real pump: the
     * pump is in [end] already before a cannula fill, so [end] alone says nothing: a refused or lost command looked done.
     */
    private suspend fun runMotor(during: AaruyPumpState, end: AaruyPumpState, send: () -> Unit): Boolean {
        aaruyPump.forgetSeenStates()
        send()
        val start = dateUtil.now()
        while (dateUtil.now() - start < MOTOR_TIMEOUT_MS) {
            if (aaruyPump.hasSeen(during) && aaruyPump.pumpState == end) return true
            delay(POLL_MS)
        }
        return false
    }

    fun rewind() {
        // Not through the command queue: a bolus the loop started after the wizard opened would be ended.
        if (aaruyPump.pumpState == AaruyPumpState.LARGEDOSE_RUNNING) {
            _uiState.update { it.copy(error = R.string.aaruy_state_bolus) }
            return
        }
        command(R.string.alarm_rewinding_fail) {
            val ok = runMotor(AaruyPumpState.PUSHBACK_RUNNING, AaruyPumpState.STOP_RUNNING) { aaruyBle.replaceDevice() }
            if (ok) next(AaruyReplaceStep.REWIND)
            ok
        }
    }

    fun assembled() = next(AaruyReplaceStep.ASSEMBLE)

    fun selectInsulin(iCfg: ICfg) {
        _selectedInsulin.value = iCfg
    }

    fun insulinSelected() = next(AaruyReplaceStep.SELECT_INSULIN)

    /** A real pump says it is done long before the piston stops, so the answer ends the wait. */
    fun prime() {
        if (_uiState.value.busy) return
        _uiState.update { it.copy(busy = true, error = null) }
        primeTimeout?.cancel()
        primeTimeout = viewModelScope.launch(Dispatchers.IO) {
            aaruyBle.pushForward()
            delay(PRIME_TIMEOUT_MS)
            if (_uiState.value.busy && _uiState.value.step == AaruyReplaceStep.PRIME) {
                _uiState.update { it.copy(busy = false, error = R.string.alarm_discharge_liquid_fail) }
            }
        }
    }

    fun askDroplets() = _uiState.update { it.copy(confirmDroplets = true) }
    fun dropletsNotSeen() = _uiState.update { it.copy(confirmDroplets = false) }
    fun dropletsSeen() {
        _uiState.update { it.copy(confirmDroplets = false) }
        next(AaruyReplaceStep.PRIME)
    }

    fun connectedToBody() = next(AaruyReplaceStep.CONNECT)

    override fun updateSiteLocation(location: TE.Location) {
        _siteLocation.value = location
    }

    override fun updateSiteArrow(arrow: TE.Arrow) {
        _siteArrow.value = arrow
    }

    override fun completeSiteLocation() = next(AaruyReplaceStep.SITE_LOCATION)

    override fun skipSiteLocation() {
        _siteLocation.value = TE.Location.NONE
        _siteArrow.value = TE.Arrow.NONE
        next(AaruyReplaceStep.SITE_LOCATION)
    }

    override fun bodyType(): BodyType = BodyType.fromPref(preferences.get(IntKey.SiteRotationUserProfile))
    override fun siteRotationEntries(): List<TE> = siteRotationEntries

    fun selectNeedle(type: Int) {
        aaruyPump.softNeedleType = type
        _uiState.update { it.copy(needleType = type) }
    }

    /**
     * Basal starts by itself after this: the insulin for the cannula is the last step before it. A failed basal start
     * says so, and the next press only starts basal: "Priming failed" would make the user fill the cannula again.
     */
    fun fillCannula() {
        if (_uiState.value.cannulaFilled) {
            command(R.string.alarm_basal_run_fail) { startBasal(cannulaChanged = true) }
            return
        }
        if (_uiState.value.busy) return
        _uiState.update { it.copy(busy = true, error = null) }
        viewModelScope.launch(Dispatchers.IO) {
            aaruyBle.syncAndLoadBasicParam()
            val filled = runMotor(AaruyPumpState.EXHAUSTING, AaruyPumpState.STOP_RUNNING) { aaruyBle.needlePushAir(_uiState.value.needleType.toByte()) }
            _uiState.update { it.copy(cannulaFilled = filled) }
            val ok = filled && startBasal(cannulaChanged = true)
            aapsLogger.debug(LTag.PUMP, "Replace fill: filled $filled, basal $ok")
            _uiState.update {
                it.copy(busy = false, error = when {
                    ok     -> null
                    filled -> R.string.alarm_basal_run_fail
                    else   -> R.string.alarm_discharge_liquid_fail
                })
            }
        }
    }

    fun skipFillingCannula() = command(R.string.alarm_basal_run_fail) { startBasal(cannulaChanged = false) }

    private suspend fun startBasal(cannulaChanged: Boolean): Boolean {
        aaruyPump.alreadyPushBack = false
        aaruyPump.clearAlarmState()
        aaruyBle.startBaseInject()
        val start = dateUtil.now()
        while (aaruyPump.pumpState != AaruyPumpState.BASALRATE_RUNNING) {
            if (dateUtil.now() - start > BASAL_TIMEOUT_MS) return false
            delay(POLL_MS)
        }
        val now = dateUtil.now()
        pumpSync.insertTherapyEventIfNewWithTimestamp(now, TE.Type.INSULIN_CHANGE, pumpType = aaruyPump.pumpType(), pumpSerial = aaruyPump.pumpSN)
        if (cannulaChanged) {
            pumpSync.insertTherapyEventIfNewWithTimestamp(now, TE.Type.CANNULA_CHANGE, pumpType = aaruyPump.pumpType(), pumpSerial = aaruyPump.pumpSN)
            saveSiteLocation(now)
        }
        switchInsulin()
        _uiState.update { it.copy(step = AaruyReplaceStep.DONE) }
        return true
    }

    /** The site belongs to the new cannula. Without one it is not saved, and the old cannula keeps its age. */
    private suspend fun saveSiteLocation(time: Long) {
        val location = _siteLocation.value.takeIf { it != TE.Location.NONE }
        val arrow = _siteArrow.value.takeIf { it != TE.Arrow.NONE }
        if (location == null && arrow == null) return
        // Basal runs already. A failure here must not leave the wizard waiting.
        try {
            persistenceLayer.getTherapyEventDataFromToTime(time, time)
                .firstOrNull { it.type == TE.Type.CANNULA_CHANGE }
                ?.let { persistenceLayer.insertOrUpdateTherapyEvent(it.copy(location = location, arrow = arrow)) }
        } catch (e: Exception) {
            aapsLogger.error(LTag.PUMP, "The site of the new cannula was not saved", e)
        }
    }

    /** The pump runs by now: a refused switch cannot stop the change, but it says why. */
    private suspend fun switchInsulin() {
        val selected = _selectedInsulin.value ?: return
        if (selected.insulinLabel == _activeInsulinLabel.value) return
        val source = if (aaruyPump.pumpType() == PumpType.AARUY_BASE) Sources.AaruyBase else Sources.AaruyFull
        try {
            switchToWizardInsulin(profileFunction, rh, rxBus, selected, source)
        } catch (e: Exception) {
            aapsLogger.error(LTag.PUMP, "The insulin was not switched", e)
        }
    }

    private companion object {

        const val POLL_MS = 50L

        // UNVERIFIED: how long the motor takes with a full reservoir. A real pump with none was done in less than a second.
        const val MOTOR_TIMEOUT_MS = 120_000L
        const val PRIME_TIMEOUT_MS = 30_000L
        const val BASAL_TIMEOUT_MS = 5_000L
    }
}
