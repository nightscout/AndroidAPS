package info.nightscout.implementation.profiling

import dagger.Reusable
import info.nightscout.annotations.OpenForTesting
import info.nightscout.interfaces.profiling.Profiler
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.logging.LTag
import javax.inject.Inject

@OpenForTesting
@Reusable
class ProfilerImpl @Inject constructor(val aapsLogger: AAPSLogger) : Profiler {

    override fun log(lTag: LTag, function: String, start: Long) {
        val milliseconds = System.currentTimeMillis() - start
        aapsLogger.debug(lTag, ">>> $function <<< executed in $milliseconds milliseconds")
    }
}