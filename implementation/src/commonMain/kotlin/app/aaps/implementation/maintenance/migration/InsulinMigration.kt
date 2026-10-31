package app.aaps.implementation.maintenance.migration

import app.aaps.core.data.configuration.Constants
import app.aaps.core.data.model.ICfg
import app.aaps.core.interfaces.insulin.InsulinType
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.sharedPreferences.KeyValueStore
import app.aaps.core.interfaces.utils.HardLimits
import dev.zacsweers.metro.Inject

/**
 * Which insulin the records that predate ICfg were delivered with.
 *
 * In v3 the insulin was a plugin and the DIA belonged to the profile. In v4 both are one thing, carried by
 * the record. The database migration to v33 marks every record it cannot fill with `insulinEndTime = -1`,
 * and this works out what to stamp onto those.
 *
 * ## Where the answer comes from, best first
 *
 * 1. **The record itself.** v3 wrote the insulin onto every profile switch - the plugin's label and peak
 *    plus `insulinEndTime = pureProfile.dia * 3600 * 1000` - and `migration32to33` now carries those three
 *    columns across. So most installs are answered by the row and nothing below runs.
 * 2. **The dead v3 preference names**, for the records the row cannot answer: boluses, which v3 never
 *    stamped, and any row whose own value was missing. [readLegacyInsulin] recovers the plugin selection and
 *    [harvestProfileDia] the DIA each local profile was using.
 * 3. **A guess**, when neither can say. Always reported to the user, because it is wrong IOB on historical
 *    records and it is one-way: the sentinel is consumed, so those rows are never revisited.
 *
 * ## Why this is not in `PreferenceMigrations`
 *
 * A preference migration MOVES a value the store already holds, and runs over an imported file as well as
 * over the device. This reads the store to fill a decision that is then written to the DATABASE, and there
 * is no database import in AAPS, so a fresh install plus import has no legacy rows to stamp. It is also a
 * pure read: nothing here writes, and `MainApp` removes the legacy names only once the value they carry is
 * safely in the insulin list.
 *
 * ## Why it is a class and not `MainApp` code
 *
 * It used to be three private methods on `MainApp`, which has no test and cannot have one. Everything else
 * in `doMigrations` was moved into `PreferenceMigrations` and covered; this half stayed behind untested, and
 * two defects lived there for months - a DIA that silently became the hard-limit maximum of 9 h, and the
 * two sources asked in the wrong order, so an install whose own record knew the answer got a
 * reconstruction anyway.
 */
@Inject
class InsulinMigration(
    private val aapsLogger: AAPSLogger,
    private val hardLimits: HardLimits,
    private val rh: TextResolver
) {

    /**
     * The insulin plugin a v3 install had selected.
     *
     * Falls back to [InsulinType.OREF_RAPID_ACTING] when no row is found, and that is NOT a guess. v3
     * declared that plugin `enableByDefault(true)` and wrote NO `ConfigBuilder_*INSULIN*` row at all until
     * the user changed the selection, so "no row" means rapid-acting. Checked on a real 3.4.2.1 install: a
     * fresh start has not one such key in its preferences, and Config Builder shows Rapid-Acting Oref as
     * the insulin in use. [LegacyInsulin.fromExplicitKey] records which of the two it was, because it
     * decides whether the DIA, if it cannot be found either, is worth telling the user about.
     *
     * Both spellings are checked: the legacy `ConfigBuilder_INSULIN_<X>_Enabled` and the
     * `ConfigBuilder_Enabled_INSULIN_<X>` the ConfigBuilder rename produces. So it does not matter whether
     * `PreferenceMigrations.configBuilder` has been past yet - only that this runs before
     * `PreferenceMigrations.insulinPlugins` deletes the names.
     */
    fun readLegacyInsulin(sp: KeyValueStore): LegacyInsulin {
        fun selected(plugin: String) =
            sp.getBoolean("ConfigBuilder_Enabled_INSULIN_$plugin", false) || sp.getBoolean("ConfigBuilder_INSULIN_${plugin}_Enabled", false)

        val explicit = when {
            selected("InsulinOrefRapidActingPlugin")      -> InsulinType.OREF_RAPID_ACTING
            selected("InsulinOrefUltraRapidActingPlugin") -> InsulinType.OREF_ULTRA_RAPID_ACTING
            selected("InsulinOrefFreePeakPlugin")         -> InsulinType.OREF_FREE_PEAK
            selected("InsulinLyumjevPlugin")              -> InsulinType.OREF_LYUMJEV
            else                                          -> null
        }
        val type = explicit ?: InsulinType.OREF_RAPID_ACTING
        // Free peak is the only one whose peak the user set, and 75 is the default the v3 key carried.
        val peakTime =
            if (type == InsulinType.OREF_FREE_PEAK) (sp.getInt("insulin_oref_peak", 75) * 60 * 1000).toLong()
            else type.insulinPeakTime
        return LegacyInsulin(nickname = rh.gs(type.label), peakTime = peakTime, fromExplicitKey = explicit != null)
    }

    /**
     * The DIA each v3 local profile was using, paired with the profile's name.
     *
     * Four names, because a profile field has been spelled two ways and the DIA key has not moved with the
     * rest: `LocalProfile_<n>_name` / `LocalProfile_<n>_dia` is the raw 3.3 spelling, and
     * `LocalProfile_name_<n>` / `LocalProfile_dia_<n>` is what the ConfigBuilder-era rename produced. No key
     * in this build owns a DIA any more - the insulin does - so both DIA spellings are collected for removal
     * rather than carried forward, and so is the raw `_name` that `PreferenceMigrations.localProfile`
     * deliberately copies without deleting.
     *
     * The caller removes [ProfileDiaHarvest.keysToRemove] only after the value they carry is in the insulin
     * list. Removing them as they are read put the only copy of those DIAs in memory, across a startup gap
     * that can take the process down, and a death in that gap lost them for good.
     */
    fun harvestProfileDia(sp: KeyValueStore): ProfileDiaHarvest {
        val indexToName = mutableMapOf<Int, String>()
        val indexToDia = mutableMapOf<Int, Double>()
        val keysToRemove = mutableListOf<String>()
        for ((key, value) in sp.getAll()) {
            val parts = key.split("_")
            if (key.startsWith(Constants.LOCAL_PROFILE + "_") && key.endsWith("_name"))
                parts.getOrNull(1)?.toIntOrNull()?.let { number ->
                    LegacyPreferenceValue.asString(value)?.let { name ->
                        indexToName[number] = name
                        keysToRemove += key
                    }
                }
            if (key.startsWith(Constants.LOCAL_PROFILE + "_name_"))
                parts.getOrNull(2)?.toIntOrNull()?.let { number ->
                    LegacyPreferenceValue.asString(value)?.let { indexToName[number] = it }
                }
            if (key.startsWith(Constants.LOCAL_PROFILE + "_") && key.endsWith("_dia"))
                parts.getOrNull(1)?.toIntOrNull()?.let { number ->
                    LegacyPreferenceValue.asDouble(value)?.let { dia ->
                        indexToDia[number] = dia
                        keysToRemove += key
                    }
                }
            if (key.startsWith(Constants.LOCAL_PROFILE + "_dia_"))
                parts.getOrNull(2)?.toIntOrNull()?.let { number ->
                    LegacyPreferenceValue.asDouble(value)?.let { dia ->
                        indexToDia[number] = dia
                        keysToRemove += key
                    }
                }
        }
        val nameToDia = indexToDia.mapNotNull { (index, dia) ->
            indexToName[index]?.let { name -> name to dia }
        }.toMap()
        return ProfileDiaHarvest(nameToDia, keysToRemove)
    }

    /**
     * The insulin to stamp onto the records that still carry the sentinel.
     *
     * [recordICfg] is the running record's own insulin, which after `migration32to33` is the v3 value and
     * therefore the right answer. It is consulted FIRST, which is the opposite of the old order: that asked
     * the legacy preferences whenever any DIA had been harvested, so an install whose row knew the answer
     * got a reconstruction anyway. It still has to be [ICfg.isUsable] - a row that carries the sentinel
     * reports a DIA of 0.0, and reading that back would write the sentinel over itself for ever.
     *
     * [activeProfileName] is the running record's `originalProfileName`, used to pick that profile's DIA out
     * of the harvest.
     */
    fun plan(recordICfg: ICfg?, legacy: LegacyInsulin?, harvest: ProfileDiaHarvest, activeProfileName: String?): InsulinMigrationPlan {
        if (recordICfg?.isUsable == true) return plan(recordICfg, InsulinMigrationPlan.Source.RECORD)
        // Null only if nothing read the store, which the start-up order rules out. Same treatment as an
        // install with no row: v3's own default.
        val insulin = legacy ?: LegacyInsulin(rh.gs(InsulinType.OREF_RAPID_ACTING.label), InsulinType.OREF_RAPID_ACTING.insulinPeakTime, fromExplicitKey = false)
        val dia = harvest.diaFor(activeProfileName)
        // Concentration is 1.0 by construction, not a guess: these records predate concentration entirely,
        // so 1.0 is the identity that leaves historical doses unscaled. The label is left blank on purpose -
        // the insulin list builds it from the nickname and the curve, so there is one naming rule.
        val iCfg = ICfg(
            insulinLabel = "",
            insulinEndTime = ((dia ?: hardLimits.diaRange().endInclusive) * 3600 * 1000).toLong(),
            insulinPeakTime = insulin.peakTime,
            concentration = 1.0
        ).also { it.insulinNickname = insulin.nickname }
        val source = when {
            dia != null               -> InsulinMigrationPlan.Source.LEGACY_KEYS
            insulin.fromExplicitKey   -> InsulinMigrationPlan.Source.GUESSED_DIA
            else                      -> InsulinMigrationPlan.Source.NO_LEGACY_DATA
        }
        return plan(iCfg, source)
    }

    private fun plan(iCfg: ICfg, source: InsulinMigrationPlan.Source): InsulinMigrationPlan {
        aapsLogger.info(LTag.CORE, "Insulin migration: ${iCfg.peak} min peak, ${iCfg.dia} h DIA, from $source")
        return InsulinMigrationPlan(iCfg, source)
    }
}

/**
 * The insulin plugin a v3 install had selected. Everything about it except the DIA, which the profile owned.
 *
 * [fromExplicitKey] is false when no ConfigBuilder row said so and v3's own default was taken instead. The
 * insulin is still right either way; the flag only says how much is left to guess if the DIA is missing too.
 */
data class LegacyInsulin(val nickname: String, val peakTime: Long, val fromExplicitKey: Boolean)

/** The DIA each v3 local profile was using, and the legacy names those values came from. */
data class ProfileDiaHarvest(val nameToDia: Map<String, Double>, val keysToRemove: List<String>) {

    /**
     * The DIA to use: the running profile's own, or the one value every harvested profile agrees on.
     *
     * The agreement step is what keeps the common cases off the guess. One local profile is the usual setup,
     * and several profiles sharing a DIA is the next most usual; in both the answer is certain even when the
     * running record's name cannot be matched - it was renamed, or it came from Nightscout. Without it those
     * installs took the hard-limit maximum of 9 h, silently, over their whole history.
     */
    fun diaFor(profileName: String?): Double? =
        profileName?.let { nameToDia[it] } ?: nameToDia.values.distinct().singleOrNull()
}

/** The insulin the migration decided on, and how sure it is. */
data class InsulinMigrationPlan(val iCfg: ICfg, val source: Source) {

    enum class Source {

        /** The record's own v3 insulin. Certain. */
        RECORD,

        /** Rebuilt from the v3 insulin plugin selection and a harvested profile DIA. Certain. */
        LEGACY_KEYS,

        /** The right insulin, but no DIA could be found for it, so the hard-limit maximum stands in. */
        GUESSED_DIA,

        /**
         * Neither an insulin row nor a DIA. v3's default insulin and the hard-limit DIA stand in, and
         * nothing here says this is a v3 install at all - a fresh install looks exactly like this, which is
         * why the caller leaves the insulin list alone on this one.
         */
        NO_LEGACY_DATA
    }

    /** True when the user has to be told, because part of [iCfg] was invented and will not be corrected later. */
    val isGuess: Boolean get() = source == Source.GUESSED_DIA || source == Source.NO_LEGACY_DATA
}
