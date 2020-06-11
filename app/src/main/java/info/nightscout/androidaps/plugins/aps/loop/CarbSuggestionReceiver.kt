package info.nightscout.androidaps.plugins.aps.loop

import android.content.Context
import android.content.Intent
import dagger.android.DaggerBroadcastReceiver
import javax.inject.Inject

class CarbSuggestionReceiver : DaggerBroadcastReceiver() {
    @Inject lateinit var loopPlugin: LoopPlugin
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val duration = intent.getIntExtra("ignoreDuration", 5)
        loopPlugin.disableCarbSuggestions(duration)
    }
}