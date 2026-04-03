package app.aaps.pump.common.compose

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.pump.defs.PumpDeviceState
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.common.events.EventRileyLinkDeviceStatusChange
import app.aaps.pump.common.hw.rileylink.R
import app.aaps.pump.common.hw.rileylink.RileyLinkUtil
import app.aaps.pump.common.hw.rileylink.data.RLHistoryItem
import app.aaps.pump.common.hw.rileylink.defs.RileyLinkPumpDevice
import app.aaps.pump.common.hw.rileylink.defs.RileyLinkTargetDevice
import app.aaps.pump.common.hw.rileylink.keys.RileylinkBooleanPreferenceKey
import app.aaps.pump.common.hw.rileylink.service.RileyLinkServiceData
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RileyLinkStatusUiState(
    // General
    val address: String = "-",
    val name: String = "-",
    val batteryLevel: String? = null,
    val connectionStatus: String = "-",
    val connectionError: String = "-",
    val firmwareVersion: String = "-",
    val deviceType: String = "-",
    val configuredDeviceModel: String? = null,
    val connectedDeviceModel: String? = null,
    val serialNumber: String = "-",
    val pumpFrequency: String = "-",
    val lastUsedFrequency: String? = null,
    val lastDeviceContact: String = "-",
    // History
    val historyItems: List<RileyLinkHistoryItem> = emptyList()
)

data class RileyLinkHistoryItem(
    val time: String,
    val source: String,
    val description: String
)

@Stable
@HiltViewModel
class RileyLinkStatusViewModel @Inject constructor(
    private val rh: ResourceHelper,
    private val rileyLinkServiceData: RileyLinkServiceData,
    private val rileyLinkUtil: RileyLinkUtil,
    private val activePlugin: ActivePlugin,
    private val dateUtil: DateUtil,
    private val preferences: Preferences,
    private val rxBus: RxBus
) : ViewModel() {

    private val _uiState = MutableStateFlow(RileyLinkStatusUiState())
    val uiState: StateFlow<RileyLinkStatusUiState> = _uiState

    init {
        refresh()
        viewModelScope.launch {
            rxBus.toFlow(EventRileyLinkDeviceStatusChange::class.java)
                .collect { refresh() }
        }
    }

    fun refresh() {
        _uiState.update { buildState() }
    }

    private fun buildState(): RileyLinkStatusUiState {
        val targetDevice = rileyLinkServiceData.targetDevice
        val rileyLinkPumpDevice = activePlugin.activePumpInternal as? RileyLinkPumpDevice
            ?: return RileyLinkStatusUiState()
        val pumpInfo = rileyLinkPumpDevice.pumpInfo

        // Battery
        val batteryLevel = if (preferences.get(RileylinkBooleanPreferenceKey.ShowReportedBatteryLevel)) {
            rileyLinkServiceData.batteryLevel?.let { rh.gs(R.string.rileylink_battery_level_value, it) } ?: EMPTY
        } else null

        // Firmware
        val firmwareVersion = if (rileyLinkServiceData.isOrange && rileyLinkServiceData.versionOrangeFirmware != null) {
            rh.gs(R.string.rileylink_firmware_version_value_orange, rileyLinkServiceData.versionOrangeFirmware, rileyLinkServiceData.versionOrangeHardware ?: EMPTY)
        } else {
            rh.gs(R.string.rileylink_firmware_version_value, rileyLinkServiceData.versionBLE113 ?: EMPTY, rileyLinkServiceData.versionCC110 ?: EMPTY)
        }

        // Connected device details (Medtronic only)
        val isMedtronic = targetDevice == RileyLinkTargetDevice.MedtronicPump
        val configuredDeviceModel = if (isMedtronic) activePlugin.activePumpInternal.pumpDescription.pumpType.description else null
        val connectedDeviceModel = if (isMedtronic) pumpInfo.connectedDeviceModel else null

        // Last used frequency
        val lastUsedFrequency = rileyLinkServiceData.lastGoodFrequency?.let {
            rh.gs(R.string.rileylink_pump_frequency_value, it)
        }

        // Last device contact
        val lastContact = rileyLinkPumpDevice.lastConnectionTimeMillis
        val lastDeviceContact = if (lastContact == 0L) rh.gs(R.string.riley_link_ble_config_connected_never)
        else dateUtil.dateAndTimeAndSecondsString(lastContact)

        // History
        val historyItems = rileyLinkUtil.rileyLinkHistory
            .filter { isValidHistoryItem(it) }
            .sortedWith(RLHistoryItem.Comparator())
            .map { item ->
                RileyLinkHistoryItem(
                    time = dateUtil.dateAndTimeAndSecondsString(item.dateTime.toDateTime().millis),
                    source = item.source.desc,
                    description = item.getDescription(rh)
                )
            }

        return RileyLinkStatusUiState(
            address = rileyLinkServiceData.rileyLinkAddress ?: EMPTY,
            name = rileyLinkServiceData.rileyLinkName ?: EMPTY,
            batteryLevel = batteryLevel,
            connectionStatus = rh.gs(rileyLinkServiceData.rileyLinkServiceState.resourceId),
            connectionError = rileyLinkServiceData.rileyLinkError?.let { rh.gs(it.getResourceId(targetDevice)) } ?: EMPTY,
            firmwareVersion = firmwareVersion,
            deviceType = rh.gs(targetDevice.resourceId),
            configuredDeviceModel = configuredDeviceModel,
            connectedDeviceModel = connectedDeviceModel,
            serialNumber = pumpInfo.connectedDeviceSerialNumber,
            pumpFrequency = pumpInfo.pumpFrequency,
            lastUsedFrequency = lastUsedFrequency,
            lastDeviceContact = lastDeviceContact,
            historyItems = historyItems
        )
    }

    private fun isValidHistoryItem(item: RLHistoryItem): Boolean =
        item.pumpDeviceState !== PumpDeviceState.Sleeping &&
            item.pumpDeviceState !== PumpDeviceState.Active &&
            item.pumpDeviceState !== PumpDeviceState.WakingUp

    companion object {

        private const val EMPTY = "-"
    }
}
