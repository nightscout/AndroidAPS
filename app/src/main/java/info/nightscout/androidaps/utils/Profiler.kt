package info.nightscout.androidaps.utils

import info.nightscout.androidaps.annotations.OpenForTesting
import info.nightscout.shared.logging.AAPSLogger
import info.nightscout.shared.logging.LTag
import javax.inject.Inject
import javax.inject.Singleton

@OpenForTesting
@Singleton
class Profiler @Inject constructor(val aapsLogger: AAPSLogger) {

    fun log(lTag: LTag, function: String, start: Long) {
        val milliseconds = System.currentTimeMillis() - start
        aapsLogger.debug(lTag, ">>> $function <<< executed in $milliseconds milliseconds")
    }
}