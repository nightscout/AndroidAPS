package info.nightscout.androidaps.plugins.pump.carelevo.ble.core

import info.nightscout.androidaps.plugins.pump.carelevo.ble.CarelevoBleSource
import info.nightscout.androidaps.plugins.pump.carelevo.ble.data.BleParams
import info.nightscout.androidaps.plugins.pump.carelevo.ble.data.BleState
import info.nightscout.androidaps.plugins.pump.carelevo.ble.data.BondingState
import info.nightscout.androidaps.plugins.pump.carelevo.ble.data.CommandResult
import info.nightscout.androidaps.plugins.pump.carelevo.ble.data.DeviceModuleState.Companion.codeToDeviceResult
import info.nightscout.androidaps.plugins.pump.carelevo.ble.data.FailureState
import info.nightscout.androidaps.plugins.pump.carelevo.ble.data.NotificationState
import info.nightscout.androidaps.plugins.pump.carelevo.ble.data.PeripheralConnectionState
import info.nightscout.androidaps.plugins.pump.carelevo.ble.data.PeripheralScanResult
import info.nightscout.androidaps.plugins.pump.carelevo.ble.data.ServiceDiscoverState
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

class CarelevoBleControllerImpl @Inject constructor(
    private val params: BleParams,
    private val btManager: CarelevoBleManager
) : CarelevoBleController {

    private val bleCommandLifecycle = CompositeDisposable()
    private var isLastCommandSuccess = false

    init {
        initController()
    }

    override fun initController() {
        initializedBluetoothState()
        isLastCommandSuccess = false
        startCommandExecutor()
    }

    override fun registerPeripheralInfo() {
        btManager.registerPeripheralInfoRegistered()
    }

    override fun unRegisterPeripheralInfo() {
        btManager.unRegisterPeripheralInfoRegistered()
    }

    override fun isBluetoothEnabled(): Boolean {
        return btManager.isBluetoothEnabled()
    }

    override fun getConnectedAddress(): String? {
        return btManager.getGatt()?.let { gatt ->
            gatt.device?.address
        }
    }

    override fun getParams(): BleParams {
        return params
    }

    override fun checkGatt(): Boolean {
        return btManager.getGatt() == null
    }

    override fun clearGatt() {
        btManager.disableManager()
    }

    override fun clearOnlyGatt() {
        btManager.clearGatt()
    }

    override fun clearScan() {
        CarelevoBleSource._scanDevices.onNext(PeripheralScanResult.Init(listOf()))
    }

    override fun isBonded(address: String): Boolean {
        return btManager.isBonded(address)
    }

    override fun clearBond(address: String): CommandResult<Boolean> {
        return btManager.clearBond(address)
    }

    override fun unBondDevice(): CommandResult<Boolean> {
        getConnectedAddress()?.let {
            return btManager.unBondDevice(it)
        } ?: return CommandResult.Failure(FailureState.FAILURE_INVALID_PARAMS, "Connected address not exist")
    }

    override fun unBondDevice(address: String): CommandResult<Boolean> {
        return btManager.unBondDevice(address)
    }

    override fun pend(command: BleCommand): CommandResult<Boolean> {
        return runCatching {
            CarelevoBleSource.bleCommandChains.onNext(mutableListOf(command))
            CommandResult.Pending(true)
        }.getOrElse {
            it.printStackTrace()
            CommandResult.Error(it)
        }
    }

    override fun execute(command: BleCommand): Single<CommandResult<Boolean>> {
        when (command) {
            is StartScan -> with(command) {
                return Single.just(btManager.startScan(scanFilter))
                // return btManager.startScan(scanFilter)
            }

            is StopScan -> {
                return Single.just(btManager.stopScan())
                // return btManager.stopScan()
            }

            is Connect -> with(command) {
                return connectToSingle(address)
                // return btManager.connectTo(address)
            }

            is Disconnect -> {
                return Single.just(btManager.disconnectFrom())
                // return btManager.disconnectFrom()
            }

            is DiscoveryService -> {
                return Single.just(btManager.discoverService())
                // return btManager.discoverService()
            }

            is WriteToCharacteristic -> with(command) {
                return Single.just(btManager.writeCharacteristic(uuid = characteristicUuid, payload = payload))
                // return btManager.writeCharacteristic(uuid = characteristicUuid, payload = payload)
            }

            is ReadFromCharacteristic -> with(command) {
                return Single.just(btManager.readCharacteristic(characteristicUuid = characteristicUuid))
                // return btManager.readCharacteristic(characteristicUuid = characteristicUuid)
            }

            is EnableNotifications -> with(command) {
                return Single.just(btManager.enabledNotifications(uuid = characteristicUuid))
                // return btManager.enabledNotifications(uuid = characteristicUuid)
            }

            is DisableNotifications -> with(command) {
                return Single.just(btManager.disabledNotifications(uuid = characteristicUuid))
                // return btManager.disabledNotifications(uuid = characteristicUuid)
            }

            is UnBondDevice -> with(command) {
                return Single.just(btManager.unBondDevice(macAddress = address))
                // btManager.unBondDevice(macAddress = address)
            }

            is Delay -> with(command) {
                // delay(duration)
                Thread.sleep(duration)
                return Single.just(CommandResult.Success(true))
                // return CommandResult.Success(true)
            }
        }
        return Single.just(CommandResult.Success(false))
    }

    private fun connectToSingle(address: String): Single<CommandResult<Boolean>> {
        return Single.create { emitter ->
            val job = CoroutineScope(Dispatchers.IO).launch {
                try {
                    val result = btManager.connectTo(address)
                    if (!emitter.isDisposed) {
                        emitter.onSuccess(result)
                    }
                } catch (e: Throwable) {
                    if (!emitter.isDisposed) {
                        emitter.onError(e)
                    }
                }
            }

            emitter.setCancellable { job.cancel() }
        }
    }

    override fun stop(): Boolean {
        stopCommandExecutor()
        btManager.disableManager()
        return true
    }

    override fun isConnectedNow(address: String): Boolean {
        return btManager.isConnected(address)
    }

    private fun initializedBluetoothState() {
        CarelevoBleSource._bluetoothState.onNext(
            BleState(
                isEnabled = btManager.getBluetoothAdapterState().codeToDeviceResult(),
                isBonded = BondingState.BOND_NONE,
                isConnected = PeripheralConnectionState.CONN_STATE_NONE,
                isServiceDiscovered = ServiceDiscoverState.DISCOVER_STATE_NONE,
                isNotificationEnabled = NotificationState.NOTIFICATION_NONE
            )
        )
    }

    private fun startCommandExecutor() {
        bleCommandLifecycle += CarelevoBleSource.bleCommandChains
            .subscribe {
                it.forEach { bleCommand ->
                    executeSingleCommand(bleCommand)
                }
            }
    }

    private fun executeSingleCommand(bleCommand: BleCommand): Single<Boolean> {

        return if (btManager.isBluetoothEnabled()) {
            execute(bleCommand)
                .concatMap { result ->
                    isLastCommandSuccess = if (result is CommandResult.Success) {
                        result.data
                    } else {
                        false
                    }
                    if (!isLastCommandSuccess && bleCommand.isImportant) {
                        executeCommandWhileComplete(bleCommand)
                    } else {
                        Single.just(isLastCommandSuccess)
                    }
                }
        } else {
            Single.just(false)
        }
    }

    private fun executeCommandWhileComplete(bleCommand: BleCommand): Single<Boolean> {
        var result = Single.just(false)
        while (!isLastCommandSuccess && bleCommand.retryCnt > 0) {
            if (btManager.isBluetoothEnabled()) {
                result = execute(bleCommand).concatMap {
                    if (it is CommandResult.Success) {
                        Single.just(it.data)
                    } else {
                        Single.just(false)
                    }
                }
            }
        }
        return result
    }

    private fun stopCommandExecutor() {
        bleCommandLifecycle.dispose()
    }
}