package app.aaps.wear.watchdirect

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import app.aaps.plugins.dexcomoneplus.OnePlusAndroidPlatform
import app.aaps.plugins.dexcomoneplus.OnePlusCgmDriverReal
import app.aaps.plugins.dexcomoneplus.OnePlusDisplaySlot
import app.aaps.plugins.dexcomoneplus.OnePlusGlucoseSample
import app.aaps.plugins.dexcomoneplus.OnePlusGlucoseWatcher
import app.aaps.plugins.dexcomoneplus.OnePlusWarmupState
import app.aaps.plugins.dexcomoneplus.identity.OnePlusSensorIdentity

/**
 * [WatchSensorLink] on our Dexcom ONE+ / G7 driver, in its light watch mode: the sensor's watch slot
 * ([OnePlusDisplaySlot.WATCH]) with the watch's own key, the generic radio profile, no pre-soak slot,
 * no sensor start and no calibration. The phone stays the master of the sensor.
 *
 * Built only on Android 12 or newer (the driver's Bluetooth calls need it), see
 * [WatchDirect.isSupported].
 *
 * The driver's readings stop here. They are turned into [WatchSensorReading], which only the watch
 * faces see.
 */
class OnePlusWatchSensorLink(context: Context) : WatchSensorLink {

    private val appContext = context.applicationContext

    private val driver: OnePlusCgmDriverReal by lazy {
        OnePlusCgmDriverReal(storeNamespace = STORE_NAMESPACE, displaySlot = OnePlusDisplaySlot.WATCH).also {
            it.attach(OnePlusAndroidPlatform(appContext))
            it.addWatcher(watcher)
        }
    }

    @Volatile private var listener: WatchSensorLink.Listener? = null

    @Volatile private var connected = false

    private val watcher = object : OnePlusGlucoseWatcher {
        override fun onWarmup(state: OnePlusWarmupState) = Unit

        override fun onGlucose(sample: OnePlusGlucoseSample) {
            listener?.onReading(WatchSensorReading(sample.mgdl, sample.timestampMs, sample.trendSlopeMgdlPerMin))
        }

        override fun onSession(up: Boolean, reason: String?) {
            connected = up
            listener?.onConnected(up)
        }

        override fun onError(message: String, fatal: Boolean) {
            listener?.onProblem(message, fatal)
        }
    }

    override fun setListener(listener: WatchSensorLink.Listener) {
        this.listener = listener
    }

    override fun address(): String? = driver.sensorStore()?.load()?.lastMac

    override fun pairingCode(): String? = driver.sensorStore()?.load()?.identity?.pin

    override fun hasKey(): Boolean = driver.savedSharedKey()?.size == SHARED_KEY_SIZE

    override fun isConnected(): Boolean = connected && driver.isSessionUp()

    override fun connect(address: String, pairingCode: String, serial: String?) {
        setPaused(false)
        removeBond(address)
        driver.saveIdentity(OnePlusSensorIdentity(pin = pairingCode, serial = serial))
        driver.connect(address, pairingCode)
    }

    override fun resume(): Boolean {
        setPaused(false)
        return driver.resumeStoredSession()
    }

    override fun pairAgain(): Boolean {
        val stored = driver.sensorStore()?.load() ?: return false
        val address = stored.lastMac ?: return false
        setPaused(false)
        removeBond(address)
        driver.connect(address, stored.identity.pin)
        return true
    }

    override fun pause() {
        driver.disconnect()
        connected = false
        setPaused(true)
    }

    override fun isPaused(): Boolean = settings.getBoolean(KEY_PAUSED, false)

    override fun forget() {
        val address = address()
        driver.disconnect()
        driver.sensorStore()?.clearAll()
        connected = false
        setPaused(false)
        if (address != null) removeBond(address)
    }

    private val settings: SharedPreferences get() = appContext.getSharedPreferences(SETTINGS_FILE, Context.MODE_PRIVATE)

    private fun setPaused(paused: Boolean) = settings.edit().putBoolean(KEY_PAUSED, paused).apply()

    /**
     * Remove the watch's Bluetooth bond with the sensor at [address], before a pairing from the
     * start and when the sensor is forgotten.
     *
     * A key exchange without a stored key must not run over an old bond. On a Galaxy Watch 5 the
     * sensor agreed on the new key, closed the link, and refused that key at the next contact, again
     * and again. The driver only removes the bond when the key exchange itself fails, which it did
     * not. The watch's settings do not list the bond, so the user cannot remove it by hand.
     *
     * Android has no public call for this. It is the same hidden call the driver uses on the phone
     * (`OnePlusGattClientAndroid.removeBond`). Where the watch blocks it, the bond stays and the
     * log says so.
     */
    @SuppressLint("MissingPermission") // Without the permission the call throws, and that is caught.
    private fun removeBond(address: String) {
        try {
            val device = appContext.getSystemService(BluetoothManager::class.java)?.adapter?.getRemoteDevice(address) ?: return
            if (device.bondState == BluetoothDevice.BOND_NONE) return
            val removed = device.javaClass.getMethod("removeBond").invoke(device)
            Log.i(TAG, "Direct to watch: old bond with the sensor removed before pairing, result=$removed")
        } catch (e: Exception) {
            // SecurityException without the Bluetooth permission, or the hidden call is blocked.
            Log.w(TAG, "Direct to watch: the old bond with the sensor could not be removed", e)
        }
    }

    private companion object {

        const val TAG = "WatchDirect"
        const val SETTINGS_FILE = "watch_direct_link"
        const val KEY_PAUSED = "paused"


        /** The watch's own sensor store file, apart from anything a phone build would use. */
        const val STORE_NAMESPACE = "watch"
        const val SHARED_KEY_SIZE = 16
    }
}
