package info.nightscout.comboctl.android

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import info.nightscout.comboctl.base.BluetoothAddress
import info.nightscout.comboctl.base.BluetoothDevice
import info.nightscout.comboctl.base.BluetoothException
import info.nightscout.comboctl.base.BluetoothInterface
import info.nightscout.comboctl.base.LogLevel
import info.nightscout.comboctl.base.Logger
import info.nightscout.comboctl.base.toBluetoothAddress
import java.io.IOException
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread
import android.bluetooth.BluetoothAdapter as SystemBluetoothAdapter
import android.bluetooth.BluetoothDevice as SystemBluetoothDevice
import android.bluetooth.BluetoothManager as SystemBluetoothManager
import android.bluetooth.BluetoothServerSocket as SystemBluetoothServerSocket
import android.bluetooth.BluetoothSocket as SystemBluetoothSocket

private val logger = Logger.get("AndroidBluetoothInterface")

/**
 * Class for accessing Bluetooth functionality on Android.
 *
 * This needs an Android [Context] that is always present for
 * the duration of the app's existence. It is not recommended
 * to use the context from an [Activity], since such a context
 * may go away if the user turns the screen for example. If
 * the context goes away, and discovery is ongoing, then that
 * discovery prematurely ends. The context of an [Application]
 * instance is an ideal choice.
 */
class AndroidBluetoothInterface(private val androidContext: Context) : BluetoothInterface {
    private var bluetoothAdapter: SystemBluetoothAdapter? = null
    private var rfcommServerSocket: SystemBluetoothServerSocket? = null
    private var discoveryStarted = false
    private var discoveryBroadcastReceiver: BroadcastReceiver? = null

    // Note that this contains ALL paired/bonded devices, not just
    // the ones that pass the deviceFilterCallback.This is important
    // in case the filter is changed sometime later, otherwise
    // getPairedDeviceAddresses() would return an incomplete
    // list.getPairedDeviceAddresses() has to apply the filter manually.
    private val pairedDeviceAddresses = mutableSetOf<BluetoothAddress>()

    // This is necessary, since the BroadcastReceivers always
    // run in the UI thread, while access to the pairedDeviceAddresses
    // can be requested from other threads.
    private val deviceAddressLock = ReentrantLock()

    private var listenThread: Thread? = null

    private var unpairedDevicesBroadcastReceiver: BroadcastReceiver? = null

    // Stores SystemBluetoothDevice that were previously seen in
    // onAclConnected(). These instances represent a device that
    // was found during discovery. The first time the device is
    // discovered, an instance is provided - but that first instance
    // is not usable (this seems to be caused by an underlying
    // Bluetooth stack bug). Only when _another_ instance that
    // represents the same device is seen can that other instance
    // be used and pairing can continue. Therefore, we store the
    // previous observation to be able to detect whether a
    // discovered instance is the first or second one that represents
    // the device. We also retain the first instance until the
    // second one is found - this seems to improve pairing stability
    // on some Android devices.
    // TODO: Find out why these weird behavior occurs and why
    // we can only use the second instance.
    private val previouslyDiscoveredDevices = mutableMapOf<BluetoothAddress, SystemBluetoothDevice?>()

    // Set to a non-null value if discovery timeouts or if it is
    // manually stopped via stopDiscovery().
    private var discoveryStoppedReason: BluetoothInterface.DiscoveryStoppedReason? = null
    // Set to true once a device is found. Used in onDiscoveryFinished()
    // to suppress a discoveryStopped callback invocation.
    private var foundDevice = false

    // Invoked if discovery stops for any reason other than that
    // a device was found.
    private var discoveryStopped: (reason: BluetoothInterface.DiscoveryStoppedReason) -> Unit = { }

    override var onDeviceUnpaired: (deviceAddress: BluetoothAddress) -> Unit = { }

    override var deviceFilterCallback: (deviceAddress: BluetoothAddress) -> Boolean = { true }

    /**
     * Safe version of getParcelableExtra depending on Android version running
     */
    fun <T> Intent.safeGetParcelableExtra(name: String?, clazz: Class<T>): T? =
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) getParcelableExtra(name, clazz)
        else @Suppress("DEPRECATION") getParcelableExtra(name)

    fun setup() {
        val bluetoothManager = androidContext.getSystemService(Context.BLUETOOTH_SERVICE) as SystemBluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        val bondedDevices = checkForConnectPermission(androidContext) {
            bluetoothAdapter!!.bondedDevices
        }

        logger(LogLevel.DEBUG) { "Found ${bondedDevices.size} bonded Bluetooth device(s)" }

        for (bondedDevice in bondedDevices) {
            val androidBtAddressString = bondedDevice.address
            logger(LogLevel.DEBUG) {
                "... device $androidBtAddressString"
            }

            try {
                val comboctlBtAddress = androidBtAddressString.toBluetoothAddress()
                pairedDeviceAddresses.add(comboctlBtAddress)
            } catch (e: IllegalArgumentException) {
                logger(LogLevel.ERROR) {
                    "Could not convert Android bluetooth device address " +
                            "\"$androidBtAddressString\" to a valid BluetoothAddress instance; skipping device"
                }
            }
        }

        unpairedDevicesBroadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                logger(LogLevel.DEBUG) { "unpairedDevicesBroadcastReceiver received new action: ${intent.action}" }

                when (intent.action) {
                    SystemBluetoothDevice.ACTION_BOND_STATE_CHANGED -> onBondStateChanged(intent)
                    else -> Unit
                }
            }
        }

        androidContext.registerReceiver(
            unpairedDevicesBroadcastReceiver,
            IntentFilter(SystemBluetoothDevice.ACTION_BOND_STATE_CHANGED)
        )
    }

    fun teardown() {
        if (unpairedDevicesBroadcastReceiver != null) {
            androidContext.unregisterReceiver(unpairedDevicesBroadcastReceiver)
            unpairedDevicesBroadcastReceiver = null
        }
    }

    /** Callback for custom discovery activity startup.
     *
     * Useful for when more elaborate start procedures are done such as those that use
     * [ActivityResultCaller.registerForActivityResult]. If this callback is set to null,
     * the default behavior is used (= start activity with [Activity.startActivity]).
     * Note that this default behavior does not detected when the user rejects permission
     * to make the Android device discoverable.
     */
    var customDiscoveryActivityStartCallback: ((intent: Intent) -> Unit)? = null

    override fun startDiscovery(
        sdpServiceName: String,
        sdpServiceProvider: String,
        sdpServiceDescription: String,
        btPairingPin: String,
        discoveryDuration: Int,
        onDiscoveryStopped: (reason: BluetoothInterface.DiscoveryStoppedReason) -> Unit,
        onFoundNewPairedDevice: (deviceAddress: BluetoothAddress) -> Unit
    ) {
        check(!discoveryStarted) { "Discovery already started" }

        previouslyDiscoveredDevices.clear()
        foundDevice = false
        discoveryStoppedReason = null

        // The Combo communicates over RFCOMM using the SDP Serial Port Profile.
        // We use an insecure socket, which means that it lacks an authenticated
        // link key. This is done because the Combo does not use this feature.
        //
        // TODO: Can Android RFCOMM SDP service records be given custom
        // sdpServiceProvider and sdpServiceDescription values? (This is not
        // necessary for correct function, just a detail for sake of completeness.)
        logger(LogLevel.DEBUG) { "Setting up RFCOMM listener socket" }
        rfcommServerSocket = checkForConnectPermission(androidContext) {
            bluetoothAdapter!!.listenUsingInsecureRfcommWithServiceRecord(
                sdpServiceName,
                Constants.sdpSerialPortUUID
            )
        }

        // Run a separate thread to accept and throw away incoming RFCOMM connections.
        // We do not actually use those; the RFCOMM listener socket only exists to be
        // able to provide an SDP SerialPort service record that can be discovered by
        // the pump, and that record needs an RFCOMM listener port number.
        listenThread = thread {
            logger(LogLevel.DEBUG) { "RFCOMM listener thread started" }

            try {
                while (true) {
                    logger(LogLevel.DEBUG) { "Waiting for incoming RFCOMM socket to accept" }
                    var socket: SystemBluetoothSocket? = null
                    if (rfcommServerSocket != null)
                        socket = rfcommServerSocket!!.accept()
                    if (socket != null) {
                        logger(LogLevel.DEBUG) { "Closing accepted incoming RFCOMM socket" }
                        try {
                            socket.close()
                        } catch (e: IOException) {
                        }
                    }
                }
            } catch (t: Throwable) {
                // This happens when rfcommServerSocket.close() is called.
                logger(LogLevel.DEBUG) { "RFCOMM listener accept() call aborted" }
            }

            logger(LogLevel.DEBUG) { "RFCOMM listener thread stopped" }
        }

        this.discoveryStopped = onDiscoveryStopped

        logger(LogLevel.DEBUG) {
            "Registering receiver for getting notifications about pairing requests and connected devices"
        }

        val intentFilter = IntentFilter()
        intentFilter.addAction(SystemBluetoothDevice.ACTION_ACL_CONNECTED)
        intentFilter.addAction(SystemBluetoothDevice.ACTION_PAIRING_REQUEST)
        intentFilter.addAction(SystemBluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        intentFilter.addAction(SystemBluetoothAdapter.ACTION_SCAN_MODE_CHANGED)

        discoveryBroadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                logger(LogLevel.DEBUG) { "discoveryBroadcastReceiver received new action: ${intent.action}" }

                when (intent.action) {
                    SystemBluetoothDevice.ACTION_ACL_CONNECTED -> onAclConnected(intent, onFoundNewPairedDevice)
                    SystemBluetoothDevice.ACTION_PAIRING_REQUEST -> onPairingRequest(intent, btPairingPin)
                    SystemBluetoothAdapter.ACTION_DISCOVERY_FINISHED -> onDiscoveryFinished()
                    SystemBluetoothAdapter.ACTION_SCAN_MODE_CHANGED -> onScanModeChanged(intent)
                    else -> Unit
                }
            }
        }

        androidContext.registerReceiver(discoveryBroadcastReceiver, intentFilter)

        logger(LogLevel.DEBUG) { "Starting activity for making this Android device discoverable" }

        val discoverableIntent = Intent(SystemBluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
            putExtra(SystemBluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, discoveryDuration)
            putExtra(SystemBluetoothAdapter.EXTRA_SCAN_MODE, SystemBluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE)
        }

        if (customDiscoveryActivityStartCallback == null) {
            // Do the default start procedure if no custom one was defined.

            // This flag is necessary to be able to start the scan from the given context,
            // which is _not_ an activity. Starting scans from activities is potentially
            // problematic since they can go away at any moment. If this is not desirable,
            // relying on the AAPS Context is better, but we have to create a new task then.
            discoverableIntent.flags = discoverableIntent.flags or Intent.FLAG_ACTIVITY_NEW_TASK

            androidContext.startActivity(discoverableIntent)
        } else {
            customDiscoveryActivityStartCallback?.invoke(discoverableIntent)
        }

        logger(LogLevel.DEBUG) { "Started discovery" }

        discoveryStarted = true
    }

    override fun stopDiscovery() {
        discoveryStoppedReason = BluetoothInterface.DiscoveryStoppedReason.MANUALLY_STOPPED
        stopDiscoveryInternal()
    }

    override fun getDevice(deviceAddress: BluetoothAddress): BluetoothDevice =
        AndroidBluetoothDevice(androidContext, bluetoothAdapter!!, deviceAddress)

    override fun getAdapterFriendlyName() =
        checkForConnectPermission(androidContext) { bluetoothAdapter!!.name }
        ?: throw BluetoothException("Could not get Bluetooth adapter friendly name")

    override fun getPairedDeviceAddresses(): Set<BluetoothAddress> =
        try {
            deviceAddressLock.lock()
            pairedDeviceAddresses.filter { pairedDeviceAddress -> deviceFilterCallback(pairedDeviceAddress) }.toSet()
        } finally {
            deviceAddressLock.unlock()
        }

    private fun stopDiscoveryInternal() {
        // Close the server socket. This frees RFCOMM resources and ends
        // the listenThread because the accept() call inside will be aborted
        // by the close() call.
        try {
            if (rfcommServerSocket != null)
                rfcommServerSocket!!.close()
        } catch (e: IOException) {
            logger(LogLevel.ERROR) { "Caught IO exception while closing RFCOMM server socket: $e" }
        } finally {
            rfcommServerSocket = null
        }

        // The listenThread will be shutting down now after the server
        // socket was closed, since the blocking accept() call inside
        // the thread gets aborted by close(). Just wait here for the
        // thread to fully finish before we continue.
        if (listenThread != null) {
            logger(LogLevel.DEBUG) { "Waiting for RFCOMM listener thread to finish" }
            listenThread!!.join()
            logger(LogLevel.DEBUG) { "RFCOMM listener thread finished" }
            listenThread = null
        }

        if (discoveryBroadcastReceiver != null) {
            androidContext.unregisterReceiver(discoveryBroadcastReceiver)
            discoveryBroadcastReceiver = null
        }

        runIfScanPermissionGranted(androidContext) {
            @SuppressLint("MissingPermission")
            if (bluetoothAdapter!!.isDiscovering) {
                logger(LogLevel.DEBUG) { "Stopping discovery" }
                bluetoothAdapter!!.cancelDiscovery()
            }
        }

        if (discoveryStarted) {
            logger(LogLevel.DEBUG) { "Stopped discovery" }
            discoveryStarted = false
        }
    }

    private fun onAclConnected(intent: Intent, foundNewPairedDevice: (deviceAddress: BluetoothAddress) -> Unit) {
        // Sanity check in case we get this notification for the
        // device already and need to avoid duplicate processing.
        if (intent.getStringExtra("address") != null)
            return

        // Sanity check to make sure we can actually get
        // a Bluetooth device out of the intent. Otherwise,
        // we have to wait for the next notification.
        val androidBtDevice = intent.safeGetParcelableExtra(SystemBluetoothDevice.EXTRA_DEVICE, SystemBluetoothDevice::class.java)
        if (androidBtDevice == null) {
            logger(LogLevel.DEBUG) { "Ignoring ACL_CONNECTED intent that has no Bluetooth device" }
            return
        }

        val androidBtAddressString = androidBtDevice.address
        // This effectively marks the device as "already processed"
        // (see the getStringExtra() call above).
        intent.putExtra("address", androidBtAddressString)

        logger(LogLevel.DEBUG) { "ACL_CONNECTED intent has Bluetooth device with address $androidBtAddressString" }

        val comboctlBtAddress = try {
            androidBtAddressString.toBluetoothAddress()
        } catch (t: Throwable) {
            logger(LogLevel.ERROR) {
                "Could not convert Android bluetooth device address " +
                        "\"$androidBtAddressString\" to a valid BluetoothAddress instance; skipping device"
            }
            return
        }

        // During discovery, the ACTION_ACL_CONNECTED action apparently
        // is notified at least *twice*. And, the device that is present
        // in the intent may not be the same device there was during
        // the first notification (= the parcelableExtra EXTRA_DEVICE
        // seen above). It turns out that only the *second EXTRA_DEVICE
        // can actually be used (otherwise pairing fails). The reason
        // for this is unknown, but seems to be caused by bugs in the
        // Fluoride (aka BlueDroid) Bluetooth stack.
        // To circumvent this, we don't do anything the first time,
        // but remember the device's Bluetooth address. Only when we
        // see that address again do we actually proceed with announcing
        // the device as having been discovered.
        // NOTE: This is different from the getStringExtra() check
        // above. That one checks if the *same* Android Bluetooth device
        // instance was already processed. This check here instead
        // verifies if we have seen the same Bluetooth address on
        // *different* Android Bluetooth device instances.
        if (comboctlBtAddress !in previouslyDiscoveredDevices) {
            previouslyDiscoveredDevices[comboctlBtAddress] = androidBtDevice
            logger(LogLevel.DEBUG) {
                "Device with address $comboctlBtAddress discovered for the first time; " +
                        "need to \"discover\" it again to be able to announce its discovery"
            }
            return
        } else {
            previouslyDiscoveredDevices[comboctlBtAddress] = null
            logger(LogLevel.DEBUG) {
                "Device with address $comboctlBtAddress discovered for the second time; " +
                        "announcing it as discovered"
            }
        }

        // Always adding the device to the paired addresses even
        // if the deviceFilterCallback() below returns false. See
        // the pairedDeviceAddresses comments above for more.
        try {
            deviceAddressLock.lock()
            pairedDeviceAddresses.add(comboctlBtAddress)
        } finally {
            deviceAddressLock.unlock()
        }

        logger(LogLevel.INFO) { "Got device with address $androidBtAddressString" }

        try {
            // Apply device filter before announcing a newly
            // discovered device, just as the ComboCtl
            // BluetoothInterface.startDiscovery()
            // documentation requires.
            if (deviceFilterCallback(comboctlBtAddress)) {
                foundDevice = true
                stopDiscoveryInternal()
                foundNewPairedDevice(comboctlBtAddress)
            }
        } catch (t: Throwable) {
            logger(LogLevel.ERROR) { "Caught error while invoking foundNewPairedDevice callback: $t" }
        }
    }

    private fun onBondStateChanged(intent: Intent) {
        // Here, we handle the case where a previously paired
        // device just got unpaired. The caller needs to know
        // about this to check if said device was a Combo.
        // If so, the caller may have to update states like
        // the pump state store accordingly.

        val androidBtDevice = intent.safeGetParcelableExtra(SystemBluetoothDevice.EXTRA_DEVICE, SystemBluetoothDevice::class.java)
        if (androidBtDevice == null) {
            logger(LogLevel.DEBUG) { "Ignoring BOND_STATE_CHANGED intent that has no Bluetooth device" }
            return
        }

        val androidBtAddressString = androidBtDevice.address

        logger(LogLevel.DEBUG) { "PAIRING_REQUEST intent has Bluetooth device with address $androidBtAddressString" }

        val comboctlBtAddress = try {
            androidBtAddressString.toBluetoothAddress()
        } catch (e: IllegalArgumentException) {
            logger(LogLevel.ERROR) {
                "Could not convert Android bluetooth device address " +
                        "\"$androidBtAddressString\" to a valid BluetoothAddress instance; ignoring device"
            }
            return
        }

        val previousBondState = intent.getIntExtra(SystemBluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, SystemBluetoothDevice.ERROR)
        val currentBondState = intent.getIntExtra(SystemBluetoothDevice.EXTRA_BOND_STATE, SystemBluetoothDevice.ERROR)

        // An unpaired device is characterized by a state change
        // from non-NONE to NONE. Filter out all other state changes.
        if (!((currentBondState == SystemBluetoothDevice.BOND_NONE) && (previousBondState != SystemBluetoothDevice.BOND_NONE))) {
            return
        }

        previouslyDiscoveredDevices.remove(comboctlBtAddress)

        // Always removing the device from the paired addresses
        // event if the deviceFilterCallback() below returns false.
        // See the pairedDeviceAddresses comments above for more.
        try {
            deviceAddressLock.lock()
            pairedDeviceAddresses.remove(comboctlBtAddress)
            logger(LogLevel.DEBUG) { "Removed device with address $comboctlBtAddress from the list of paired devices" }
        } finally {
            deviceAddressLock.unlock()
        }

        // Apply device filter before announcing an
        // unpaired device, just as the ComboCtl
        // BluetoothInterface.startDiscovery()
        // documentation requires.
        try {
            if (deviceFilterCallback(comboctlBtAddress)) {
                onDeviceUnpaired(comboctlBtAddress)
            }
        } catch (t: Throwable) {
            logger(LogLevel.ERROR) { "Caught error while invoking onDeviceUnpaired callback: $t" }
        }
    }

    private fun onPairingRequest(intent: Intent, btPairingPin: String) {
        val androidBtDevice = intent.safeGetParcelableExtra(SystemBluetoothDevice.EXTRA_DEVICE, SystemBluetoothDevice::class.java)
        if (androidBtDevice == null) {
            logger(LogLevel.DEBUG) { "Ignoring PAIRING_REQUEST intent that has no Bluetooth device" }
            return
        }

        val androidBtAddressString = androidBtDevice.address

        logger(LogLevel.DEBUG) { "PAIRING_REQUEST intent has Bluetooth device with address $androidBtAddressString" }

        val comboctlBtAddress = try {
            androidBtAddressString.toBluetoothAddress()
        } catch (e: IllegalArgumentException) {
            logger(LogLevel.ERROR) {
                "Could not convert Android bluetooth device address " +
                        "\"$androidBtAddressString\" to a valid BluetoothAddress instance; ignoring device"
            }
            return
        }

        if (!deviceFilterCallback(comboctlBtAddress)) {
            logger(LogLevel.DEBUG) { "This is not a Combo pump; ignoring device" }
            return
        }

        logger(LogLevel.INFO) {
            " Device with address $androidBtAddressString is a Combo pump; accepting Bluetooth pairing request"
        }

        // NOTE: The setPin(), createBond(), and setPairingConfirmation()
        // calls *must* be made, no matter if the permissions were given
        // or not. Otherwise, pairing fails. This is because the Combo's
        // pairing mechanism is unusual; the Bluetooth PIN is hardcoded
        // (see the BT_PAIRING_PIN constant), and the application enters
        // it programmatically. For security reasons, this isn't normally
        // doable. But, with the calls below, it seems to work. This sort
        // of bends what is possible in Android, and is the cause for
        // pairing difficulties, but cannot be worked around.
        //
        // This means that setPin(), createBond(), and setPairingConfirmation()
        // _must not_ be called with functions like checkForConnectPermission(),
        // since those functions would always detect the missing permissions
        // and refuse to invoke these functions.
        //
        // Furthermore, setPairingConfirmation requires the BLUETOOTH_PRIVILEGED
        // permission. However, this permission is only accessible to system
        // apps. But again, without it, pairing fails, and we are probably
        // using undocumented behavior here. The call does fail with a
        // SecurityException, but still seems to do _something_.

        try {
            @SuppressLint("MissingPermission")
            if (!androidBtDevice.setPin(btPairingPin.encodeToByteArray())) {
                logger(LogLevel.WARN) { "Could not set Bluetooth pairing PIN" }
            }
        } catch (t: Throwable) {
            logger(LogLevel.WARN) { "Caught error while setting Bluetooth pairing PIN: $t" }
        }

        try {
            @SuppressLint("MissingPermission")
            if (!androidBtDevice.createBond()) {
                logger(LogLevel.WARN) { "Could not create bond" }
            }
        } catch (t: Throwable) {
            logger(LogLevel.WARN) { "Caught error while creating bond: $t" }
        }

        try {
            @SuppressLint("MissingPermission")
            if (!androidBtDevice.setPairingConfirmation(true)) {
                logger(LogLevel.WARN) { "Could not set pairing confirmation" }
            }
        } catch (t: Throwable) {
            logger(LogLevel.WARN) { "Caught exception while setting pairing confirmation: $t" }
        }

        logger(LogLevel.INFO) { "Established Bluetooth pairing with Combo pump with address $androidBtAddressString" }
    }

    private fun onDiscoveryFinished() {
        logger(LogLevel.DEBUG) { "Discovery finished" }

        // If a device was found, foundNewPairedDevice is called,
        // which implicitly announces that discovery stopped.
        if (!foundDevice) {
            // discoveryStoppedReason is set to a non-NULL value only
            // if stopDiscovery() is called. If the discovery timeout
            // is reached, we get to this point, but the value of
            // discoveryStoppedReason  is still null.
            discoveryStopped(discoveryStoppedReason ?: BluetoothInterface.DiscoveryStoppedReason.DISCOVERY_TIMEOUT)
        }
    }

    private fun onScanModeChanged(intent: Intent) {
        // Only using EXTRA_SCAN_MODE here, since EXTRA_PREVIOUS_SCAN_MODE never
        // seems to be populated. See: https://stackoverflow.com/a/30935424/560774
        // This appears to be either a bug in Android or an error in the documentation.

        val currentScanMode = intent.getIntExtra(SystemBluetoothAdapter.EXTRA_SCAN_MODE, SystemBluetoothAdapter.ERROR)
        if (currentScanMode == SystemBluetoothAdapter.ERROR) {
            logger(LogLevel.ERROR) { "Could not get current scan mode; EXTRA_SCAN_MODE extra field missing" }
            return
        }

        logger(LogLevel.DEBUG) { "Scan mode changed to $currentScanMode" }

        // Since EXTRA_PREVIOUS_SCAN_MODE is not available, we have to use a trick
        // to make sure we detect a discovery timeout. If there's a broadcast
        // receiver, we must have been discoverable so far. And if the EXTRA_SCAN_MODE
        // field indicates that we aren't discoverable right now, it follows that
        // we used to be discoverable but no longer are.
        if ((discoveryBroadcastReceiver != null) &&
            (currentScanMode != SystemBluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE)
        ) {
            logger(LogLevel.INFO) { "We are no longer discoverable" }
            // Only proceed if the discovery timed out. This happens if no device was
            // found and discoveryStoppedReason wasn't set. (see stopDiscovery()
            // for an example where discoveryStoppedReason is set prior to stopping.)
            if (!foundDevice && (discoveryStoppedReason == null)) {
                discoveryStoppedReason = BluetoothInterface.DiscoveryStoppedReason.DISCOVERY_TIMEOUT
                onDiscoveryFinished()
            }
        }
    }
}
