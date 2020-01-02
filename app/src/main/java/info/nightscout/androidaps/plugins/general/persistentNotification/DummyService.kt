package info.nightscout.androidaps.plugins.general.persistentNotification

import android.app.Service
import android.content.Intent
import android.os.IBinder
import dagger.android.DaggerService
import info.nightscout.androidaps.events.EventAppExit
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.utils.FabricPrivacy
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import javax.inject.Inject

/**
 * Keeps AndroidAPS in foreground state, so it won't be terminated by Android nor get restricted by the background execution limits
 */
class DummyService : DaggerService() {

    @Inject lateinit var rxBus: RxBusWrapper
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var persistentNotificationPlugin: PersistentNotificationPlugin

    private val disposable = CompositeDisposable()

    override fun onBind(intent: Intent): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        // TODO: I guess this was moved here in order to adhere to the 5 seconds rule to call "startForeground" after a Service was called as Foreground service?
        // As onCreate() is not called every time a service is started, copied to onStartCommand().
        startForeground(persistentNotificationPlugin.ONGOING_NOTIFICATION_ID, persistentNotificationPlugin.getLastNotification())
        disposable.add(rxBus
            .toObservable(EventAppExit::class.java)
            .observeOn(Schedulers.io())
            .subscribe({
                aapsLogger.debug(LTag.CORE, "EventAppExit received")
                stopSelf()
            }) { FabricPrivacy.logException(it) }
        )
    }

    override fun onDestroy() {
        aapsLogger.debug(LTag.CORE, "onDestroy")
        disposable.clear()
        super.onDestroy()
        stopForeground(true)
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        startForeground(persistentNotificationPlugin.ONGOING_NOTIFICATION_ID, persistentNotificationPlugin.getLastNotification())
        return Service.START_STICKY
    }
}