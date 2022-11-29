package info.nightscout.comboctl.base.testUtils

import info.nightscout.comboctl.base.BluetoothAddress
import info.nightscout.comboctl.base.CurrentTbrState
import info.nightscout.comboctl.base.InvariantPumpData
import info.nightscout.comboctl.base.NUM_NONCE_BYTES
import info.nightscout.comboctl.base.Nonce
import info.nightscout.comboctl.base.PumpStateAlreadyExistsException
import info.nightscout.comboctl.base.PumpStateDoesNotExistException
import info.nightscout.comboctl.base.PumpStateStore
import kotlinx.datetime.UtcOffset

class TestPumpStateStore : PumpStateStore {
    data class Entry(
        val invariantPumpData: InvariantPumpData,
        var currentTxNonce: Nonce,
        var currentUtcOffset: UtcOffset,
        var currentTbrState: CurrentTbrState
    )

    var states = mutableMapOf<BluetoothAddress, Entry>()
        private set

    override fun createPumpState(
        pumpAddress: BluetoothAddress,
        invariantPumpData: InvariantPumpData,
        utcOffset: UtcOffset,
        tbrState: CurrentTbrState
    ) {
        if (states.contains(pumpAddress))
            throw PumpStateAlreadyExistsException(pumpAddress)

        states[pumpAddress] = Entry(invariantPumpData, Nonce(List(NUM_NONCE_BYTES) { 0x00 }), utcOffset, tbrState)
    }

    override fun deletePumpState(pumpAddress: BluetoothAddress) =
        if (states.contains(pumpAddress)) {
            states.remove(pumpAddress)
            true
        } else {
            false
        }

    override fun hasPumpState(pumpAddress: BluetoothAddress): Boolean =
        states.contains(pumpAddress)

    override fun getAvailablePumpStateAddresses(): Set<BluetoothAddress> = states.keys

    override fun getInvariantPumpData(pumpAddress: BluetoothAddress): InvariantPumpData {
        if (!states.contains(pumpAddress))
            throw PumpStateDoesNotExistException(pumpAddress)
        return states[pumpAddress]!!.invariantPumpData
    }

    override fun getCurrentTxNonce(pumpAddress: BluetoothAddress): Nonce {
        if (!states.contains(pumpAddress))
            throw PumpStateDoesNotExistException(pumpAddress)
        return states[pumpAddress]!!.currentTxNonce
    }

    override fun setCurrentTxNonce(pumpAddress: BluetoothAddress, currentTxNonce: Nonce) {
        if (!states.contains(pumpAddress))
            throw PumpStateDoesNotExistException(pumpAddress)
        states[pumpAddress]!!.currentTxNonce = currentTxNonce
    }

    override fun getCurrentUtcOffset(pumpAddress: BluetoothAddress): UtcOffset {
        if (!states.contains(pumpAddress))
            throw PumpStateDoesNotExistException(pumpAddress)
        return states[pumpAddress]!!.currentUtcOffset
    }

    override fun setCurrentUtcOffset(pumpAddress: BluetoothAddress, utcOffset: UtcOffset) {
        if (!states.contains(pumpAddress))
            throw PumpStateDoesNotExistException(pumpAddress)
        states[pumpAddress]!!.currentUtcOffset = utcOffset
    }

    override fun getCurrentTbrState(pumpAddress: BluetoothAddress): CurrentTbrState {
        if (!states.contains(pumpAddress))
            throw PumpStateDoesNotExistException(pumpAddress)
        return states[pumpAddress]!!.currentTbrState
    }

    override fun setCurrentTbrState(pumpAddress: BluetoothAddress, currentTbrState: CurrentTbrState) {
        if (!states.contains(pumpAddress))
            throw PumpStateDoesNotExistException(pumpAddress)
        states[pumpAddress]!!.currentTbrState = currentTbrState
    }
}
