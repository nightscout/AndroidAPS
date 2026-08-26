package app.aaps.receivers

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.ui.CarbSuggestionActions
import app.aaps.core.objects.workflow.MetroBroadcastReceiver
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import javax.inject.Inject

/**
 * Moved here from :plugins:aps so that module could become multiplatform.
 *
 * Nothing about the receiver needed Android APIs that `androidMain` lacks - it was the field
 * injection. Dagger answers `@Inject lateinit` with a generated members injector written in Java, and
 * a multiplatform module has no Java compilation step, so the file would be generated and silently
 * dropped. See `_docs/KMP_IOS_FEASIBILITY.md`, under "Decisions taken".
 */
class CarbSuggestionReceiver : MetroBroadcastReceiver() {

    @Inject lateinit var loop: Loop
    @Inject lateinit var aapsLogger: AAPSLogger

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val duration = intent.getIntExtra(EXTRA_IGNORE_DURATION, 5)
        aapsLogger.debug(LTag.CORE, "CarbSuggestion should be disabled for $duration minutes")
        loop.disableCarbSuggestions(duration)
    }

    companion object {

        const val EXTRA_IGNORE_DURATION = "ignoreDuration"
    }
}

/**
 * Builds the intents for [CarbSuggestionReceiver], so LoopPlugin does not have to name a class that
 * no longer lives in its module.
 */
// Metro builds this; the @Binds in PersistentNotificationModule is gone.
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
class CarbSuggestionActionsImpl @Inject constructor(
    private val context: Context
) : CarbSuggestionActions {

    override fun ignoreFor(minutes: Int, requestCode: Int): PendingIntent {
        val intent = Intent(context, CarbSuggestionReceiver::class.java)
            .putExtra(CarbSuggestionReceiver.EXTRA_IGNORE_DURATION, minutes)
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }
}
