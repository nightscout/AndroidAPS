package app.aaps.interfaces.iob

interface GlucoseStatusProvider {

    val glucoseStatusData: GlucoseStatus?
    fun getGlucoseStatusData(allowOldData: Boolean = false): GlucoseStatus?
}