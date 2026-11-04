package app.aaps.core.interfaces.profiling

import app.aaps.core.interfaces.logging.LTag

interface Profiler {

    fun log(lTag: LTag, function: String, start: Long)
}