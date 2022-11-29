package info.nightscout.interfaces.profiling

import info.nightscout.rx.logging.LTag

interface Profiler {

    fun log(lTag: LTag, function: String, start: Long)
}