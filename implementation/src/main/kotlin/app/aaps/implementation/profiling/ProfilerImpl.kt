package app.aaps.implementation.profiling

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.profiling.Profiler
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProfilerImpl @Inject constructor(val aapsLogger: AAPSLogger) : Profiler {

    override fun log(lTag: LTag, function: String, start: Long) {
        val milliseconds = System.currentTimeMillis() - start
        aapsLogger.debug(lTag, ">>> $function <<< executed in $milliseconds milliseconds")
    }
}