package app.aaps.core.interfaces.source

import app.aaps.database.entities.GlucoseValue

interface NSClientSource {

    fun isEnabled(): Boolean
    fun detectSource(glucoseValue: GlucoseValue)
}