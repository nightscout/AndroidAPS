package info.nightscout.plugins.general.overview.notifications

import android.content.Intent
import app.aaps.interfaces.rx.bus.RxBus
import app.aaps.interfaces.rx.events.EventDismissNotification
import dagger.android.DaggerIntentService
import javax.inject.Inject

class DismissNotificationService : DaggerIntentService(DismissNotificationService::class.simpleName) {

    @Inject lateinit var rxBus: RxBus

    override fun onHandleIntent(intent: Intent?) {
        intent?.let {
            rxBus.send(EventDismissNotification(intent.getIntExtra("alertID", -1)))
        }
    }
}