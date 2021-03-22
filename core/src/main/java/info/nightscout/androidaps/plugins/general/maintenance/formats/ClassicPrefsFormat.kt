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
        val userEntryHeader = resourceHelper.gs(R.string.ue_csv_header) + "\n"
        return userEntryHeader + userEntries.joinToString("\n") { entry ->
            if (entry.values.size > 0) {
                entry.values.joinToString("\n") { value ->
                    entry.timestamp.toString() + ";" +
                        dateUtil.dateAndTimeAndSecondsString(entry.timestamp) + ";" +
                        dateUtil.timeString(entry.utcOffset) + ";" +
                        csvString(entry.action) + ";" +
                        csvString(entry.s) + ";" +
                        valueWithUnitToCsv(value)
                }
            } else {
                entry.timestamp.toString() + ";" +
                    dateUtil.dateAndTimeAndSecondsString(entry.timestamp) + ";" +
                    dateUtil.timeString(entry.utcOffset) + ";" +
                    csvString(entry.action) + ";" +
                    csvString(entry.s) + ";;"
            }
        }
    }

    fun valueWithUnitToCsv(v: ValueWithUnit): String {
        return when (v.unit) {
            Units.Timestamp    -> dateUtil.dateAndTimeAndSecondsString(v.lValue) + ";" + csvString(R.string.date)
            Units.TherapyEvent -> csvString(translator.translate(v.sValue)) + ";"
            Units.R_String     -> if (v.lValue.toInt() == 0) csvString(v.iValue) + ";" else ";"                //If lValue > 0 it's a formated string, so hidden for
            Units.Mg_Dl         -> if (profileFunction.getUnits()==Constants.MGDL) DecimalFormatter.to0Decimal(v.dValue) + ";" + csvString(Units.Mg_Dl) else DecimalFormatter.to1Decimal(v.dValue/Constants.MMOLL_TO_MGDL) + ";" + csvString(Units.Mmol_L)
            Units.Mmol_L        -> if (profileFunction.getUnits()==Constants.MGDL) DecimalFormatter.to0Decimal(v.dValue*Constants.MMOLL_TO_MGDL) + ";" + csvString(Units.Mg_Dl) else DecimalFormatter.to1Decimal(v.dValue) + ";" + csvString(Units.Mmol_L)
            Units.U_H, Units.U  -> DecimalFormatter.to2Decimal(v.dValue) + ";" + csvString(v.unit)
            Units.G, Units.M, Units.H, Units.Percent
                                -> v.iValue.toString() + ";" + csvString(v.unit)
            else                -> if (v.sValue != "")  { csvString(v.sValue) +  ";" + csvString(v.unit)}
                                    else if (v.iValue != 0) { v.iValue.toString() + ";" + csvString(v.unit)}
                                    else ";"
        }
    }

    private fun csvString(action: Action): String = "\"" + translator.translate(action.name).replace("\"", "\"\"") + "\""
    private fun csvString(unit: Units): String = "\"" + translator.translate(unit.name).replace("\"", "\"\"") + "\""
    private fun csvString(id: Int): String = if (id != 0) "\"" + resourceHelper.gs(id).replace("\"", "\"\"") + "\"" else ""
    private fun csvString(s: String): String = if (s != "") "\"" + s.replace("\"", "\"\"") + "\"" else ""
}