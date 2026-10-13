package info.nightscout.pump.combov2

import app.aaps.core.interfaces.sharedPreferences.SP
import info.nightscout.comboctl.base.BluetoothAddress
import info.nightscout.comboctl.base.CurrentTbrState
import info.nightscout.comboctl.base.InvariantPumpData
import info.nightscout.comboctl.base.Nonce
import info.nightscout.comboctl.base.PumpStateStore
import info.nightscout.comboctl.base.PumpStateStoreAccessException
import info.nightscout.comboctl.base.Tbr
import info.nightscout.comboctl.base.toBluetoothAddress
import info.nightscout.comboctl.base.toCipher
import info.nightscout.comboctl.base.toNonce
import kotlinx.datetime.UtcOffset
import kotlin.reflect.KClassifier
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * [PumpStateStore] subclass that uses AndroidAPS' [SP] class for storing the pump state.
 *
 * This pump state has a special logic to address the following problem:
 *
 * What if the user already paired AAPS with a Combo, and then imports and old AAPS settings file, intending to just restore
 * other settings like the basal profile, but does _not_ intend to import an old pump state? After all, the pump state is
 * in the AAPS SP, so the state values will also be written into a settings file when exporting said settings. If we just
 * relied on the imported configuration, the current pump state - including pairing info like the PC and CP keys - would
 * be overwritten with the old ones from the imported settings. If that pump state is from an previous pairing, the user
 * would have to unnecessarily re-pair the Combo every time settings get imported.
 *
 * This pump state store has two special characteristics to be aware of, which work to solve the problem.
 *
 * 1. This subclass stores at most only one state. The base class is designed to be able to store multiple states,
 *  but this is not supported in AndroidAPS. For this reason, several methods do not actually use their
 *  pumpAddress arguments. One example is the [hasPumpState] method.
 * 2. When AndroidAPS imports a configuration that was previously stored as an XML file, it calls plugin methods
 *  before and after the import was done. The ComboV2 driver uses this to restore an existing pump state. This is
 *  necessary, because importing a configuration wipes the values in the [sp] store.
 *
 * The second characteristic is why this subclass has the [createBackup] and [applyBackup] methods, as well as
 * the [hasAnyPumpState] method. When a configuration is imported, AndroidAPS calls the plugin class' method
 * mentioned earlier. In that method, the plugin uses [createBackup] to make a backup of the pump state.
 * During the import, the pump state is wiped. Once the import is done, another plugin method is called
 * by AndroidAPS. Inside that method, [applyBackup] is called to restore the previously pump state out of
 * the backup. The result is that the pump state remains intact. (If there is no pump state, then the
 * pre- and post-import methods do nothing of course.)
 *
 * Should the imported configuration contain its own pump state, the following logic is applied:
 *
 * If there is a pump state already, a backup is created as usual. Then, post-import, if there is a backup,
 * it will override the pump state that was imported from the configuration XML. If the imported configuration
 * contains a pump state, and there is no backup, then the pump state from the imported configuration is used.
 *
 * This logic, together with the one mentioned earlier, covers the following possible cases:
 *
 * 1. There is no pump state in the SP prior to the import, and there is none in the imported configuration.
 *   Nothing pump state related is done in this case.
 * 2. There is no pump state in the SP prior to the import, and there is one in the imported configuration.
 *   As mentioned above, no pump state backup is created prior to the import, and the pump state from the
 *   imported configuration is used instead.
 * 3. There is a pump state in the SP prior to the import, and there is none in the imported configuration.
 *   A pump state backup is created prior to the import, and it is restored once the import is done. This
 *   prevents the import from wiping the pump state.
 * 4. There is a pump state in the SP prior to the import, and there is one in the imported configuration.
 *   A pump state backup is created prior to the import, and it is restored once the import is done,
 *   overwriting the pump state from the imported configuration.
 */
class AAPSPumpStateStore(
    private val sp: SP
) : PumpStateStore {
    sealed interface States {
        var btAddress: String

        var nonceString: String

        var cpCipherString: String
        var pcCipherString: String
        var keyResponseAddressInt: Int
        var pumpID: String
        var tbrTimestamp: Long
        var tbrPercentage: Int
        var tbrDuration: Int
        var tbrType: String
        var utcOffsetSeconds: Int

        fun copyFrom(states: States) {
            btAddress = states.btAddress

            nonceString = states.nonceString

            cpCipherString = states.cpCipherString
            pcCipherString = states.pcCipherString
            keyResponseAddressInt = states.keyResponseAddressInt
            pumpID = states.pumpID
            tbrTimestamp = states.tbrTimestamp
            tbrPercentage = states.tbrPercentage
            tbrDuration = states.tbrDuration
            tbrType = states.tbrType
            utcOffsetSeconds = states.utcOffsetSeconds
        }
    }

    class StatesBackup: States {
        override var btAddress: String = ""
        override var nonceString: String = ""
        override var cpCipherString: String = ""
        override var pcCipherString: String = ""
        override var keyResponseAddressInt: Int = 0
        override var pumpID: String = ""
        override var tbrTimestamp: Long = 0
        override var tbrPercentage: Int = 0
        override var tbrDuration: Int = 0
        override var tbrType: String = ""
        override var utcOffsetSeconds: Int = 0
    }

    class SPStates(val sp: SP): States {
        override var btAddress: String
            by SPDelegateString(sp, PreferenceKeys.BT_ADDRESS_KEY.str, "")

        // The nonce is updated with commit instead of apply to make sure
        // is atomically written to storage synchronously, minimizing
        // the likelihood that it could be lost due to app crashes etc.
        // It is very important to not lose the nonce, hence that choice.
        override var nonceString: String
            by SPDelegateString(sp, PreferenceKeys.NONCE_KEY.str, Nonce.nullNonce().toString(), commit = true)

        override var cpCipherString: String
            by SPDelegateString(sp, PreferenceKeys.CP_CIPHER_KEY.str, "")
        override var pcCipherString: String
            by SPDelegateString(sp, PreferenceKeys.PC_CIPHER_KEY.str, "")
        override var keyResponseAddressInt: Int
            by SPDelegateInt(sp, PreferenceKeys.KEY_RESPONSE_ADDRESS_KEY.str, 0)
        override var pumpID: String
            by SPDelegateString(sp, PreferenceKeys.PUMP_ID_KEY.str, "")
        override var tbrTimestamp: Long
            by SPDelegateLong(sp, PreferenceKeys.TBR_TIMESTAMP_KEY.str, 0)
        override var tbrPercentage: Int
            by SPDelegateInt(sp, PreferenceKeys.TBR_PERCENTAGE_KEY.str, 0)
        override var tbrDuration: Int
            by SPDelegateInt(sp, PreferenceKeys.TBR_DURATION_KEY.str, 0)
        override var tbrType: String
            by SPDelegateString(sp, PreferenceKeys.TBR_TYPE_KEY.str, "")
        override var utcOffsetSeconds: Int
            by SPDelegateInt(sp, PreferenceKeys.UTC_OFFSET_KEY.str, 0)
    }

    private var spStates = SPStates(sp)

    /**
     * Checks if there is a pump state present.
     *
     * This is an AndroidAPS specific addition to the standard [PumpStateStore.hasPumpState] call. Due to the
     * way AndroidAPS works, there is actually always at most only one pump state present. Also, there are
     * situations where the [info.nightscout.comboctl.main.PumpManager] is not available and the driver must
     * test if a pump state is present. To accomplish this, this function checks for that pump state. Unlike
     * [PumpStateStore.hasPumpState], it does not need a Bluetooth address as argument.
     */
    fun hasAnyPumpState() = sp.contains(PreferenceKeys.NONCE_KEY.str)

    /**
     * Copies all values from the [sp] instance into a local data structure and returns it as a backup.
     *
     * This is needed for when AndroidAPS imports a configuration. See the class documentation above for details.
     */
    fun createBackup() = if (sp.contains(PreferenceKeys.NONCE_KEY.str))
        StatesBackup().also { backup ->
            backup.copyFrom(spStates)
        }
    else
        null

    /**
     * Copies all values from a local data structure (which is the backup) into the [sp] instance.
     *
     * This is needed for when AndroidAPS imports a configuration. See the class documentation above for details.
     */
    fun applyBackup(backup: StatesBackup) {
        spStates.copyFrom(backup)
    }

    @OptIn(ExperimentalTime::class)
    override fun createPumpState(
        pumpAddress: BluetoothAddress,
        invariantPumpData: InvariantPumpData,
        utcOffset: UtcOffset,
        tbrState: CurrentTbrState
    ) {
        sp.edit(commit = true) {
            putString(PreferenceKeys.BT_ADDRESS_KEY.str, pumpAddress.toString().uppercase())
            putString(PreferenceKeys.CP_CIPHER_KEY.str, invariantPumpData.clientPumpCipher.toString())
            putString(PreferenceKeys.PC_CIPHER_KEY.str, invariantPumpData.pumpClientCipher.toString())
            putInt(PreferenceKeys.KEY_RESPONSE_ADDRESS_KEY.str, invariantPumpData.keyResponseAddress.toInt() and 0xFF)
            putString(PreferenceKeys.PUMP_ID_KEY.str, invariantPumpData.pumpID)
            putLong(PreferenceKeys.TBR_TIMESTAMP_KEY.str, if (tbrState is CurrentTbrState.TbrStarted) tbrState.tbr.timestamp.epochSeconds else -1)
            putInt(PreferenceKeys.TBR_PERCENTAGE_KEY.str, if (tbrState is CurrentTbrState.TbrStarted) tbrState.tbr.percentage else -1)
            putInt(PreferenceKeys.TBR_DURATION_KEY.str, if (tbrState is CurrentTbrState.TbrStarted) tbrState.tbr.durationInMinutes else -1)
            putString(PreferenceKeys.TBR_TYPE_KEY.str, if (tbrState is CurrentTbrState.TbrStarted) tbrState.tbr.type.stringId else "")
            putInt(PreferenceKeys.UTC_OFFSET_KEY.str, utcOffset.totalSeconds)
        }
    }

    override fun deletePumpState(pumpAddress: BluetoothAddress): Boolean {
        val hasState = sp.contains(PreferenceKeys.NONCE_KEY.str)

        sp.edit(commit = true) {
            for (keys in PreferenceKeys.entries)
                remove(keys.str)
        }

        return hasState
    }

    override fun hasPumpState(pumpAddress: BluetoothAddress): Boolean =
        sp.contains(PreferenceKeys.NONCE_KEY.str)

    override fun getAvailablePumpStateAddresses(): Set<BluetoothAddress> =
        if (spStates.btAddress.isBlank()) setOf() else setOf(spStates.btAddress.toBluetoothAddress())

    override fun getInvariantPumpData(pumpAddress: BluetoothAddress) = InvariantPumpData(
        clientPumpCipher = spStates.cpCipherString.toCipher(),
        pumpClientCipher = spStates.pcCipherString.toCipher(),
        keyResponseAddress = spStates.keyResponseAddressInt.toByte(),
        pumpID = spStates.pumpID
    )

    override fun getCurrentTxNonce(pumpAddress: BluetoothAddress) = spStates.nonceString.toNonce()

    override fun setCurrentTxNonce(pumpAddress: BluetoothAddress, currentTxNonce: Nonce) {
        spStates.nonceString = currentTxNonce.toString()
    }

    override fun getCurrentUtcOffset(pumpAddress: BluetoothAddress) =
        UtcOffset(seconds = spStates.utcOffsetSeconds)

    override fun setCurrentUtcOffset(pumpAddress: BluetoothAddress, utcOffset: UtcOffset) {
        spStates.utcOffsetSeconds = utcOffset.totalSeconds
    }

    @OptIn(ExperimentalTime::class)
    override fun getCurrentTbrState(pumpAddress: BluetoothAddress) =
        if (spStates.tbrTimestamp >= 0)
            CurrentTbrState.TbrStarted(
                Tbr(
                    timestamp = Instant.fromEpochSeconds(spStates.tbrTimestamp),
                    percentage = spStates.tbrPercentage,
                    durationInMinutes = spStates.tbrDuration,
                    type = Tbr.Type.fromStringId(spStates.tbrType) ?: throw PumpStateStoreAccessException(pumpAddress, "Invalid type \"$spStates.tbrType\"")
                )
            )
        else
            CurrentTbrState.NoTbrOngoing

    @OptIn(ExperimentalTime::class)
    override fun setCurrentTbrState(pumpAddress: BluetoothAddress, currentTbrState: CurrentTbrState) {
        when (currentTbrState) {
            is CurrentTbrState.TbrStarted -> {
                spStates.tbrTimestamp = currentTbrState.tbr.timestamp.epochSeconds
                spStates.tbrPercentage = currentTbrState.tbr.percentage
                spStates.tbrDuration = currentTbrState.tbr.durationInMinutes
                spStates.tbrType = currentTbrState.tbr.type.stringId
            }

            else                          -> {
                spStates.tbrTimestamp = -1
                spStates.tbrPercentage = -1
                spStates.tbrDuration = -1
                spStates.tbrType = ""
            }
        }
    }

    /***
     * By @MilosKozak
     * If changed see [ComboV2Plugin.ComboStringKey], [ComboV2Plugin.ComboIntKey], [ComboV2Plugin.ComboLongKey]
     *
     */

    private enum class PreferenceKeys(val str: String, val type: KClassifier) {
        BT_ADDRESS_KEY("combov2-bt-address-key", String::class),
        NONCE_KEY("combov2-nonce-key", String::class),
        CP_CIPHER_KEY("combov2-cp-cipher-key", String::class),
        PC_CIPHER_KEY("combov2-pc-cipher-key", String::class),
        KEY_RESPONSE_ADDRESS_KEY("combov2-key-response-address-key", Int::class),
        PUMP_ID_KEY("combov2-pump-id-key", String::class),
        TBR_TIMESTAMP_KEY("combov2-tbr-timestamp", Long::class),
        TBR_PERCENTAGE_KEY("combov2-tbr-percentage", Int::class),
        TBR_DURATION_KEY("combov2-tbr-duration", Int::class),
        TBR_TYPE_KEY("combov2-tbr-type", String::class),
        UTC_OFFSET_KEY("combov2-utc-offset", Int::class);

        override fun toString(): String = str
    }
}