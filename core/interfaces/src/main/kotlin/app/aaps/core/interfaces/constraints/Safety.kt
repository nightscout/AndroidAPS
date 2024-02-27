package app.aaps.core.interfaces.constraints

import app.aaps.core.interfaces.configuration.ConfigExportImport

interface Safety : ConfigExportImport {

    fun ageEntries(): Array<CharSequence>
    fun ageEntryValues(): Array<CharSequence>
}