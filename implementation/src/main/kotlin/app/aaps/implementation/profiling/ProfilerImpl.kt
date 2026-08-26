package app.aaps.implementation.profiling

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.profiling.Profiler
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import javax.inject.Inject

@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
class ProfilerImpl @Inject constructor(val aapsLogger: AAPSLogger) : Profiler {

    override fun log(lTag: LTag, function: String, start: Long) {
        val milliseconds = System.currentTimeMillis() - start
        aapsLogger.debug(lTag, ">>> $function <<< executed in $milliseconds milliseconds")
    }
}