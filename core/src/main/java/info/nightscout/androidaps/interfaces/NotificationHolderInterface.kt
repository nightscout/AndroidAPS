package info.nightscout.androidaps.interfaces

import android.app.Notification

interface NotificationHolderInterface {
    val channelID : String
    val notificationID : Int
    var notification: Notification

}