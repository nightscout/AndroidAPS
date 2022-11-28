package info.nightscout.interfaces.source

import info.nightscout.database.entities.GlucoseValue

interface NSClientSource {
    fun detectSource(glucoseValue: GlucoseValue)
}