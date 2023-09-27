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
import kotlinx.datetime.Instant
import kotlinx.datetime.UtcOffset
import kotlin.reflect.KClassifier

/**
 * Special pump state store that mainly uses the internalSP, but also is able to sync up the AAPS main SP with that internal SP.
 *
 * This pump state store solves a problem: What if the user already paired AAPS with a Combo, and then imports and old AAPS
 * settings file, intending to just restore other settings like the basal profile, but does _not_ intend to import an old
 * pump state? After all, if we write a pump state to the AAPS main SP, the state values will also be written into a settings
 * file when exporting said settings. If we just relied on the main AAPS SP, the current pump state - including pairing info
 * like the PC and CP keys - would be overwritten with the old ones from the imported settings, and the user would have to
 * unnecessarily re-pair the Combo every time settings get imported.
 *
 * The solution is for this driver to _not_ primarily use the AAPS main SP. Instead, it uses its own internal SP. That SP
 * is exclusively used by this driver. But, a copy of the pump state values are stored in the AAPS main SP, and kept in
 * sync with the contents of the internal SP.
 *
 * When a new pump state is created, the invariant values (CP/PC keys etc.) are written in both the AAPS main SP and the
 * internal SP, along with the initial nonce. During operation, the driver will update the nonce value of the internal SP.
 * If TBR states are changed, then these changes are stored in the internal SP. Callers can use [copyVariantValuesToAAPSMainSP]
 * to update the corresponding values in the AAPS main SP to keep the two SPs in sync.
 *
 * There are a couple of special situations:
 *
 * 1. There is no pump state in the internal SP, and there is no pump state in the AAPS main SP. This is the unpaired state.
 * 2. There is no pump state in the internal SP, but there is one in the AAPS main SP. This typically happens when the
 *   user (re)installed AAPS, and immediately after installing, imported AAPS settings. Callers are then supposed to call
 *   [copyAllValuesFromAAPSMainSP] to import the pump state from the AAPS main SP over to the internal SP and continue to
 *   work with that state.
 * 3. There is a pump state, and there is also one in the AAPS main SP. The latter one is then ignored. A pump state in the
 *   AAPS main SP solely and only exists to be able to export/import pump states. It is not used for actual pump operations.
 *   In particular, if - as mentioned above - a pump is already paired, and the user imports settings, this logic prevents
 *   the current pump state to be overwritten.
 */
class AAPSPumpStateStore(
    private val aapsMainSP: SP,
    private val internalSP: InternalSP
) : PumpStateStore {

    private var btAddress: String
        by SPDelegateString(internalSP, PreferenceKeys.BT_ADDRESS_KEY.str, "")

    // The nonce is updated with commit instead of apply to make sure
    // is atomically written to storage synchronously, minimizing
    // the likelihood that it could be lost due to app crashes etc.
    // It is very important to not lose the nonce, hence that choice.
    private var nonceString: String
        by SPDelegateString(internalSP, PreferenceKeys.NONCE_KEY.str, Nonce.nullNonce().toString(), commit = true)

    private var cpCipherString: String
        by SPDelegateString(internalSP, PreferenceKeys.CP_CIPHER_KEY.str, "")
    private var pcCipherString: String
        by SPDelegateString(internalSP, PreferenceKeys.PC_CIPHER_KEY.str, "")
    private var keyResponseAddressInt: Int
        by SPDelegateInt(internalSP, PreferenceKeys.KEY_RESPONSE_ADDRESS_KEY.str, 0)
    private var pumpID: String
        by SPDelegateString(internalSP, PreferenceKeys.PUMP_ID_KEY.str, "")
    private var tbrTimestamp: Long
        by SPDelegateLong(internalSP, PreferenceKeys.TBR_TIMESTAMP_KEY.str, 0)
    private var tbrPercentage: Int
        by SPDelegateInt(internalSP, PreferenceKeys.TBR_PERCENTAGE_KEY.str, 0)
    private var tbrDuration: Int
        by SPDelegateInt(internalSP, PreferenceKeys.TBR_DURATION_KEY.str, 0)
    private var tbrType: String
        by SPDelegateString(internalSP, PreferenceKeys.TBR_TYPE_KEY.str, "")
    private var utcOffsetSeconds: Int
        by SPDelegateInt(internalSP, PreferenceKeys.UTC_OFFSET_KEY.str, 0)

    override fun createPumpState(
        pumpAddress: BluetoothAddress,
        invariantPumpData: InvariantPumpData,
        utcOffset: UtcOffset,
        tbrState: CurrentTbrState
    ) {
        internalSP.edit(commit = true) {
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

        copyAllValuesToAAPSMainSP(commit = true)
    }

    override fun deletePumpState(pumpAddress: BluetoothAddress): Boolean {
        val hasState = internalSP.contains(PreferenceKeys.NONCE_KEY.str)

        internalSP.edit(commit = true) {
            for (keys in PreferenceKeys.values())
                remove(keys.str)
        }

        aapsMainSP.edit(commit = true) {
            for (keys in PreferenceKeys.values())
                remove(keys.str)
        }

        return hasState
    }

    override fun hasPumpState(pumpAddress: BluetoothAddress): Boolean =
        internalSP.contains(PreferenceKeys.NONCE_KEY.str)

    override fun getAvailablePumpStateAddresses(): Set<BluetoothAddress> =
        if (btAddress.isBlank()) setOf() else setOf(btAddress.toBluetoothAddress())

    override fun getInvariantPumpData(pumpAddress: BluetoothAddress) = InvariantPumpData(
        clientPumpCipher = cpCipherString.toCipher(),
        pumpClientCipher = pcCipherString.toCipher(),
        keyResponseAddress = keyResponseAddressInt.toByte(),
        pumpID = pumpID
    )

    override fun getCurrentTxNonce(pumpAddress: BluetoothAddress) = nonceString.toNonce()

    override fun setCurrentTxNonce(pumpAddress: BluetoothAddress, currentTxNonce: Nonce) {
        nonceString = currentTxNonce.toString()
    }

    override fun getCurrentUtcOffset(pumpAddress: BluetoothAddress) =
        UtcOffset(seconds = utcOffsetSeconds)

    override fun setCurrentUtcOffset(pumpAddress: BluetoothAddress, utcOffset: UtcOffset) {
        utcOffsetSeconds = utcOffset.totalSeconds
    }

    override fun getCurrentTbrState(pumpAddress: BluetoothAddress) =
        if (tbrTimestamp >= 0)
            CurrentTbrState.TbrStarted(
                Tbr(
                    timestamp = Instant.fromEpochSeconds(tbrTimestamp),
                    percentage = tbrPercentage,
                    durationInMinutes = tbrDuration,
                    type = Tbr.Type.fromStringId(tbrType) ?: throw PumpStateStoreAccessException(pumpAddress, "Invalid type \"$tbrType\"")
                )
            )
        else
            CurrentTbrState.NoTbrOngoing

    override fun setCurrentTbrState(pumpAddress: BluetoothAddress, currentTbrState: CurrentTbrState) {
        when (currentTbrState) {
            is CurrentTbrState.TbrStarted -> {
                tbrTimestamp = currentTbrState.tbr.timestamp.epochSeconds
                tbrPercentage = currentTbrState.tbr.percentage
                tbrDuration = currentTbrState.tbr.durationInMinutes
                tbrType = currentTbrState.tbr.type.stringId
            }

            else                          -> {
                tbrTimestamp = -1
                tbrPercentage = -1
                tbrDuration = -1
                tbrType = ""
            }
        }
    }

    // Copies only those pump state values from the internal SP to the AAPS main SP which can vary during
    // pump operations. These are the TBR values, the UTC offset, and the nonce. Users are recommended to
    // call this after AAPS disconnects the pump.
    fun copyVariantValuesToAAPSMainSP(commit: Boolean) =
        copyValuesBetweenSPs(
            commit, from = internalSP, to = aapsMainSP, arrayOf(
                PreferenceKeys.NONCE_KEY,
                PreferenceKeys.TBR_TIMESTAMP_KEY,
                PreferenceKeys.TBR_PERCENTAGE_KEY,
                PreferenceKeys.TBR_DURATION_KEY,
                PreferenceKeys.TBR_TYPE_KEY,
                PreferenceKeys.UTC_OFFSET_KEY
            )
        )

    // Copies all pump state values from the AAPS main SP to the internal SP. This is supposed to be
    // called if the internal SP is empty. That way, a pump state can be imported from AAPS settings files.
    fun copyAllValuesFromAAPSMainSP(commit: Boolean) =
        copyValuesBetweenSPs(commit, from = aapsMainSP, to = internalSP, keys = PreferenceKeys.values())

    // Copies all pump state values from the internal SP to the AAPS main SP to. The createPumpState()
    // function calls this after creating the pump state to ensure both SPs are in sync. Also, this
    // should be called when the driver starts in case AAPS settings are imported and there is already
    // a pump state present in the internal SP. Calling this then ensures that the pump state in the
    // main SP is fully synced up with the one from the internal SP, and does not contain some old
    // state that is not in use anymore.
    fun copyAllValuesToAAPSMainSP(commit: Boolean) =
        copyValuesBetweenSPs(commit, from = internalSP, to = aapsMainSP, keys = PreferenceKeys.values())

    private fun copyValuesBetweenSPs(commit: Boolean, from: SP, to: SP, keys: Array<PreferenceKeys>) {
        to.edit(commit) {
            for (key in keys) {
                if (!from.contains(key.str))
                    continue
                when (key.type) {
                    Int::class    -> putInt(key.str, from.getInt(key.str, 0))
                    Long::class   -> putLong(key.str, from.getLong(key.str, 0L))
                    String::class -> putString(key.str, from.getString(key.str, ""))
                }
            }
        }
    }

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