package app.aaps.pump.aaruy.compose

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.insulin.InsulinManager
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileRepository
import app.aaps.core.interfaces.pump.ble.ScannedDevice
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.ui.compose.pump.ProfileGateStepHost
import app.aaps.core.ui.compose.pump.activateProfileFromGate
import app.aaps.pump.aaruy.AaruyPump
import app.aaps.pump.aaruy.code.ConnectionState
import app.aaps.pump.aaruy.services.AaruyBLE
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

enum class AaruyPairStep {

    /** Only when no profile runs: a new pump needs a basal profile. */
    PROFILE_GATE,
    BLE_SCAN,

    /** A real pump does not pair until its button is pressed. Android gives up after 30 s, and the driver tries again. */
    PRESS_BUTTON,
    COMPLETE
}

@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
@ViewModelKey
@Stable
@Inject
class AaruyPairWizardViewModel(
    private val aaruyBle: AaruyBLE,
    private val aaruyPump: AaruyPump,
    private val aapsLogger: AAPSLogger,
    private val rh: ResourceHelper,
    private val rxBus: RxBus,
    private val dateUtil: DateUtil,
    private val profileFunction: ProfileFunction,
    private val profileRepository: ProfileRepository,
    private val insulinManager: InsulinManager
) : ViewModel(), ProfileGateStepHost {

    /** Null until it is known whether the profile step is needed. */
    private val _step = MutableStateFlow<AaruyPairStep?>(null)
    val step: StateFlow<AaruyPairStep?> = _step
    private val _steps = MutableStateFlow(AaruyPairStep.entries.filter { it != AaruyPairStep.PROFILE_GATE })
    val steps: StateFlow<List<AaruyPairStep>> = _steps
    val devices: StateFlow<List<ScannedDevice>> = aaruyBle.scannedDevices

    /** The profile step was left with Cancel. */
    private val _cancelled = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val cancelled: SharedFlow<Unit> = _cancelled

    private val _availableProfiles = MutableStateFlow<List<String>>(emptyList())
    override val availableProfiles: StateFlow<List<String>> = _availableProfiles
    private val _selectedProfile = MutableStateFlow<String?>(null)
    override val selectedProfile: StateFlow<String?> = _selectedProfile

    init {
        viewModelScope.launch {
            if (profileFunction.getRequestedProfile() == null) {
                val names = profileRepository.profiles.value.map { it.name }
                _availableProfiles.value = names
                val activeName = profileFunction.getOriginalProfileName()
                _selectedProfile.value = activeName.takeIf { it in names } ?: names.firstOrNull()
                _steps.value = AaruyPairStep.entries
                _step.value = AaruyPairStep.PROFILE_GATE
            } else _step.value = AaruyPairStep.BLE_SCAN
        }
    }

    fun startScan() = aaruyBle.startScan()
    fun stopScan() = aaruyBle.stopScan()

    fun selectDevice(device: ScannedDevice) {
        stopScan()
        _step.value = AaruyPairStep.PRESS_BUTTON
        aaruyBle.pair(device)
        viewModelScope.launch {
            aaruyPump.connectionStateFlow.first { it == ConnectionState.CONNECTED }
            _step.value = AaruyPairStep.COMPLETE
        }
    }

    override fun selectProfile(name: String) {
        _selectedProfile.value = name
    }

    override fun activateSelectedProfile() {
        val name = _selectedProfile.value ?: return
        val store = profileRepository.profile.value ?: return
        val iCfg = insulinManager.insulins.firstOrNull() ?: return
        val source = if (aaruyPump.pumpType() == PumpType.AARUY_BASE) Sources.AaruyBase else Sources.AaruyFull
        viewModelScope.launch {
            // A refused switch says why and keeps the step on screen.
            if (activateProfileFromGate(profileFunction, rh, rxBus, store, name, iCfg, source, dateUtil.now())) _step.value = AaruyPairStep.BLE_SCAN
            else aapsLogger.error(LTag.PUMP, "ProfileGate: createProfileSwitch failed for $name")
        }
    }

    override fun cancelGate() {
        _cancelled.tryEmit(Unit)
    }

    /** Leaving before the pump paired: no half paired pump stays behind. */
    fun cancel() {
        stopScan()
        if (_step.value == AaruyPairStep.PRESS_BUTTON) aaruyBle.unbindDevice()
    }

    override fun onCleared() {
        super.onCleared()
        stopScan()
    }
}
