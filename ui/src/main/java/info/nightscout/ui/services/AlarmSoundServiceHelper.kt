package info.nightscout.ui.services

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.annotation.RawRes
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.NotificationHolder
import dagger.Lazy
import javax.inject.Inject
import javax.inject.Singleton

/*
    This code replaces  following
    val alarm = Intent(context, AlarmSoundService::class.java)
    alarm.putExtra("soundId", n.soundId)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(alarm) else context.startService(alarm)

    it fails randomly with error
    Context.startForegroundService() did not then call Service.startForeground(): ServiceRecord{e317f7e u0 info.nightscout.nsclient/info.nightscout.androidaps.services.AlarmSoundService}

 */
@Singleton
class AlarmSoundServiceHelper @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val notificationHolder: Lazy<NotificationHolder>,
    private val context: Context
) {

    fun startAlarm(@RawRes sound: Int, reason: String) {
        aapsLogger.debug(LTag.CORE, "Starting alarm from $reason")
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                // The binder of the service that returns the instance that is created.
                val binder: AlarmSoundService.LocalBinder = service as AlarmSoundService.LocalBinder

                val alarmSoundService: AlarmSoundService = binder.getService()

                context.startForegroundService(getServiceIntent(context, sound))

                // This is the key: Without waiting Android Framework to call this method
                // inside Service.onCreate(), immediately call here to post the notification.
                alarmSoundService.startForeground(notificationHolder.get().notificationID, notificationHolder.get().notification)

                // Release the connection to prevent leaks.
                context.unbindService(this)
            }

            override fun onServiceDisconnected(name: ComponentName?) {
            }
        }

        try {
            context.bindService(getServiceIntent(context, sound), connection, Context.BIND_AUTO_CREATE)
        } catch (ignored: RuntimeException) {
            // This is probably a broadcast receiver context even though we are calling getApplicationContext().
            // Just call startForegroundService instead since we cannot bind a service to a
            // broadcast receiver context. The service also have to call startForeground in
            // this case.
            context.startForegroundService(getServiceIntent(context, sound))
        }
    }

    fun stopAlarm(reason: String) {
        aapsLogger.debug(LTag.CORE, "Stopping alarm from $reason")
        val alarm = Intent(context, AlarmSoundService::class.java)
        context.stopService(alarm)
    }

    private fun getServiceIntent(context: Context, @RawRes sound: Int): Intent {
        val alarm = Intent(context, AlarmSoundService::class.java)
        alarm.putExtra(AlarmSoundService.SOUND_ID, sound)
        return alarm
    }
}