package info.nightscout.pump.combov2

import info.nightscout.comboctl.base.BluetoothAddress
import info.nightscout.comboctl.base.CurrentTbrState
import info.nightscout.comboctl.base.InvariantPumpData
import info.nightscout.comboctl.base.Nonce
import info.nightscout.comboctl.base.PumpStateStore
import info.nightscout.comboctl.base.Tbr
import info.nightscout.comboctl.base.toBluetoothAddress
import info.nightscout.comboctl.base.toCipher
import info.nightscout.comboctl.base.toNonce
import info.nightscout.shared.sharedPreferences.SP
import info.nightscout.shared.sharedPreferences.SPDelegateInt
import info.nightscout.shared.sharedPreferences.SPDelegateLong
import info.nightscout.shared.sharedPreferences.SPDelegateString
import kotlinx.datetime.Instant
import kotlinx.datetime.UtcOffset

/**
 * AndroidAPS [SP] based pump state store.
 *
 * This store is set up to contain a single paired pump. AndroidAPS is not
 * designed to handle multiple pumps, so this simplification makes sense.
 * This affects all accessors, which
 */
class SPPumpStateStore(private val sp: SP) : PumpStateStore {
    private var btAddress: String
        by SPDelegateString(sp, BT_ADDRESS_KEY, "")

    // The nonce is updated with commit instead of apply to make sure
    // is atomically written to storage synchronously, minimizing
    // the likelihood that it could be lost due to app crashes etc.
    // It is very important to not lose the nonce, hence that choice.
    private var nonceString: String
        by SPDelegateString(sp, NONCE_KEY, Nonce.nullNonce().toString(), commit = true)

    private var cpCipherString: String
        by SPDelegateString(sp, CP_CIPHER_KEY, "")
    private var pcCipherString: String
        by SPDelegateString(sp, PC_CIPHER_KEY, "")
    private var keyResponseAddressInt: Int
        by SPDelegateInt(sp, KEY_RESPONSE_ADDRESS_KEY, 0)
    private var pumpID: String
        by SPDelegateString(sp, PUMP_ID_KEY, "")
    private var tbrTimestamp: Long
        by SPDelegateLong(sp, TBR_TIMESTAMP_KEY, 0)
    private var tbrPercentage: Int
        by SPDelegateInt(sp, TBR_PERCENTAGE_KEY, 0)
    private var tbrDuration: Int
        by SPDelegateInt(sp, TBR_DURATION_KEY, 0)
    private var tbrType: String
        by SPDelegateString(sp, TBR_TYPE_KEY, "")
    private var utcOffsetSeconds: Int
        by SPDelegateInt(sp, UTC_OFFSET_KEY, 0)

    override fun createPumpState(
        pumpAddress: BluetoothAddress,
        invariantPumpData: InvariantPumpData,
        utcOffset: UtcOffset,
        tbrState: CurrentTbrState
    ) {
        // Write these values via edit() instead of using the delegates
        // above to be able to write all of them with a single commit.
        sp.edit(commit = true) {
            putString(BT_ADDRESS_KEY, pumpAddress.toString().uppercase())
            putString(CP_CIPHER_KEY, invariantPumpData.clientPumpCipher.toString())
            putString(PC_CIPHER_KEY, invariantPumpData.pumpClientCipher.toString())
            putInt(KEY_RESPONSE_ADDRESS_KEY, invariantPumpData.keyResponseAddress.toInt() and 0xFF)
            putString(PUMP_ID_KEY, invariantPumpData.pumpID)
            putLong(TBR_TIMESTAMP_KEY, if (tbrState is CurrentTbrState.TbrStarted) tbrState.tbr.timestamp.epochSeconds else -1)
            putInt(TBR_PERCENTAGE_KEY, if (tbrState is CurrentTbrState.TbrStarted) tbrState.tbr.percentage else -1)
            putInt(TBR_DURATION_KEY, if (tbrState is CurrentTbrState.TbrStarted) tbrState.tbr.durationInMinutes else -1)
            putString(TBR_TYPE_KEY, if (tbrState is CurrentTbrState.TbrStarted) tbrState.tbr.type.stringId else "")
            putInt(UTC_OFFSET_KEY, utcOffset.totalSeconds)
        }
    }

    override fun deletePumpState(pumpAddress: BluetoothAddress): Boolean {
        val hasState = sp.contains(NONCE_KEY)

        sp.edit(commit = true) {
            remove(BT_ADDRESS_KEY)
            remove(NONCE_KEY)
            remove(CP_CIPHER_KEY)
            remove(PC_CIPHER_KEY)
            remove(KEY_RESPONSE_ADDRESS_KEY)
            remove(TBR_TIMESTAMP_KEY)
            remove(TBR_PERCENTAGE_KEY)
            remove(TBR_DURATION_KEY)
            remove(TBR_TYPE_KEY)
            remove(UTC_OFFSET_KEY)
        }

        return hasState
    }

    override fun hasPumpState(pumpAddress: BluetoothAddress) =
        sp.contains(NONCE_KEY)

    override fun getAvailablePumpStateAddresses() =
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
            CurrentTbrState.TbrStarted(Tbr(
                timestamp = Instant.fromEpochSeconds(tbrTimestamp),
                percentage = tbrPercentage,
                durationInMinutes = tbrDuration,
                type = Tbr.Type.fromStringId(tbrType)!!
            ))
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
            else -> {
                tbrTimestamp = -1
                tbrPercentage = -1
                tbrDuration = -1
                tbrType = ""
            }
        }
    }

    companion object {
        const val BT_ADDRESS_KEY = "combov2-bt-address-key"
        const val NONCE_KEY = "combov2-nonce-key"
        const val CP_CIPHER_KEY = "combov2-cp-cipher-key"
        const val PC_CIPHER_KEY = "combov2-pc-cipher-key"
        const val KEY_RESPONSE_ADDRESS_KEY = "combov2-key-response-address-key"
        const val PUMP_ID_KEY = "combov2-pump-id-key"
        const val TBR_TIMESTAMP_KEY = "combov2-tbr-timestamp"
        const val TBR_PERCENTAGE_KEY = "combov2-tbr-percentage"
        const val TBR_DURATION_KEY = "combov2-tbr-duration"
        const val TBR_TYPE_KEY = "combov2-tbr-type"
        const val UTC_OFFSET_KEY = "combov2-utc-offset"
    }
}
