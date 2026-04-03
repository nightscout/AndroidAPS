package app.aaps.pump.danars.compose

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Stable
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.insulin.ConcentrationHelper
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.pump.ble.BleTransport
import app.aaps.core.interfaces.pump.ble.PairingState
import app.aaps.core.interfaces.pump.ble.PairingStep
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.compose.pump.ActionCategory
import app.aaps.core.ui.compose.pump.PumpAction
import app.aaps.pump.dana.DanaPump
import app.aaps.pump.dana.R
import app.aaps.pump.dana.compose.DanaOverviewViewModel
import app.aaps.pump.dana.keys.DanaStringNonKey
import app.aaps.pump.danars.DanaRSPlugin
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

@HiltViewModel
@Stable
class DanaRSOverviewViewModel @Inject constructor(
    aapsLogger: AAPSLogger,
    rh: ResourceHelper,
    rxBus: RxBus,
    aapsSchedulers: AapsSchedulers,
    commandQueue: CommandQueue,
    dateUtil: DateUtil,
    private val danaPump: DanaPump,
    activePlugin: ActivePlugin,
    ch: ConcentrationHelper,
    persistenceLayer: PersistenceLayer,
    private val danaRSPlugin: DanaRSPlugin,
    uel: UserEntryLogger,
    preferences: Preferences,
    private val bleTransport: BleTransport,
    @ApplicationContext context: Context
) : DanaOverviewViewModel(
    aapsLogger = aapsLogger,
    rh = rh,
    rxBus = rxBus,
    aapsSchedulers = aapsSchedulers,
    commandQueue = commandQueue,
    dateUtil = dateUtil,
    danaPump = danaPump,
    activePlugin = activePlugin,
    ch = ch,
    persistenceLayer = persistenceLayer,
    uel = uel,
    preferences = preferences,
    context = context
) {

    override fun performUnpair() {
        uel.log(Action.CLEAR_PAIRING_KEYS, Sources.Dana)
        val address = preferences.get(DanaStringNonKey.MacAddress)
        danaRSPlugin.clearPairing()
        preferences.remove(DanaStringNonKey.MacAddress)
        preferences.remove(DanaStringNonKey.RsName)
        preferences.remove(DanaStringNonKey.EmulatorDeviceName)
        if (address.isNotBlank()) bleTransport.adapter.removeBond(address)
        bleTransport.updatePairingState(PairingState(step = PairingStep.IDLE))
        danaRSPlugin.changePump() // resets mDeviceAddress/mDeviceName — makes isConfigured() return false
        rxTrigger.value = System.currentTimeMillis()
    }

    override fun buildManagementActions(pump: DanaPump, isInitialized: Boolean, isConfigured: Boolean): List<PumpAction> = buildList {
        // User Settings (only for RS pumps with non-legacy firmware)
        if (pump.hwModel != 1 && pump.protocol != 0x00) {
            add(
                PumpAction(
                    label = rh.gs(R.string.danar_user_options),
                    icon = Icons.Filled.Settings,
                    category = ActionCategory.MANAGEMENT,
                    visible = isInitialized,
                    onClick = { onUserSettingsClick() }
                )
            )
        }

        // Pair / Unpair
        if (isConfigured) {
            add(
                PumpAction(
                    label = rh.gs(app.aaps.core.ui.R.string.pump_unpair),
                    icon = Icons.Filled.Bluetooth,
                    category = ActionCategory.MANAGEMENT,
                    onClick = { onUnpairClick() }
                )
            )
        } else {
            add(
                PumpAction(
                    label = rh.gs(app.aaps.core.ui.R.string.pairing),
                    icon = Icons.Filled.Bluetooth,
                    category = ActionCategory.MANAGEMENT,
                    onClick = { onPairClick() }
                )
            )
        }
    }
}
