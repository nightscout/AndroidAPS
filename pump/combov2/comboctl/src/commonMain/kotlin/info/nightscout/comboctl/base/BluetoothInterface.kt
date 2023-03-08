package info.nightscout.comboctl.base

/**
 * Simple high-level interface to the system's Bluetooth stack.
 *
 * This interface offers the bare minimum to accomplish the following tasks:
 *
 * 1. Discover and pair Bluetooth devices with the given pairing PIN.
 *    (An SDP service is temporarily set up during discovery.)
 * 2. Connect to a Bluetooth device and enable RFCOMM-based blocking IO with it.
 *
 * The constructor must set up all necessary platform specific resources.
 */
interface BluetoothInterface {
    /**
     * Possible reasons for why discovery stopped.
     *
     * Used in the [startDiscovery] discoveryStopped callback.
     */
    enum class DiscoveryStoppedReason(val str: String) {
        MANUALLY_STOPPED("manually stopped"),
        DISCOVERY_ERROR("error during discovery"),
        DISCOVERY_TIMEOUT("discovery timeout reached")
    }

    /**
     * Callback for when a previously paired device is unpaired.
     *
     * This is independent of the device discovery. That is, this callback
     * can be invoked by the implementation even when discovery is inactive.
     *
     * The unpairing may have been done via [BluetoothDevice.unpair] or
     * via some sort of system settings.
     *
     * Note that this callback may be called from another thread. Using
     * synchronization primitives to avoid race conditions is recommended.
     * Also, implementations must make sure that setting the callback
     * can not cause data races; that is, it must not happen that a new
     * callback is set while the existing callback is invoked due to an
     * unpaired device.
     *
     * Do not spend too much time in this callback, since it may block
     * internal threads.
     *
     * Exceptions thrown by this callback are logged, but not propagated.
     *
     * See the note at [getPairedDeviceAddresses] about using this callback
     * and that function in the correct order.
     */
    var onDeviceUnpaired: (deviceAddress: BluetoothAddress) -> Unit

    /**
     * Callback for filtering devices based on their Bluetooth addresses.
     *
     * This is used for checking if a device shall be processed or ignored.
     * When a newly paired device is discovered, or a paired device is
     * unpaired, this callback is invoked. If it returns false, then
     * the device is ignored, and those callbacks don't get called.
     *
     * Note that this callback may be called from another thread. Using
     * synchronization primitives to avoid race conditions is recommended.
     * Also, implementations must make sure that setting the callback
     * can not cause data races.
     *
     * Do not spend too much time in this callback, since it may block
     * internal threads.
     *
     * IMPORTANT: This callback must not throw.
     *
     * The default callback always returns true.
     */
    var deviceFilterCallback: (deviceAddress: BluetoothAddress) -> Boolean

    /**
     * Starts discovery of Bluetooth devices that haven't been paired yet.
     *
     * Discovery is actually a process that involves multiple parts:
     *
     * 1. An SDP service is set up. This service is then announced to
     *    Bluetooth devices. Each SDP device has a record with multiple
     *    attributes, three of which are defined by the sdp* arguments.
     * 2. Pairing is set up so that when a device tries to pair with the
     *    interface, it is authenticated using the given PIN.
     * 3. Each detected device is filtered via its address by calling
     *    the [deviceFilterCallback]. Only those devices whose addresses
     *    pass this filter are forwarded to the pairing authorization
     *    (see step 2 above). As a result, only the filtered devices
     *    can eventually have their address passed to the
     *    foundNewPairedDevice callback.
     *
     * Note that the callbacks typically are called from a different
     * thread, so make sure that thread synchronization primitives like
     * mutexes are used.
     *
     * Do not spend too much time in the callbacks, since this
     * may block internal threads.
     *
     * This function may only be called after creating the interface and
     * after discovery stopped.
     *
     * Discovery can stop because of these reasons:
     *
     * 1. [stopDiscovery] is called. This will cause the discoveryStopped
     *    callback to be invoked, with its "reason" argument value set to
     *    [DiscoveryStoppedReason.MANUALLY_STOPPED].
     * 2. An error occurred during discovery. The discoveryStopped callback
     *    is then called with its "reason" argument value set to
     *    [DiscoveryStoppedReason.DISCOVERY_ERROR].
     * 3. The discovery timeout was reached and no device was discovered.
     *    The discoveryStopped callback is then called with its "reason"
     *    argument value set to [DiscoveryStoppedReason.DISCOVERY_TIMEOUT].
     * 4. A device is discovered and paired (with the given pairing PIN).
     *    The discoveryStopped callback is _not_ called in that case.
     *    The foundNewPairedDevice callback is called (after discovery
     *    was shut down), both announcing the newly discovered device to
     *    the caller and implicitly notifying that discovery stopped.
     *
     * @param sdpServiceName Name for the SDP service record.
     *        Must not be empty.
     * @param sdpServiceProvider Human-readable name of the provider of
     *        this SDP service record. Must not be empty.
     * @param sdpServiceDescription Human-readable description of
     *        this SDP service record. Must not be empty.
     * @param btPairingPin Bluetooth PIN code to use for pairing.
     *        Not to be confused with the Combo's 10-digit pairing PIN.
     *        This PIN is a sequence of characters used by the Bluetooth
     *        stack for its pairing/authorization.
     * @param discoveryDuration How long the discovery shall go on,
     *        in seconds. Must be a value between 1 and 300.
     * @param onDiscoveryStopped: Callback that gets invoked when discovery
     *        is stopped for any reason _other_ than that a device
     *        was discovered.
     * @param onFoundNewPairedDevice Callback that gets invoked when a device
     *        was found that passed the filter (see [deviceFilterCallback])
     *        and is paired. Exceptions thrown by this callback are logged,
     *        but not propagated. Discovery is stopped before this is called.
     * @throws IllegalStateException if this is called again after
     *         discovery has been started already, or if the interface
     *         is in a state in which discovery is not possible, such as
     *         a Bluetooth subsystem that has been shut down.
     * @throws BluetoothPermissionException if discovery fails because
     *         scanning and connection permissions are missing.
     * @throws BluetoothNotEnabledException if the system's
     *         Bluetooth adapter is currently not enabled.
     * @throws BluetoothNotAvailableException if the system's
     *         Bluetooth adapter is currently not available.
     * @throws BluetoothException if discovery fails due to an underlying
     *         Bluetooth issue.
     */
    fun startDiscovery(
        sdpServiceName: String,
        sdpServiceProvider: String,
        sdpServiceDescription: String,
        btPairingPin: String,
        discoveryDuration: Int,
        onDiscoveryStopped: (reason: DiscoveryStoppedReason) -> Unit,
        onFoundNewPairedDevice: (deviceAddress: BluetoothAddress) -> Unit
    )

    /**
     * Stops any ongoing discovery.
     *
     * If no discovery is going on, this does nothing.
     */
    fun stopDiscovery()

    /**
     * Creates and returns a BluetoothDevice for the given address.
     *
     * This merely creates a new BluetoothDevice instance. It does
     * not connect to the device. Use [BluetoothDevice.connect]
     * for that purpose.
     *
     * NOTE: Creating multiple instances to the same device is
     * possible, but untested.
     *
     * @return BluetoothDevice instance for the device with the
     *         given address
     * @throws BluetoothNotEnabledException if the system's
     *         Bluetooth adapter is currently not enabled.
     * @throws BluetoothNotAvailableException if the system's
     *         Bluetooth adapter is currently not available.
     * @throws IllegalStateException if the interface is in a state
     *         in which accessing devices is not possible, such as
     *         a Bluetooth subsystem that has been shut down.
     */
    fun getDevice(deviceAddress: BluetoothAddress): BluetoothDevice

    /**
     * Returns the friendly (= human-readable) name for the adapter.
     *
     * @throws BluetoothPermissionException if getting the adapter name
     *         fails because connection permissions are missing.
     * @throws BluetoothNotAvailableException if the system's
     *         Bluetooth adapter is currently not available.
     * @throws BluetoothException if getting the adapter name fails
     *         due to an underlying Bluetooth issue.
     */
    fun getAdapterFriendlyName(): String

    /**
     * Returns a set of addresses of paired Bluetooth devices.
     *
     * The [deviceFilterCallback] is applied here. That is, the returned set
     * only contains addresses of devices which passed that filter.
     *
     * The return value is a new set, not a reference to an internal
     * one, so it is safe to use even if devices get paired/unpaired
     * in the meantime.
     *
     * To avoid a race condition where an unpaired device is missed
     * when an application is starting, it is recommended to first
     * assign the [onDeviceUnpaired] callback, and then retrieve the
     * list of paired addresses here. If it is done the other way
     * round, it is possible that between the [getPairedDeviceAddresses]
     * call and the [onDeviceUnpaired] assignment, a device is
     * unpaired, and thus does not get noticed.
     *
     * @throws BluetoothNotEnabledException if the system's
     *         Bluetooth adapter is currently not enabled.
     * @throws BluetoothNotAvailableException if the system's
     *         Bluetooth adapter is currently not available.
     */
    fun getPairedDeviceAddresses(): Set<BluetoothAddress>
}
