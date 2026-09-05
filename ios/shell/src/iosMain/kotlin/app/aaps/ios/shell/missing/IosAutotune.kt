package app.aaps.ios.shell.missing

import app.aaps.core.interfaces.autotune.Autotune
import app.aaps.core.interfaces.logging.AAPSLogger
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/**
 * Placeholder. A run does nothing and reports failure, so a caller sees "autotune did not work"
 * rather than "autotune finished and changed nothing" - the second would be a quiet lie about
 * insulin settings.
 *
 * Worth saying plainly: **autotune is not an iOS problem.** `AutotunePlugin` is arithmetic over
 * treatment history, and the only reason it is in androidMain is that it has not been moved. This
 * should be ported rather than implemented here, and this class deleted when it is.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class IosAutotune @Inject constructor(
    private val aapsLogger: AAPSLogger
) : Autotune {

    override var lastRunSuccess: Boolean = false
    override var calculationRunning: Boolean = false

    override suspend fun aapsAutotune(daysBack: Int, autoSwitch: Boolean, profileToTune: String, weekDays: BooleanArray?) {
        aapsLogger.notOnIosYet("Autotune.aapsAutotune")
        lastRunSuccess = false
    }

    override fun atLog(message: String) {
        aapsLogger.notOnIosYet("Autotune.atLog")
    }
}
