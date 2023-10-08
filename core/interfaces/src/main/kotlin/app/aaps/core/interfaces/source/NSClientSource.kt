package app.aaps.core.interfaces.source

import app.aaps.core.data.db.GV

interface NSClientSource {

    fun isEnabled(): Boolean
    fun detectSource(glucoseValue: GV)
}