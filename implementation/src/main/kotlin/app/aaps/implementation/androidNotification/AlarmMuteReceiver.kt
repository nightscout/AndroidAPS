package app.aaps.implementation.androidNotification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.aaps.core.interfaces.ui.UiInteraction
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * Handles the "Mute" action on the background alarm notification: silences every audible alarm
 * (the looping [app.aaps.core.interfaces.notifications.AlarmSoundPlayer] plus the alarm
 * notifications) via [UiInteraction.stopAlarm], without the user having to open the alarm screen.
 * Primary use: the case where `ErrorActivity` could not be brought to the foreground (another app on
 * top) so the user's only lock-screen surface is the notification.
 *
 * Reaches [UiInteraction] through a Hilt entry point since a manifest [BroadcastReceiver] is not
 * itself injected.
 */
class AlarmMuteReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        EntryPointAccessors.fromApplication(context.applicationContext, Entry::class.java)
            .uiInteraction()
            .stopAlarm("Notification mute action")
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Entry {

        fun uiInteraction(): UiInteraction
    }
}
