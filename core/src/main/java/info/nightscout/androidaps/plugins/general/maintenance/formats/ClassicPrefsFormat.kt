package info.nightscout.androidaps.plugins.general.maintenance.formats

import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.core.R
import info.nightscout.androidaps.database.entities.UserEntry
import info.nightscout.androidaps.database.entities.UserEntry.*
import info.nightscout.androidaps.interfaces.ProfileFunction
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.DecimalFormatter
import info.nightscout.androidaps.utils.Translator
import info.nightscout.androidaps.utils.extensions.stringId
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
        val userEntryHeader = "Date;UTC Offset;Action;Note;Value;Unit\n"
        return userEntryHeader + userEntries.joinToString("\n") { entry ->
            if (entry.values.size > 0) {
                entry.values.joinToString("\n") { value ->
                    dateUtil.dateAndTimeAndSecondsString(entry.timestamp) + ";" +
                        dateUtil.timeString(entry.utcOffset) + ";" +
                        "\"" + resourceHelper.gs(entry.action.stringId()) + "\";" +
                        if (entry.s != "") {"\""+ entry.s.replace("\"", "\"\"") + "\";" } else { ";" } +
                        valueWithUnitToCsv(value) }
            } else {
                dateUtil.dateAndTimeAndSecondsString(entry.timestamp) + ";" +
                    dateUtil.timeString(entry.utcOffset) + ";" +
                    "\"" + resourceHelper.gs(entry.action.stringId()) + "\";" +
                    if (entry.s != "") {"\""+ entry.s.replace("\"", "\"\"") + "\";" } else { ";;" }
            }
        }
    }

    fun valueWithUnitToCsv(v: ValueWithUnit): String {
        return when (v.unit) {
            Units.Timestamp -> dateUtil.dateAndTimeAndSecondsString(v.lValue) + ";" + resourceHelper.gs(R.string.date)
            Units.CPEvent -> translator.translate(v.sValue) + ";"
            Units.R_String -> "\"" + resourceHelper.gs(v.iValue).replace("\"", "\"\"") + "\";"
            Units.Mg_Dl -> if (profileFunction.getUnits()==Constants.MGDL) DecimalFormatter.to0Decimal(v.dValue) + ";" + resourceHelper.gs(Units.Mg_Dl.stringId()) else DecimalFormatter.to1Decimal(v.dValue/Constants.MMOLL_TO_MGDL) + ";" + resourceHelper.gs(Units.Mmol_L.stringId())
            Units.Mmol_L -> if (profileFunction.getUnits()==Constants.MGDL) DecimalFormatter.to0Decimal(v.dValue*Constants.MMOLL_TO_MGDL) + ";" + resourceHelper.gs(Units.Mg_Dl.stringId()) else DecimalFormatter.to1Decimal(v.dValue) + ";" + resourceHelper.gs(Units.Mmol_L.stringId())
            Units.G -> v.iValue.toString() + ";" + resourceHelper.gs(Units.G.stringId())
            Units.U_H -> DecimalFormatter.to2Decimal(v.dValue) + ";" + resourceHelper.gs(Units.U_H.stringId())
            else -> if (v.sValue != "")  {"\""+ v.sValue.replace("\"", "\"\"") + if (!v.unit.stringId().equals(0)) "\";\"" + resourceHelper.gs(v.unit.stringId()).replace("\"", "\"\"")  + "\"" else "\";"}
            else if (v.dValue != 0.0 || v.iValue != 0) { v.value().toString() + if (!v.unit.stringId().equals(0)) ";" + resourceHelper.gs(v.unit.stringId()) else ";" }
            else ";"
        }
    }
}