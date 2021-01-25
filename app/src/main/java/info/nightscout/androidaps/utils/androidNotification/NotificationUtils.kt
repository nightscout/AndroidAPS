package info.nightscout.androidaps.utils.androidNotification

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.TaskStackBuilder
import info.nightscout.androidaps.MainActivity

fun openAppIntent(context: Context): PendingIntent? = TaskStackBuilder.create(context).run {
    addParentStack(MainActivity::class.java)
    addNextIntent(Intent(context, MainActivity::class.java))
    getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT)
}