package app.aaps.plugins.configuration.configBuilder

import app.aaps.core.interfaces.configuration.AppExit
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/**
 * Refuses to end the app, because on iOS that is the correct answer.
 *
 * Apple's guidance is explicit that an app must not terminate itself, and a self-terminated app is
 * recorded as a crash. `exitProcess` exists on Kotlin/Native and would "work", which is exactly why
 * this class is here rather than the obvious one-liner: the tempting implementation ships a crash.
 *
 * So nothing is exited and the refusal is logged at error, leaving the app running and usable. The
 * caller has already sent `EventAppExit` and written the user entry by this point, which is the part
 * that is shared; only the last step is declined.
 *
 * Written from the Windows side so that moving `ConfigBuilderImpl` to commonMain did not leave the
 * iOS graph without a binding. If iOS should instead show something ("please close AAPS yourself"),
 * that belongs here and this is the place to put it.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class IosAppExit @Inject constructor(
    private val aapsLogger: AAPSLogger
) : AppExit {

    override fun exit(launchAgain: Boolean) {
        aapsLogger.error(LTag.CORE, "iOS apps may not terminate themselves, so AAPS stays running (restart asked: $launchAgain)")
    }
}
