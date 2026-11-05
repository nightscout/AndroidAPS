package app.aaps.wear.watchdirect

import android.content.Context
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
        driver.saveIdentity(OnePlusSensorIdentity(pin = pairingCode, serial = serial))
        driver.connect(address, pairingCode)
    }

    override fun resume(): Boolean = driver.resumeStoredSession()

    override fun forget() {
        driver.disconnect()
        driver.sensorStore()?.clearAll()
        connected = false
    }

    private companion object {

        /** The watch's own sensor store file, apart from anything a phone build would use. */
        const val STORE_NAMESPACE = "watch"
        const val SHARED_KEY_SIZE = 16
    }
}
