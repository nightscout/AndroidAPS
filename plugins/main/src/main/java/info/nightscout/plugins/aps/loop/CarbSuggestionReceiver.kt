package info.nightscout.plugins.aps.loop

import android.content.Context
import android.content.Intent
import dagger.android.DaggerBroadcastReceiver
import info.nightscout.interfaces.aps.Loop
import javax.inject.Inject

class CarbSuggestionReceiver : DaggerBroadcastReceiver() {
    @Inject lateinit var loop: Loop

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val duration = intent.getIntExtra("ignoreDuration", 5)
        loop.disableCarbSuggestions(duration)
    }
}