package info.nightscout.androidaps.plugins.general.persistentNotification

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import dagger.android.DaggerService
import info.nightscout.androidaps.events.EventAppExit
import info.nightscout.androidaps.interfaces.NotificationHolder
import info.nightscout.shared.logging.AAPSLogger
import info.nightscout.shared.logging.LTag
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.rx.AapsSchedulers
import io.reactivex.rxjava3.disposables.CompositeDisposable
import javax.inject.Inject

/**
 * Keeps AndroidAPS in foreground state, so it won't be terminated by Android nor get restricted by the background execution limits
 */
class DummyService : DaggerService() {

    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var notificationHolder: NotificationHolder

    private val disposable = CompositeDisposable()

    inner class LocalBinder : Binder() {

        fun getService(): DummyService = this@DummyService
    }

    private val binder = LocalBinder()
    override fun onBind(intent: Intent): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        // TODO: I guess this was moved here in order to adhere to the 5 seconds rule to call "startForeground" after a Service was called as Foreground service?
        // As onCreate() is not called every time a service is started, copied to onStartCommand().
        try {
            aapsLogger.debug("Starting DummyService with ID ${notificationHolder.notificationID} notification ${notificationHolder.notification}")
            startForeground(notificationHolder.notificationID, notificationHolder.notification)
        } catch (e: Exception) {
            startForeground(4711, Notification())
        }
        disposable.add(rxBus
            .toObservable(EventAppExit::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({
                aapsLogger.debug(LTag.CORE, "EventAppExit received")
                stopSelf()
            }, fabricPrivacy::logException)
        )
    }

    override fun onDestroy() {
        aapsLogger.debug(LTag.CORE, "onDestroy")
        disposable.clear()
        super.onDestroy()
        stopForeground(true)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        try {
            aapsLogger.debug("Starting DummyService with ID ${notificationHolder.notificationID} notification ${notificationHolder.notification}")
            startForeground(notificationHolder.notificationID, notificationHolder.notification)
        } catch (e: Exception) {
            startForeground(4711, Notification())
        }
        return Service.START_STICKY
    }
}
