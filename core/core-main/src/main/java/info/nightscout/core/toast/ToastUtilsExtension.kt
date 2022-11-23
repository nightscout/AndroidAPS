package info.nightscout.core.toast

import android.content.Context
import info.nightscout.core.events.EventNewNotification
import info.nightscout.core.ui.toast.ToastUtils
import info.nightscout.interfaces.notifications.Notification
import info.nightscout.rx.bus.RxBus

fun ToastUtils.showToastAdNotification(
    ctx: Context?, rxBus: RxBus,
    string: String?, soundID: Int
) {
    showToastInUiThread(ctx, string)
    playSound(ctx, soundID)
    val notification = Notification(Notification.TOAST_ALARM, string!!, Notification.URGENT)
    rxBus.send(EventNewNotification(notification))
}

