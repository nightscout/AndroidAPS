package info.nightscout.implementation.profiling

import app.aaps.annotations.OpenForTesting
import app.aaps.interfaces.logging.AAPSLogger
import app.aaps.interfaces.logging.LTag
import app.aaps.interfaces.profiling.Profiler
import dagger.Reusable
import javax.inject.Inject

@OpenForTesting
@Reusable
class ProfilerImpl @Inject constructor(val aapsLogger: AAPSLogger) : Profiler {

    override fun log(lTag: LTag, function: String, start: Long) {
        val milliseconds = System.currentTimeMillis() - start
        aapsLogger.debug(lTag, ">>> $function <<< executed in $milliseconds milliseconds")
    }
}