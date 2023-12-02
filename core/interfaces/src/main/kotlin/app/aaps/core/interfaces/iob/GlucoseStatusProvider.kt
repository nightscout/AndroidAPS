package app.aaps.core.interfaces.iob

import app.aaps.core.data.iob.GlucoseStatus

interface GlucoseStatusProvider {

    val glucoseStatusData: GlucoseStatus?
    fun getGlucoseStatusData(allowOldData: Boolean = false): GlucoseStatus?
}