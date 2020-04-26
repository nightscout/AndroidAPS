package info.nightscout.androidaps.utils

import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Profiler @Inject constructor(val aapsLogger: AAPSLogger) {

    fun log(lTag: LTag, function: String, start: Long) {
        val msec = System.currentTimeMillis() - start
        aapsLogger.debug(lTag, ">>> $function <<< executed in $msec miliseconds")
    }
}