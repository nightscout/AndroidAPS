package info.nightscout.androidaps.plugins.general.overview.notifications

import android.content.Intent
import dagger.android.DaggerIntentService
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.general.overview.events.EventDismissNotification
import javax.inject.Inject

class DismissNotificationService : DaggerIntentService(DismissNotificationService::class.simpleName) {
    @Inject lateinit var rxBus: RxBusWrapper

    override fun onHandleIntent(intent: Intent?) {
        intent?.let {
            rxBus.send(EventDismissNotification(intent.getIntExtra("alertID", -1)))
        }
    }
}