package app.aaps.plugins.main.general.overview.notifications.receivers

import android.content.Context
import android.content.Intent
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventDismissNotification
import dagger.android.DaggerBroadcastReceiver
import javax.inject.Inject

class DismissNotificationReceiver : DaggerBroadcastReceiver() {
    companion object {

        const val ACTION = "app.aaps.plugins.main.general.overview.notifications.receivers.DismissNotificationReceiver"
    }

    @Inject lateinit var rxBus: RxBus

    override fun onReceive(context: Context?, intent: Intent?) {
        super.onReceive(context, intent)
        rxBus.send(EventDismissNotification(intent?.getIntExtra("alertID", -1) ?: -1))
    }
}