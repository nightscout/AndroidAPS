package app.aaps.core.interfaces.ui

import android.app.PendingIntent

/**
 * Supplies the "ignore carb suggestion" actions attached to the carbs-required notification.
 *
 * The Loop plugin used to build these itself with `Intent(context, CarbSuggestionReceiver::class)`.
 * That receiver field injects, and a members injector is generated Java, which a multiplatform module
 * cannot produce - see `_docs/KMP_IOS_FEASIBILITY.md`, under "Decisions taken". So the receiver lives
 * in :app now, and the plugin asks for the intent through this instead of naming the class.
 *
 * Typed on purpose: addressing the receiver by `ComponentName` string would work too, and would break
 * silently the first time someone renamed or moved it.
 */
interface CarbSuggestionActions {

    /**
     * A broadcast [PendingIntent] that silences carb suggestions for [minutes].
     *
     * @param requestCode must differ per action, or the intents collide and Android reuses the first
     */
    fun ignoreFor(minutes: Int, requestCode: Int): PendingIntent
}
