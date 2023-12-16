package app.aaps.core.interfaces.source

import app.aaps.core.data.model.GV

interface NSClientSource {

    fun isEnabled(): Boolean
    fun detectSource(glucoseValue: GV)
}