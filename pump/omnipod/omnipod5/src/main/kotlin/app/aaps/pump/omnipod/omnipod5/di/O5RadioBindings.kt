package app.aaps.pump.omnipod.omnipod5.di

import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.configuration.ExternalOptions
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.pump.emulator.PumpEmulators
import app.aaps.core.utils.toHex
import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.device.BleDeviceManager
import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.session.BleConnection
import app.aaps.pump.omnipod.common.bledriver.comm.session.Session
import app.aaps.pump.omnipod.common.bledriver.pod.definition.PodType
import app.aaps.pump.omnipod.emulator.OmnipodEmulatorTransport
import app.aaps.pump.omnipod.emulator.PodEmulator
import app.aaps.pump.omnipod.emulator.PodLinkState
import app.aaps.pump.omnipod.omnipod5.bledriver.comm.O5LegacyRadio
import app.aaps.pump.omnipod.omnipod5.bledriver.comm.O5Radio
import app.aaps.pump.omnipod.omnipod5.bledriver.comm.pair.O5CertificateStore
import app.aaps.pump.omnipod.omnipod5.bledriver.comm.pair.O5RegistrationData
import app.aaps.pump.omnipod.omnipod5.bledriver.pod.state.O5PodStateManager
import app.aaps.pump.omnipod.omnipod5.bledriver.pod.util.P256KeyGenerator
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

/**
 * Picks the radio of the Omnipod 5 driver: the phone's Bluetooth, or the in-tree pod emulator when the
 * `emulate_omnipod_5` option is on. The driver above the radio is the same in both cases.
 */
@ContributesTo(AppScope::class)
@BindingContainer
object O5RadioBindings {

    @Provides
    @SingleIn(AppScope::class)
    fun provideO5Radio(
        config: Config,
        aapsLogger: AAPSLogger,
        pumpEmulators: PumpEmulators,
        podState: O5PodStateManager,
        p256KeyGenerator: P256KeyGenerator,
        legacyRadio: () -> O5LegacyRadio
    ): O5Radio =
        if (config.isEnabled(ExternalOptions.EMULATE_OMNIPOD_5)) {
            aapsLogger.debug(LTag.PUMPEMULATOR, "Omnipod 5 emulator active - real Bluetooth is not used")
            O5EmulatorRadio(aapsLogger, config, pumpEmulators, podState, p256KeyGenerator)
        } else legacyRadio()
}

/**
 * The emulated Omnipod 5 pod as the radio of the driver.
 *
 * A pairing needs controller credentials, which a user imports from the vendor. The emulated pod checks none, so a
 * key pair made here stands for them, with a controller id no real controller has. They are kept in memory only, are
 * never stored and never leave the phone, and the vendor is never asked.
 */
class O5EmulatorRadio(
    private val aapsLogger: AAPSLogger,
    config: Config,
    pumpEmulators: PumpEmulators,
    private val podState: O5PodStateManager,
    private val p256KeyGenerator: P256KeyGenerator
) : O5Radio {

    /** Where a test reaches the test hooks of the emulated pod. */
    val transport = OmnipodEmulatorTransport(
        emulator = pumpEmulators.getOrCreate(PodEmulator.KEY_OMNIPOD_5) {
            PodEmulator(aapsLogger, productId = PodEmulator.PRODUCT_ID_OMNIPOD_5)
        },
        aapsLogger = aapsLogger,
        config = config,
        podType = PodType.OMNIPOD_5,
        // As `O5Connection` of the real radio does: the driver reads from the pod state whether the link is up.
        onLinkState = { state ->
            when (state) {
                PodLinkState.CONNECTING   -> {
                    podState.connectionAttempts++
                    podState.bluetoothConnectionState = O5PodStateManager.BluetoothConnectionState.CONNECTING
                }

                PodLinkState.CONNECTED    -> podState.bluetoothConnectionState = O5PodStateManager.BluetoothConnectionState.CONNECTED
                PodLinkState.DISCONNECTED -> podState.bluetoothConnectionState = O5PodStateManager.BluetoothConnectionState.DISCONNECTED
            }
        },
        // As `O5Connection.createSession`: a session that signs the insulin commands.
        sessionFactory = { messageIO, ids, keys, enDecrypt ->
            val controllerId = requireNotNull(podState.controllerId) { "Missing controllerId, cannot establish a signed O5 session" }
            Session(
                aapsLogger, messageIO, ids, sessionKeys = keys, enDecrypt = enDecrypt,
                commandSigner = O5CertificateStore(aapsLogger, p256KeyGenerator, controllerId)
            )
        }
    )

    init {
        installEmulatedCredentials()
    }

    override val deviceManager: BleDeviceManager get() = transport

    override fun scanForNewPod(): String = transport.scanForNewPod()

    override fun createConnection(podAddress: String, controllerId: Long?): BleConnection =
        transport.createConnection(podAddress, controllerId ?: podState.controllerId)

    private fun installEmulatedCredentials() {
        if (O5RegistrationData.contains(EMULATED_CONTROLLER_ID)) return
        val privateKey = p256KeyGenerator.generatePrivateKey()
        O5RegistrationData.install(
            O5RegistrationData(
                controllerId = EMULATED_CONTROLLER_ID,
                privateKeyHex = privateKey.toHex(),
                publicKeyHex = p256KeyGenerator.publicFromPrivate(privateKey).toHex(),
                // The emulated pod reads no certificate. Empty ones are valid input for the driver.
                intermediateCABase64 = "",
                tlsCertificateBase64 = ""
            ),
            O5RegistrationData.O5RegistrationSource.BUILT_IN
        )
    }

    private companion object {

        /** Not the id of a real controller. The low two bits are zero, as the driver's pod ids are made from it. */
        const val EMULATED_CONTROLLER_ID = 0x0E0E0E00L
    }
}
