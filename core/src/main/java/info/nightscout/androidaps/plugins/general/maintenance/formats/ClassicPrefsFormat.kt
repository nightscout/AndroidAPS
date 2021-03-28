package info.nightscout.androidaps.plugins.general.maintenance.formats

import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.core.R
import info.nightscout.androidaps.database.entities.UserEntry
import info.nightscout.androidaps.database.entities.UserEntry.*
import info.nightscout.androidaps.interfaces.ProfileFunction
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.DecimalFormatter
import info.nightscout.androidaps.utils.Translator
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.storage.Storage
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ClassicPrefsFormat @Inject constructor(
    private var resourceHelper: ResourceHelper,
    private var dateUtil: DateUtil,
    private var translator: Translator,
    private var profileFunction: ProfileFunction,
    private var storage: Storage
) : PrefsFormat {

    companion object {

        val FORMAT_KEY = "aaps_old"
    }

    override fun isPreferencesFile(file: File, preloadedContents: String?): Boolean {
        val contents = preloadedContents ?: storage.getFileContents(file)
        return contents.contains("units::" + Constants.MGDL) || contents.contains("units::" + Constants.MMOL) || contents.contains("language::") || contents.contains("I_understand::")
    }

    override fun savePreferences(file: File, prefs: Prefs, masterPassword: String?) {
        try {
            val contents = prefs.values.entries.joinToString("\n") { entry ->
                "${entry.key}::${entry.value}"
            }
            storage.putFileContents(file, contents)
        } catch (e: FileNotFoundException) {
            throw PrefFileNotFoundError(file.absolutePath)
        } catch (e: IOException) {
            throw PrefIOError(file.absolutePath)
        }
    }

    override fun loadPreferences(file: File, masterPassword: String?): Prefs {
        var lineParts: Array<String>
        val entries: MutableMap<String, String> = mutableMapOf()
        try {

            val rawLines = storage.getFileContents(file).split("\n")
            rawLines.forEach { line ->
                lineParts = line.split("::").toTypedArray()
                if (lineParts.size == 2) {
                    entries[lineParts[0]] = lineParts[1]
                }
            }

            return Prefs(entries, loadMetadata())

        } catch (e: FileNotFoundException) {
            throw PrefFileNotFoundError(file.absolutePath)
        } catch (e: IOException) {
            throw PrefIOError(file.absolutePath)
        }
    }

    override fun loadMetadata(contents: String?): PrefMetadataMap {
        val metadata: MutableMap<PrefsMetadataKey, PrefMetadata> = mutableMapOf()
        metadata[PrefsMetadataKey.FILE_FORMAT] = PrefMetadata(FORMAT_KEY, PrefsStatus.WARN, resourceHelper.gs(R.string.metadata_warning_outdated_format))
        return metadata
    }

    fun saveCsv(file: File, userEntries: List<UserEntry>) {
        try {
            val contents = UserEntriesToCsv(userEntries)
            storage.putFileContents(file, contents)
        } catch (e: FileNotFoundException) {
            throw PrefFileNotFoundError(file.absolutePath)
        } catch (e: IOException) {
            throw PrefIOError(file.absolutePath)
        }
    }

    fun UserEntriesToCsv(userEntries: List<UserEntry>): String {
        val userEntryHeader = resourceHelper.gs(R.string.ue_csv_header,
            csvString(R.string.ue_timestamp),
            csvString(R.string.date),
            csvString(R.string.ue_utc_offset),
            csvString(R.string.ue_action),
            csvString(R.string.eventtype),
            csvString(R.string.ue_source),
            csvString(R.string.careportal_note),
            csvString(R.string.ue_formated_string),
            csvString(R.string.event_time_label),
            csvString(Units.fromText(profileFunction.getUnits())),
            csvString(Units.G),
            csvString(Units.U),
            csvString(Units.U_H),
            csvString(Units.Percent),
            csvString(Units.H),
            csvString(Units.M),
            csvString(R.string.ue_none)
        ) + "\n"
        return userEntryHeader + userEntries.joinToString("\n") { entry ->
            var timestampRec = "" + entry.timestamp
            var dateTimestampRev = dateUtil.dateAndTimeAndSecondsString(entry.timestamp)
            var utcOffset = dateUtil.timeString(entry.utcOffset)
            var action = csvString(entry.action)
            var therapyEvent = ""
            var source = ""
            var note = csvString(entry.s)
            var formatedString = ""
            var timestamp = ""
            var bg = ""
            var g = ""
            var u = ""
            var uh = ""
            var percent = ""
            var h = ""
            var m = ""
            var other = ""

            for (v in entry.values) {
                when (v.unit) {
                    Units.Timestamp    -> timestamp = dateUtil.dateAndTimeAndSecondsString(v.lValue)
                    Units.TherapyEvent -> therapyEvent = if (therapyEvent == "") translator.translate(v.sValue) else therapyEvent + " / " + translator.translate(v.sValue)  //Todo update with XXXValueWithUnit
                    Units.Source       -> source = csvString(v.sValue)
                    Units.R_String     -> if (v.iValue != 0) {  //Formated string lValue is the number of parameters, up to 3
                        var rStringParam = v.lValue.toInt()
                        var tempString = ""
                        when (rStringParam) {   //
                            0 -> tempString = resourceHelper.gs(v.iValue)
                            1 -> tempString = resourceHelper.gs(v.iValue, entry.values[entry.values.indexOf(v)+1].value())
                            2 -> tempString = resourceHelper.gs(v.iValue, entry.values[entry.values.indexOf(v)+1].value(), entry.values[entry.values.indexOf(v)+2].value())
                            3 -> tempString = resourceHelper.gs(v.iValue, entry.values[entry.values.indexOf(v)+1].value(), entry.values[entry.values.indexOf(v)+2].value(), entry.values[entry.values.indexOf(v)+3].value())
                        }
                        formatedString = if (formatedString == "") tempString else  formatedString + " / " + tempString
                    }
                    Units.Mg_Dl         -> bg = if (profileFunction.getUnits()==Constants.MGDL) DecimalFormatter.to0Decimal(v.dValue) else DecimalFormatter.to1Decimal(v.dValue/Constants.MMOLL_TO_MGDL)
                    Units.Mmol_L        -> bg = if (profileFunction.getUnits()==Constants.MGDL) DecimalFormatter.to0Decimal(v.dValue*Constants.MMOLL_TO_MGDL) else DecimalFormatter.to1Decimal(v.dValue)
                    Units.G             -> g = v.iValue.toString()
                    Units.U             -> u = DecimalFormatter.to2Decimal(v.dValue)
                    Units.U_H           -> uh = DecimalFormatter.to2Decimal(v.dValue)
                    Units.Percent       -> percent = v.iValue.toString()
                    Units.H             -> h = v.iValue.toString()
                    Units.M             -> m = v.iValue.toString()
                    else                -> other = if (other == "") v.value().toString() else other + " / " + v.value().toString()
                }
            }
            therapyEvent = csvString(therapyEvent)
            formatedString = csvString(formatedString)
            other = csvString(other)
            timestampRec + ";" + dateTimestampRev + ";" + utcOffset + ";" + action + ";" + therapyEvent  + ";" + source  + ";" + note  + ";" + formatedString  + ";" + timestamp  + ";" + bg  + ";" + g  + ";" + u  + ";" + uh  + ";" + percent  + ";" + h  + ";" + m + ";" + other
        }
    }

    private fun saveString(id: Int): String = if (id != 0) resourceHelper.gs(id) else ""
    private fun csvString(action: Action): String = "\"" + translator.translate(action).replace("\"", "\"\"") + "\""
    private fun csvString(unit: Units): String = "\"" + translator.translate(unit).replace("\"", "\"\"") + "\""
    private fun csvString(id: Int): String = if (id != 0) "\"" + resourceHelper.gs(id).replace("\"", "\"\"") + "\"" else ""
    private fun csvString(s: String): String = if (s != "") "\"" + s.replace("\"", "\"\"") + "\"" else ""
}