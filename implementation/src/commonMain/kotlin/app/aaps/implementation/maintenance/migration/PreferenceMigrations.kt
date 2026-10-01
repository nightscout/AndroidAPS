package app.aaps.implementation.maintenance.migration

import app.aaps.core.data.configuration.Constants
import app.aaps.core.data.model.RM
import app.aaps.core.data.model.TT
import app.aaps.core.data.model.TTPreset
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.sharedPreferences.KeyValueStore
import app.aaps.core.interfaces.tempTargets.toJson
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.BooleanComposedKey
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.LongComposedKey
import app.aaps.core.keys.ProfileComposedBooleanKey
import app.aaps.core.keys.ProfileComposedStringKey
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.StringNonKey
import app.aaps.core.keys.UnitDoubleKey
import dev.zacsweers.metro.Inject

/**
 * The preference migrations, as an ordered list of functions over the store they are given.
 *
 * ## Why they take the store
 *
 * These used to be the body of `MainApp.doMigrations()` and ran only against the live store on start
 * up. That was enough while an import wrote every name from the file into the store raw, because the
 * next start then converted them. It is not enough now: `PreferenceImportApplier` resolves each name
 * from the file and drops the ones it does not know, so an old backup's names never reach the store
 * for the start-up pass to find. The user loses their profiles, their plugin selection, their
 * objectives and their loop mode.
 *
 * Taking the store as a parameter is the whole change. Start up passes the real store; an import
 * passes one built over the file. Same functions, same order, one copy of the rules - which matters
 * because a second copy drifts, and this code has an example: the OpenAPSSMBDynamicISF migration
 * wrote `ConfigBuilder_APS_OpenAPSSMB_Enabled` while `ConfigBuilderImpl.composedKeyFor` built
 * `APS_OpenAPSSMBPlugin`. The two spellings never met and it did nothing from January 2024 until
 * somebody read it.
 *
 * ## A migration may do more than move a key
 *
 * [apsMode] writes a `RunningMode` row as well as consuming its key, and that is deliberate: restoring
 * the loop mode from a backup IS the migration. A function is free to do whatever its migration needs.
 *
 * The one thing every function must do is read and write through the `sp` it was handed, and nothing
 * else. A read of `preferences` or a write to the live store would touch THIS phone while processing
 * a file, and would do it identically on both paths so no test would show it.
 *
 * ## Order
 *
 * The list order is the execution order, and each function reads the store as the ones before it left
 * it. That is what makes this a chain and not a set: the order is the order the code changes happened
 * in, and every function writes the names of ITS OWN version, leaving the later ones to carry them
 * forward.
 *
 * [dynamicIsfRetired] is the example. It retired a plugin in January 2024, so it writes the
 * `ConfigBuilder_<TYPE>_<Class>_Enabled` name that build used, and [configBuilder] - the rename that
 * came later - turns it into the composed name, like any other row of that era. Writing the composed
 * name there directly would jump ahead of the chain, and [configBuilder] would then arrive with the
 * old value and overwrite it.
 *
 * So a new migration goes at the END of the list, and writes whatever the build it belongs to wrote.
 *
 * Every function is guarded by its own data - it does nothing when its names are absent - so running
 * the whole list every time is correct and there is nothing to record or skip.
 *
 * ## What is NOT here
 *
 * What stays in `MainApp` is only what would misbehave against a file, and the line is always the same
 * one: a migration MOVES a value the store already holds, it never INVENTS one.
 *
 * - the simple-mode seed, which writes a value derived from no legacy key. `simple_mode` feeds
 *   `PreferencesImpl.calculatedDefaultValue`, so inventing one changes the effective value of a whole
 *   family of settings, remote control among them.
 * - the seeding half of the temp target presets. [tempTargetPresets] here rebuilds the document from
 *   the old keys when they are present; `MainApp` still creates three factory presets when they are
 *   not, which is new-install bootstrap and would write invented presets over a user's real ones.
 * - the insulin label and peak derivation, and the `LocalProfile_<n>_name` / `_dia` harvest. Both fill
 *   `MainApp` fields that `dataMigrations()` consumes much later, so they are about this app's
 *   start-up sequence rather than about the store. Neither is needed on an import: there is no
 *   database import in AAPS, so a fresh install plus import has no legacy rows to stamp.
 */
@Inject
class PreferenceMigrations(
    private val aapsLogger: AAPSLogger,
    private val config: Config,
    private val persistenceLayer: PersistenceLayer,
    private val dateUtil: DateUtil,
    private val profileUtil: ProfileUtil
) {

    /**
     * Runs every migration, in order, against [sp].
     *
     * Each function scans the store itself, rather than sharing one snapshot taken up front, so it
     * sees what the functions before it wrote. `getAll()` answers a copy on both stores, so a function
     * can write while it walks its own scan.
     */
    suspend fun migrate(sp: KeyValueStore) {
        overviewMarks(sp)
        dynamicIsfRetired(sp)
        dynIsfAdjust(sp)
        smsOtpPassword(sp)
        activityMonitor(sp)
        widgetUseBlack(sp)
        objectives(sp)
        configBuilder(sp)
        localProfile(sp)
        insulinPlugins(sp)
        tidepool(sp)
        apsMode(sp)
        tempTargetPresets(sp)
    }

    /**
     * The insulin curve stopped being a plugin, so its ConfigBuilder rows are dead keys.
     *
     * Both spellings are removed - the legacy `ConfigBuilder_INSULIN_<X>_Enabled` and the
     * `ConfigBuilder_Enabled_INSULIN_<X>` that [configBuilder] renames it to. Without this on the
     * import path an old backup would restore a plugin-enabled row for an insulin plugin this build
     * does not have: inert, because `applyConfiguration()` only looks at plugins that exist, but
     * litter that the import itself created.
     *
     * `MainApp` reads these names before the migrations run, to work out which insulin the old records
     * were delivered with. It checks both spellings for exactly that reason, so this may delete them
     * without caring whether [configBuilder] has been past yet.
     */
    private fun insulinPlugins(sp: KeyValueStore) {
        val anyPresent = INSULIN_PLUGINS.any { plugin ->
            sp.contains("ConfigBuilder_INSULIN_${plugin}_Enabled") || sp.contains("ConfigBuilder_Enabled_INSULIN_$plugin")
        }
        if (!anyPresent) return
        INSULIN_PLUGINS.forEach { plugin ->
            sp.remove("ConfigBuilder_INSULIN_${plugin}_Enabled")
            sp.remove("ConfigBuilder_INSULIN_${plugin}_Visible")
            sp.remove("ConfigBuilder_Enabled_INSULIN_$plugin")
        }
        sp.remove("insulin_oref_peak")
    }

    /**
     * The three temp target presets became one JSON document, so this rebuilds it from the old keys.
     *
     * Only the MIGRATING half is here. The original also SEEDS three factory presets when it finds no
     * old keys, because on a device it doubles as new-install bootstrap - and over a file that would
     * write presets the user never had, on top of the ones they do have. That half stays in `MainApp`
     * and this one does nothing when the old keys are absent.
     *
     * The target is decoded with [ProfileUtil.convertToMgdlDetect], and `units` is deliberately NOT
     * consulted, because `units` does not say what these numbers mean.
     *
     * In 3.3 these were `UnitDoubleKey`s, and `PreferencesImpl` stored them like this:
     *
     * ```
     * get(key: UnitDoublePreferenceKey) = profileUtil.valueInCurrentUnitsDetect(sp.getDouble(key.key, …))
     * put(key: UnitDoublePreferenceKey, value) = sp.putDouble(key.key, value)   // raw, no conversion
     * ```
     *
     * So the stored number carries whatever units the user happened to be in WHEN THEY LAST SAVED IT,
     * and the way back out is a magnitude test (`value >= 36` means it is already mg/dL). Our own
     * `PreferencesImpl.refreshUnitDoubleFlows` still says the same thing: "changing the units changes
     * what every unit-double READS AS, without any of them being written".
     *
     * Reading `units` instead looks exact and is wrong, on an ordinary history rather than a broken
     * file: set eating-soon to 100 while on mg/dL, switch the display to mmol/L, and the store holds
     * `eatingsoon_target = 100.0` beside `units = mmol` for ever, because the switch writes nothing.
     * 3.3 read that back as 100 mg/dL. Multiplying by 18 makes it 1801 mg/dL.
     *
     * The magnitude test is exact here rather than a guess: this key's own bounds are 72-160 mg/dL,
     * which is 4.0-8.9 mmol/L, so a real value never comes near 36 from either side.
     */
    private fun tempTargetPresets(sp: KeyValueStore) {
        // Zero is not a setting, it is a value 3.3 could leave behind. Remove it so the default wins.
        val beforeCleanup = sp.getAll()
        PRESETS.forEach { preset ->
            if (LegacyPreferenceValue.asLong(beforeCleanup[preset.durationName]) == 0L) sp.remove(preset.durationName)
            if (LegacyPreferenceValue.asDouble(beforeCleanup[preset.targetName]) == 0.0) sp.remove(preset.targetName)
        }

        // A client adopts the presets from the master over the sync channel; writing here would push a
        // client→master round-trip. The master migrates and publishes, the client follows.
        if (config.AAPSCLIENT) return
        // Already a document, so there is nothing to rebuild.
        if (sp.getString(TEMP_TARGET_PRESETS, "").let { it.isNotEmpty() && it != "[]" }) return
        if (!sp.contains(PRESETS.first().targetName)) return

        // Read again, so the zeroes removed just above are gone rather than taken for real values.
        val values = sp.getAll()
        val presets = PRESETS.mapNotNull { preset ->
            val target = LegacyPreferenceValue.asDouble(values[preset.targetName]) ?: return@mapNotNull null
            val minutes = LegacyPreferenceValue.asLong(values[preset.durationName]) ?: preset.defaultMinutes
            TTPreset(
                id = preset.id,
                reason = preset.reason,
                targetValue = profileUtil.convertToMgdlDetect(target),
                duration = minutes * 60L * 1000L,
                isDeletable = false
            )
        }
        if (presets.isEmpty()) return skip(PRESETS.first().targetName, "no preset could be read")
        sp.putString(TEMP_TARGET_PRESETS, presets.toJson())
    }

    /** A zero mark is not a setting, it is a value 3.3 could leave behind. Remove it so the default wins. */
    private fun overviewMarks(sp: KeyValueStore) {
        val values = sp.getAll()
        listOf(UnitDoubleKey.OverviewLowMark.key, UnitDoubleKey.OverviewHighMark.key).forEach { name ->
            if (LegacyPreferenceValue.asDouble(values[name]) == 0.0) sp.remove(name)
        }
    }

    /**
     * The plugin the user had is gone, so turn its replacement on.
     *
     * This wrote the raw name `ConfigBuilder_APS_OpenAPSSMB_Enabled`, which [configBuilder] turns into
     * the argument `APS_OpenAPSSMB` - while `ConfigBuilderImpl.composedKeyFor` builds
     * `APS_OpenAPSSMBPlugin`, from `PluginType.name + "_" + the class simple name`. The class name was
     * missing its `Plugin` suffix, the two spellings never met, and this did nothing from January 2024:
     * someone coming from a pre-2024 install with DynamicISF enabled was left with no APS plugin turned
     * on at all.
     *
     * The name is spelled in full now, and still in the `ConfigBuilder_<TYPE>_<Class>_Enabled` shape
     * the 2024 build wrote, so [configBuilder] carries it forward like any other row of that era.
     * Writing the composed name here instead would jump ahead of the chain: [configBuilder] would then
     * arrive with the legacy `ConfigBuilder_APS_OpenAPSSMBPlugin_Enabled`, which is `false` because the
     * user had DynamicISF selected rather than plain SMB, and overwrite it.
     *
     * Dropping the two DynamicISF names before [configBuilder] scans also keeps it from composing a
     * `ConfigBuilder_Enabled_APS_OpenAPSSMBDynamicISFPlugin` row for a plugin this build does not have.
     *
     * The client gating is left exactly as it was.
     */
    private fun dynamicIsfRetired(sp: KeyValueStore) {
        if (!sp.getBoolean("ConfigBuilder_APS_OpenAPSSMBDynamicISFPlugin_Enabled", false)) return
        sp.remove("ConfigBuilder_APS_OpenAPSSMBDynamicISFPlugin_Enabled")
        sp.remove("ConfigBuilder_APS_OpenAPSSMBDynamicISFPlugin_Visible")
        sp.putBoolean("ConfigBuilder_APS_OpenAPSSMBPlugin_Enabled", true)
        if (!config.AAPSCLIENT) sp.putBoolean(BooleanKey.ApsUseDynamicSensitivity.key, true)
    }

    /**
     * The adjustment factor was a text preference and is an Int key now, under the SAME stored name.
     *
     * So this is a type conversion in place, not a rename. Read through [LegacyPreferenceValue] rather
     * than `sp.getInt`: the store is untyped, and a value written by an older build can be text that
     * `getInt` would answer the caller's default for.
     */
    private fun dynIsfAdjust(sp: KeyValueStore) {
        if (config.AAPSCLIENT) return
        val name = IntKey.ApsDynIsfAdjustmentFactor.key
        val stored = sp.getAll()[name] ?: return
        if (stored is Int) return
        val value = LegacyPreferenceValue.asDouble(stored)
            ?: return skip(name, "expected a number, found ${LegacyPreferenceValue.describeType(stored)}")
        if (value != 0.0) sp.putInt(name, value.toInt())
    }

    /** A master password change once wrote the whole password here. Anything that long is not an OTP. */
    private fun smsOtpPassword(sp: KeyValueStore) {
        val name = StringKey.SmsOtpPassword.key
        if (sp.contains(name) && sp.getString(name, "").length > 10) sp.putString(name, "")
    }

    /** `Monitor_<activity>_total` and friends become `Monitor_total_<activity>`. */
    private fun activityMonitor(sp: KeyValueStore) {
        sp.getAll().forEach { (name, value) ->
            if (!name.startsWith("Monitor")) return@forEach
            val destination = when {
                name.endsWith("total")   -> LongComposedKey.ActivityMonitorTotal
                name.endsWith("resumed") -> LongComposedKey.ActivityMonitorResumed
                name.endsWith("start")   -> LongComposedKey.ActivityMonitorStart
                else                     -> return@forEach
            }
            moveLong(sp, name, value, name.split("_").getOrNull(1), destination.key)
        }
    }

    /**
     * `appwidget_use_black_<id>` becomes `widget_use_black_<id>`.
     *
     * The key was moved off the `appwidget_` prefix because it sat inside `IntComposedKey.WidgetOpacity`
     * and a Boolean could be read as an Int - see `ComposedKeyPrefixTest`. The match is anchored on
     * purpose: a bare `startsWith("appwidget_")` would also take `appwidget_<id>`, which IS the opacity.
     *
     * The old version removed the legacy name OUTSIDE the null check, so a value it could not read was
     * logged as skipped and then deleted anyway - the one migration here that destroyed what it failed
     * to convert. It now leaves it, like every other one.
     */
    private fun widgetUseBlack(sp: KeyValueStore) {
        sp.getAll().forEach { (name, value) ->
            val id = name.removePrefix("appwidget_use_black_")
            if (id == name || id.toIntOrNull() == null) return@forEach
            val useBlack = LegacyPreferenceValue.asBoolean(value)
            if (useBlack == null) skip(name, "expected true or false, found ${LegacyPreferenceValue.describeType(value)}")
            else {
                sp.putBoolean(BooleanComposedKey.WidgetUseBlack.composeKey(id.toInt()), useBlack)
                sp.remove(name)
            }
        }
    }

    /**
     * `Objectives_<name>_started` becomes `Objectives_started_<name>`.
     *
     * The destinations are written as literal names because `ObjectivesLongComposedKey` lives in
     * `:plugins:constraints`, which `:implementation` cannot depend on - and moving plugin keys into
     * `:core:keys` so a migration can name them would fill that module with other modules' keys, one
     * migration at a time. `PreferenceMigrationRoundTripTest` in `:app`, where every module is visible,
     * asserts these names still resolve.
     */
    private fun objectives(sp: KeyValueStore) {
        sp.getAll().forEach { (name, value) ->
            if (!name.startsWith("Objectives_")) return@forEach
            val destination = when {
                name.endsWith("_started")      -> "Objectives_started_"
                name.endsWith("_accomplished") -> "Objectives_accomplished_"
                else                           -> return@forEach
            }
            moveLong(sp, name, value, name.split("_").getOrNull(1), destination)
        }
    }

    /**
     * `ConfigBuilder_<TYPE>_<Class>_Enabled` becomes `ConfigBuilder_Enabled_<TYPE>_<Class>`.
     *
     * The argument is two parts because that is what `ConfigBuilderImpl.composedKeyFor` builds -
     * `PluginType.name + "_" + the plugin class simple name` - so a name with fewer is not one of ours.
     * The old guard was `parts.size > 2`, which let a three-part name through and composed a plugin id
     * of `PUMP_Enabled`. `_Visible` is dropped: fragment visibility is not tracked any more.
     */
    private fun configBuilder(sp: KeyValueStore) {
        sp.getAll().forEach { (name, value) ->
            if (!name.startsWith("ConfigBuilder_")) return@forEach
            if (name.endsWith("_Visible")) return@forEach sp.remove(name)
            if (!name.endsWith("_Enabled")) return@forEach
            val parts = name.split("_")
            if (parts.size <= 3) return@forEach skip(name, "the name does not have a plugin type and class")
            val enabled = LegacyPreferenceValue.asBoolean(value)
            if (enabled == null) skip(name, "expected true or false, found ${LegacyPreferenceValue.describeType(value)}")
            else {
                sp.putBoolean(BooleanComposedKey.ConfigBuilderEnabled.composeKey(parts[1] + "_" + parts[2]), enabled)
                sp.remove(name)
            }
        }
    }

    /**
     * `LocalProfile_<n>_isf` becomes `LocalProfile_isf_<n>`, and the same for the rest of a profile.
     *
     * Two things here are not like the others.
     *
     * The five schedules go through [LegacyPreferenceValue.asJsonArrayText], not `asString`. Over the
     * live store `asString` refuses a value that is not text, so a number could never be taken for a
     * schedule; every value in a file IS text, so that guard stops nothing there - and these names hold
     * the insulin sensitivity, carb ratio, basal and target schedules.
     *
     * The raw `_name` key is written across but NOT removed. `MainApp` still reads it with `_dia` to
     * build the name-to-DIA map `dataMigrations()` needs; removing it here would strand those values
     * and stamp historical records with a substituted insulin.
     *
     * The index is parsed strictly. `SafeParse.stringToInt`, which the old version used, answers 0 for
     * anything it cannot read, so a malformed name quietly overwrote profile 0.
     */
    private fun localProfile(sp: KeyValueStore) {
        sp.getAll().forEach { (name, value) ->
            if (!name.startsWith(LOCAL_PROFILE_PREFIX)) return@forEach
            val index = name.removePrefix(LOCAL_PROFILE_PREFIX).substringBefore('_', "").toIntOrNull() ?: return@forEach
            when (val field = name.substringAfterLast('_', "")) {
                "mgdl" -> {
                    val mgdl = LegacyPreferenceValue.asBoolean(value)
                    if (mgdl == null) skip(name, "expected true or false, found ${LegacyPreferenceValue.describeType(value)}")
                    else {
                        sp.putBoolean(ProfileComposedBooleanKey.LocalProfileNumberedMgdl.composeKey(index), mgdl)
                        sp.remove(name)
                    }
                }

                "name" -> {
                    val profileName = LegacyPreferenceValue.asString(value)
                    if (profileName == null) skip(name, "expected a name, found ${LegacyPreferenceValue.describeType(value)}")
                    // Deliberately not removed - see the note above.
                    else sp.putString(ProfileComposedStringKey.LocalProfileNumberedName.composeKey(index), profileName)
                }

                in SCHEDULE_FIELDS -> {
                    val schedule = LegacyPreferenceValue.asJsonArrayText(value)
                    if (schedule == null) skip(name, "expected a schedule array, found ${LegacyPreferenceValue.describeType(value)}")
                    else {
                        sp.putString(SCHEDULE_FIELDS.getValue(field).composeKey(index), schedule)
                        sp.remove(name)
                    }
                }
            }
        }
    }

    /**
     * Tidepool moved from a username and password to OAuth2.
     *
     * The three OAuth names are cleared as well, to force a fresh sign in - they are current, exportable
     * keys, so this only fires when the legacy pair is present, which is what makes it safe to run over
     * a file that carries a working session.
     */
    private fun tidepool(sp: KeyValueStore) {
        if (!sp.contains("tidepool_username") && !sp.contains("tidepool_password")) return
        listOf(
            "tidepool_username", "tidepool_password", "tidepool_test_login",
            "tidepool_auth_state", "tidepool_service_configuration", "tidepool_subscription_id"
        ).forEach(sp::remove)
    }

    /**
     * The loop mode became a database record, so this migration writes one.
     *
     * A function is free to do more than move a key, and this is the case that shows why: restoring the
     * loop mode from a backup is not a rename, and without it an imported 3.3 backup comes back with
     * the loop OFF, because `RM.DEFAULT_MODE` is `DISABLED_LOOP`.
     */
    private suspend fun apsMode(sp: KeyValueStore) {
        if (!config.APS || !sp.contains("aps_mode")) return
        val mode = when (sp.getString("aps_mode", "CLOSED")) {
            "OPEN"   -> RM.Mode.OPEN_LOOP
            "CLOSED" -> RM.Mode.CLOSED_LOOP
            "LGS"    -> RM.Mode.CLOSED_LOOP_LGS
            else     -> RM.Mode.CLOSED_LOOP
        }
        persistenceLayer.insertOrUpdateRunningMode(
            runningMode = RM(timestamp = dateUtil.now(), mode = mode, autoForced = false, duration = 0),
            action = Action.CLOSED_LOOP_MODE,
            source = Sources.Aaps,
            listValues = listOf(ValueWithUnit.SimpleString("Migration"))
        )
        sp.remove("aps_mode")
    }

    /** Moves a legacy name holding a whole number to [destinationPrefix] + [argument]. */
    private fun moveLong(sp: KeyValueStore, name: String, value: Any?, argument: String?, destinationPrefix: String) {
        if (argument == null) return skip(name, "the name does not say what it belongs to")
        val number = LegacyPreferenceValue.asLong(value)
            ?: return skip(name, "expected a whole number, found ${LegacyPreferenceValue.describeType(value)}")
        sp.putLong("$destinationPrefix$argument", number)
        sp.remove(name)
    }

    /**
     * Logs and leaves the name exactly where it is.
     *
     * Leaving it is the point: the value is still there to look at, the migration runs again next time
     * and gets another chance at it, and a guessed replacement would write a wrong number into a
     * profile. See [LegacyPreferenceValue].
     */
    private fun skip(name: String, reason: String) {
        aapsLogger.warn(LTag.CORE, "Not migrating '$name': $reason. Left in place.")
    }

    /** The old per-preset keys, and what they became. */
    private class PresetSpec(
        val id: String,
        val reason: TT.Reason,
        val targetName: String,
        val durationName: String,
        val defaultMinutes: Long
    )

    private companion object {

        const val LOCAL_PROFILE_PREFIX = "LocalProfile_"

        /** The five schedules a profile carries, and the key each one moves to. */
        val SCHEDULE_FIELDS = mapOf(
            "isf" to ProfileComposedStringKey.LocalProfileNumberedIsf,
            "ic" to ProfileComposedStringKey.LocalProfileNumberedIc,
            "basal" to ProfileComposedStringKey.LocalProfileNumberedBasal,
            "targetlow" to ProfileComposedStringKey.LocalProfileNumberedTargetLow,
            "targethigh" to ProfileComposedStringKey.LocalProfileNumberedTargetHigh
        )

        val TEMP_TARGET_PRESETS = StringNonKey.TempTargetPresets.key

        /** The insulin curves that used to be selected through the Config Builder. */
        val INSULIN_PLUGINS = listOf(
            "InsulinOrefRapidActingPlugin",
            "InsulinOrefUltraRapidActingPlugin",
            "InsulinOrefFreePeakPlugin",
            "InsulinLyumjevPlugin"
        )

        val PRESETS = listOf(
            PresetSpec("eatingsoon", TT.Reason.EATING_SOON, "eatingsoon_target", "eatingsoon_duration", Constants.DEFAULT_TT_EATING_SOON_DURATION.toLong()),
            PresetSpec("activity", TT.Reason.ACTIVITY, "activity_target", "activity_duration", Constants.DEFAULT_TT_ACTIVITY_DURATION.toLong()),
            PresetSpec("hypo", TT.Reason.HYPOGLYCEMIA, "hypo_target", "hypo_duration", Constants.DEFAULT_TT_HYPO_DURATION.toLong())
        )
    }
}
