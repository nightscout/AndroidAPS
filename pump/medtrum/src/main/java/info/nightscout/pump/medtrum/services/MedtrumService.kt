package info.nightscout.pump.medtrum.services

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.SystemClock
import dagger.android.DaggerService
import dagger.android.HasAndroidInjector
import info.nightscout.core.utils.fabric.FabricPrivacy
import info.nightscout.interfaces.Constants
import info.nightscout.interfaces.constraints.Constraints
import info.nightscout.interfaces.notifications.Notification
import info.nightscout.interfaces.plugin.ActivePlugin
import info.nightscout.interfaces.profile.Profile
import info.nightscout.interfaces.profile.ProfileFunction
import info.nightscout.interfaces.pump.BolusProgressData
import info.nightscout.interfaces.pump.PumpEnactResult
import info.nightscout.interfaces.pump.PumpSync
import info.nightscout.interfaces.queue.Callback
import info.nightscout.interfaces.queue.Command
import info.nightscout.interfaces.queue.CommandQueue
import info.nightscout.interfaces.ui.UiInteraction
import info.nightscout.pump.medtrum.MedtrumPlugin
import info.nightscout.pump.medtrum.encryption.Crypt
import info.nightscout.pump.medtrum.extension.toByteArray
import info.nightscout.rx.AapsSchedulers
import info.nightscout.rx.bus.RxBus
import info.nightscout.rx.events.EventAppExit
import info.nightscout.rx.events.EventInitializationChanged
import info.nightscout.rx.events.EventOverviewBolusProgress
import info.nightscout.rx.events.EventProfileSwitchChanged
import info.nightscout.rx.events.EventPumpStatusChanged
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.logging.LTag
import info.nightscout.shared.interfaces.ResourceHelper
import info.nightscout.shared.sharedPreferences.SP
import info.nightscout.shared.utils.DateUtil
import info.nightscout.shared.utils.T
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import java.util.*
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.min

class MedtrumService : DaggerService(), BLECommCallback {

    @Inject lateinit var injector: HasAndroidInjector
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var sp: SP
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var commandQueue: CommandQueue
    @Inject lateinit var context: Context
    @Inject lateinit var medtrumPlugin: MedtrumPlugin
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var constraintChecker: Constraints
    @Inject lateinit var uiInteraction: UiInteraction
    @Inject lateinit var bleComm: BLEComm
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var pumpSync: PumpSync
    @Inject lateinit var dateUtil: DateUtil

    companion object {
        private const val COMMAND_AUTH_REQ: Byte = 5
    }

    private val disposable = CompositeDisposable()
    private val mBinder: IBinder = LocalBinder()
    private val mCrypt = Crypt()

    private var mDeviceSN: Long = 0

    override fun onCreate() {
        super.onCreate()
        bleComm.setCallback(this)
        disposable += rxBus
            .toObservable(EventAppExit::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ stopSelf() }, fabricPrivacy::logException)
    }

    override fun onDestroy() {
        disposable.clear()
        super.onDestroy()
    }

    val isConnected: Boolean
        get() = bleComm.isConnected

    val isConnecting: Boolean
        get() = bleComm.isConnecting

    fun connect(from: String, deviceSN: Long): Boolean {
        // TODO Check we might want to replace this with start scan?
        mDeviceSN = deviceSN
        return bleComm.connect(from, deviceSN)
    }

    fun stopConnecting() {
        // TODO proper way for this might need send commands etc
        bleComm.stopConnecting()
    }

    fun disconnect(from: String) {
        // TODO proper way for this might need send commands etc
        bleComm.disconnect(from)
    }

    fun readPumpStatus() {
        // TODO
    }

    fun loadEvents(): PumpEnactResult {
        if (!medtrumPlugin.isInitialized()) {
            val result = PumpEnactResult(injector).success(false)
            result.comment = "pump not initialized"
            return result
        }
        // TODO need this? Check
        val result = PumpEnactResult(injector)
        return result
    }

    fun setUserSettings(): PumpEnactResult {
        // TODO need this? Check
        val result = PumpEnactResult(injector)
        return result
    }

    fun bolus(insulin: Double, carbs: Int, carbTime: Long, t: EventOverviewBolusProgress.Treatment): Boolean {
        if (!isConnected) return false
        // TODO
        return false
    }

    fun bolusStop() {
        // TODO
    }

    fun tempBasal(percent: Int, durationInHours: Int): Boolean {
        // TODO
        return false
    }

    fun highTempBasal(percent: Int): Boolean {
        // TODO
        return false
    }

    fun tempBasalShortDuration(percent: Int, durationInMinutes: Int): Boolean {
        if (durationInMinutes != 15 && durationInMinutes != 30) {
            aapsLogger.error(LTag.PUMPCOMM, "Wrong duration param")
            return false
        }
        // TODO
        return false
    }

    fun tempBasalStop(): Boolean {
        if (!isConnected) return false
        // TODO
        return false
    }

    fun updateBasalsInPump(profile: Profile): Boolean {
        if (!isConnected) return false
        // TODO
        return false
    }

    private fun authorize() {
        aapsLogger.debug(LTag.PUMPCOMM, "Start auth!")
        val role = 2 // Fixed to 2 for pump
        val key = mCrypt.keyGen(mDeviceSN)
        val commandData = byteArrayOf(COMMAND_AUTH_REQ) + byteArrayOf(role.toByte()) + 0.toByteArray(4) + key.toByteArray(4)
        bleComm.sendMessage(commandData)
    }

    /** BLECommCallbacks */
    override fun onBLEConnected() {
        // TODO Replace by FSM Entry?
        authorize()
    }

    override fun onNotification(notification: ByteArray) {
        aapsLogger.debug(LTag.PUMPCOMM, "<<<<< onNotification" + notification.contentToString())
        // TODO 
    }

    override fun onIndication(indication: ByteArray) {
        aapsLogger.debug(LTag.PUMPCOMM, "<<<<< onIndication" + indication.contentToString())
        // TODO 
    }

    override fun onSendMessageError(reason: String) {
        aapsLogger.debug(LTag.PUMPCOMM, "<<<<< error during send message " + reason)
        // TODO 
    }

    /** Service stuff */
    inner class LocalBinder : Binder() {
        val serviceInstance: MedtrumService
            get() = this@MedtrumService
    }

    override fun onBind(intent: Intent): IBinder {
        return mBinder
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        return Service.START_STICKY
    }
}