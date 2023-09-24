package app.aaps.interfaces.profiling

import app.aaps.interfaces.logging.LTag

interface Profiler {

    fun log(lTag: LTag, function: String, start: Long)
}