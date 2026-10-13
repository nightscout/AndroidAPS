package app.aaps.plugins.aps.loop

import android.content.Context
import android.content.Intent
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import dagger.android.DaggerBroadcastReceiver
import javax.inject.Inject

class CarbSuggestionReceiver : DaggerBroadcastReceiver() {

    @Inject lateinit var loop: Loop
    @Inject lateinit var aapsLogger: AAPSLogger

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val duration = intent.getIntExtra("ignoreDuration", 5)
        aapsLogger.debug(LTag.CORE, "CarbSuggestion should be disabled for $duration minutes")
        loop.disableCarbSuggestions(duration)
    }
}