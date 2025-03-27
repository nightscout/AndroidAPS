package info.nightscout.comboctl.base

import kotlinx.datetime.UtcOffset

/**
 * Pump related data that is set during pairing and not changed afterwards.
 *
 * This data is created by [PumpIO.performPairing]. Once it is
 * created, it does not change until the pump is unpaired, at
 * which point it is erased. This data is managed by the
 * [PumpStateStore] class, which stores / retrieves it.
 *
 * @property clientPumpCipher This cipher is used for authenticating
 *           packets going to the Combo.
 * @property pumpClientCipher This cipher is used for verifying
 *           packets coming from the Combo.
 * @property keyResponseAddress The address byte of a previously
 *           received KEY_RESPONSE packet. The source and destination
 *           address values inside this address byte must have been
 *           reordered to match the order that outgoing packets expect.
 *           That is: Source address stored in the upper, destination
 *           address in the lower 4 bit of the byte. (In incoming
 *           packets - and KEY_RESPONSE is an incoming packet - these
 *           two are ordered the other way round.)
 * @property pumpID The pump ID from the ID_RESPONSE packet.
 *           This is useful for displaying the pump in a UI, since the
 *           Bluetooth address itself may not be very clear to the user.
 */
data class InvariantPumpData(
    val clientPumpCipher: Cipher,
    val pumpClientCipher: Cipher,
    val keyResponseAddress: Byte,
    val pumpID: String
) {
    companion object {
        /**
         * Convenience function to create an instance with default "null" values.
         *
         * Useful for an initial state.
         */
        fun nullData() =
            InvariantPumpData(
                clientPumpCipher = Cipher(ByteArray(CIPHER_KEY_SIZE)),
                pumpClientCipher = Cipher(ByteArray(CIPHER_KEY_SIZE)),
                keyResponseAddress = 0x00.toByte(),
                pumpID = ""
            )
    }
}

/**
 * The current state of an ongoing TBR (if any).
 *
 * Due to limitations of the Combo, it is necessary to remember this
 * current state in case the client crashes and restarts, otherwise
 * it is not possible to deduce the starting timestamp (incl. the
 * UTC offset at the time of the TBR start) and whether or not a
 * previously started TBR finished in the meantime. To work around
 * this limitation, we store that information in the [PumpStateStore]
 * in a persistent fashion.
 */
sealed class CurrentTbrState {
    /**
     * No TBR is currently ongoing. If the main screen shows TBR info
     * while this is the current TBR state it means that an unknown
     * TBR was started, for example by the user while the client
     * application that uses comboctl was down.
     */
    object NoTbrOngoing : CurrentTbrState()

    /**
     * TBR is currently ongoing. This stores a [Tbr] instance with
     * its timestamp specifying when the TBR started. If the main
     * screen shows no TBR info while this is the current TBR state,
     * it means that the TBR ended already.
     */
    data class TbrStarted(val tbr: Tbr) : CurrentTbrState()
}

/**
 * Exception thrown when accessing the stored state of a specific pump fails.
 *
 * @param pumpAddress Bluetooth address of the pump whose
 *        state could not be accessed or created.
 * @param message The detail message.
 * @param cause The exception that was thrown in the loop specifying
 *        want went wrong there.
 */
class PumpStateStoreAccessException(val pumpAddress: BluetoothAddress, message: String?, cause: Throwable?) :
    ComboException(message, cause) {
    constructor(pumpAddress: BluetoothAddress, message: String) : this(pumpAddress, message, null)
    constructor(pumpAddress: BluetoothAddress, cause: Throwable) : this(pumpAddress, null, cause)
}

/**
 * Exception thrown when trying to create a new pump state even though one already exists.
 *
 * @param pumpAddress Bluetooth address of the pump.
 */
class PumpStateAlreadyExistsException(val pumpAddress: BluetoothAddress) :
    ComboException("Pump state for pump with address $pumpAddress already exists")

/**
 * Exception thrown when trying to access new pump state that does not exist.
 *
 * @param pumpAddress Bluetooth address of the pump.
 */
class PumpStateDoesNotExistException(val pumpAddress: BluetoothAddress) :
    ComboException("Pump state for pump with address $pumpAddress does not exist")

/**
 * State store interface for a specific pump.
 *
 * This interface provides access to a store that persistently
 * records the data of [InvariantPumpData] instances along with
 * the current Tx nonce, UTC offset, and TBR state.
 *
 * As the name suggests, these states are recorded persistently,
 * immediately, and ideally also atomically. If atomic storage cannot
 * be guaranteed, then there must be some sort of data error detection
 * in place to ensure that no corrupted data is retrieved. (For example,
 * if the device running ComboCtl crashes or freezes while the data is
 * being written into the store, the data may not be written completely.)
 *
 * There is one state for each paired pump. Each instance contains the
 * [InvariantPumpData], which does not change after the pump was paired,
 * the Tx nonce, which does change after each packet that is sent to
 * the Combo, the UTC offset, which usually does not change often, and
 * the TBR state, which changes whenever a TBR starts/ends. These parts
 * of a pump's state are kept separate due to the difference in access
 * patterns (that is, how often they are updated), since this allows for
 * optimizations in implementations.
 *
 * Each state is associate with a pump via the pump's Bluetooth address.
 *
 * If a function or property access throws [PumpStateStoreAccessException],
 * then the state is to be considered invalid, any existing connections
 * to a pump associated with the state must be terminated, and the pump must
 * be unpaired. This is because such an exception indicates an error in
 * the underlying pump state store implementation that said implementation
 * could not recover from. And this also implies that this pump's state inside
 * the store is in an undefined state - it cannot be relied upon anymore.
 * Internally, the implementation must delete any remaining state data when
 * such an error occurs. Callers must then also unpair the pump at the Bluetooth
 * level. The user must be told about this error, and instructed that the pump
 * must be paired again.
 *
 * Different pump states can be accessed, created, deleted concurrently.
 * However, operations on the same state must not happen concurrently.
 * For example, it is valid to create a pump state while an existing [PumpIO]
 * instance updates the Tx nonce of its associated state, but no two threads
 * may update the Tx nonce at the same time, or try to access state data and
 * delete the same state simultaneously, or access a pump state's Tx nonce
 * while another thread writes a new UTC offset into the same pump state.
 *
 * The UTC offset that is stored for each pump here exists because the Combo
 * is unaware of timezones or UTC offsets. All the time data it stores is
 * in localtime. The UTC offset in this store specifies what UTC offset
 * to associate any current Combo localtime timestamp with. Particularly
 * for the bolus history this is very important, since it allows for properly
 * handling daylight savings changes and timezone changes (for example because
 * the user is on vacation in another timezone). The stored UTC offset is
 * also necessary to be able to detect UTC offset changes even if they
 * happen when the client is not running. The overall rule with regard
 * to UTC offset changes and stored Combo localtime timestamps is that
 * all currently stored timestamps use the currently stored UTC offset,
 * and any timestamps that might be stored later on will use the new
 * UTC offset. In practice, this means that all timestamps from the Combo's
 * command mode history delta use the current UTC offset, and after the
 * delta was fetched, the UTC offset is updated.
 *
 * Finally, the stored TBR state exists because of limitations in the Combo
 * regarding ongoing TBR information. See [CurrentTbrState] for details.
 */
interface PumpStateStore {
    /**
     * Creates a new pump state and fills the state's invariant data.
     *
     * This is called during the pairing process. In regular
     * connections, this is not used. It initializes a state for the pump
     * with the given ID in the store. Before this call, trying to access
     * the state with [getInvariantPumpData], [getCurrentTxNonce],
     * [setCurrentTxNonce], [getCurrentUtcOffset], [getCurrentTbrState]
     * fails with an exception. The new state's nonce is set to a null
     * nonce (= all of its bytes set to zero). The UTC offset is set to
     * the one from the current system timezone and system time. The
     * TBR state is set to [CurrentTbrState.NoTbrOngoing].
     *
     * The state is removed by calling [deletePumpState].
     *
     * Subclasses must store the invariant pump data immediately and persistently.
     *
     * @param pumpAddress Bluetooth address of the pump to create a state for.
     * @param invariantPumpData Invariant pump data to use in the new state.
     * @param utcOffset Initial UTC offset value to use in the new state.
     * @param tbrState Initial TBR state to use in the new state.
     * @throws PumpStateAlreadyExistsException if there is already a state
     *         with the given Bluetooth address.
     * @throws PumpStateStoreAccessException if writing the new state fails
     *         due to an error that occurred in the underlying implementation.
     */
    fun createPumpState(
        pumpAddress: BluetoothAddress,
        invariantPumpData: InvariantPumpData,
        utcOffset: UtcOffset,
        tbrState: CurrentTbrState
    )

    /**
     * Deletes a pump state that is associated with the given address.
     *
     * If there is no such state, this returns false.
     *
     * NOTE: This does not throw.
     *
     * @param pumpAddress Bluetooth address of the pump whose corresponding
     *        state in the store shall be deleted.
     * @return true if there was such a state, false otherwise.
     */
    fun deletePumpState(pumpAddress: BluetoothAddress): Boolean

    /**
     * Checks if there is a valid state associated with the given address.
     *
     * @return true if there is one, false otherwise.
     */
    fun hasPumpState(pumpAddress: BluetoothAddress): Boolean

    /**
     * Returns a set of Bluetooth addresses of the states in this store.
     */
    fun getAvailablePumpStateAddresses(): Set<BluetoothAddress>

    /**
     * Returns the [InvariantPumpData] from the state associated with the given address.
     *
     * @throws PumpStateDoesNotExistException if no pump state associated with
     *         the given address exists in the store.
     * @throws PumpStateStoreAccessException if accessing the data fails
     *         due to an error that occurred in the underlying implementation.
     */
    fun getInvariantPumpData(pumpAddress: BluetoothAddress): InvariantPumpData

    /**
     * Returns the current Tx [Nonce] from the state associated with the given address.
     *
     * @throws PumpStateDoesNotExistException if no pump state associated with
     *         the given address exists in the store.
     * @throws PumpStateStoreAccessException if accessing the nonce fails
     *         due to an error that occurred in the underlying implementation.
     */
    fun getCurrentTxNonce(pumpAddress: BluetoothAddress): Nonce

    /**
     * Sets the current Tx [Nonce] in the state associated with the given address.
     *
     * Subclasses must store the new Tx nonce immediately and persistently.
     *
     * @throws PumpStateDoesNotExistException if no pump state associated with
     *         the given address exists in the store.
     * @throws PumpStateStoreAccessException if accessing the nonce fails
     *         due to an error that occurred in the underlying implementation.
     */
    fun setCurrentTxNonce(pumpAddress: BluetoothAddress, currentTxNonce: Nonce)

    /**
     * Returns the current UTC offset that is to be used for all timestamps from now on.
     *
     * See the [PumpStateStore] documentation for details about this offset.
     *
     * @throws PumpStateDoesNotExistException if no pump state associated with
     *         the given address exists in the store.
     * @throws PumpStateStoreAccessException if accessing the nonce fails
     *         due to an error that occurred in the underlying implementation.
     */
    fun getCurrentUtcOffset(pumpAddress: BluetoothAddress): UtcOffset

    /**
     * Sets the current UTC offset that is to be used for all timestamps from now on.
     *
     * See the [PumpStateStore] documentation for details about this offset.
     *
     * @throws PumpStateDoesNotExistException if no pump state associated with
     *         the given address exists in the store.
     * @throws PumpStateStoreAccessException if accessing the nonce fails
     *         due to an error that occurred in the underlying implementation.
     */
    fun setCurrentUtcOffset(pumpAddress: BluetoothAddress, utcOffset: UtcOffset)

    /**
     * Returns the TBR state that is currently known for the pump with the given [pumpAddress].
     *
     * See the [CurrentTbrState] documentation for details about this offset.
     *
     * @throws PumpStateDoesNotExistException if no pump state associated with
     *         the given address exists in the store.
     * @throws PumpStateStoreAccessException if accessing the nonce fails
     *         due to an error that occurred in the underlying implementation.
     */
    fun getCurrentTbrState(pumpAddress: BluetoothAddress): CurrentTbrState

    /**
     * Sets the TBR state that is currently known for the pump with the given [pumpAddress].
     *
     * See the [CurrentTbrState] documentation for details about this offset.
     *
     * @throws PumpStateDoesNotExistException if no pump state associated with
     *         the given address exists in the store.
     * @throws PumpStateStoreAccessException if accessing the nonce fails
     *         due to an error that occurred in the underlying implementation.
     */
    fun setCurrentTbrState(pumpAddress: BluetoothAddress, currentTbrState: CurrentTbrState)
}

/**
 * Increments the nonce of a pump state associated with the given address.
 *
 * @param pumpAddress Bluetooth address of the pump state.
 * @param incrementAmount By how much the nonce is to be incremented.
 *   Must be at least 1.
 */
fun PumpStateStore.incrementTxNonce(pumpAddress: BluetoothAddress, incrementAmount: Int = 1): Nonce {
    require(incrementAmount >= 1)

    val currentTxNonce = this.getCurrentTxNonce(pumpAddress)
    val newTxNonce = currentTxNonce.getIncrementedNonce(incrementAmount)
    this.setCurrentTxNonce(pumpAddress, newTxNonce)
    return newTxNonce
}
