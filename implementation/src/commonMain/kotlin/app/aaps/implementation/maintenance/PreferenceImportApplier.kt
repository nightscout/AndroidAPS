package app.aaps.implementation.maintenance

import app.aaps.core.data.plugin.PluginType
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.maintenance.ImportExportPrefs
import app.aaps.core.interfaces.maintenance.Prefs
import app.aaps.core.interfaces.sharedPreferences.KeyValueStore
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.KeyCategory
import app.aaps.core.keys.LongComposedKey
import app.aaps.core.keys.ResolvedKey
import app.aaps.core.keys.interfaces.BooleanComposedNonPreferenceKey
import app.aaps.core.keys.interfaces.BooleanNonPreferenceKey
import app.aaps.core.keys.interfaces.DoubleComposedNonPreferenceKey
import app.aaps.core.keys.interfaces.DoubleNonPreferenceKey
import app.aaps.core.keys.interfaces.IntComposedNonPreferenceKey
import app.aaps.core.keys.interfaces.IntNonPreferenceKey
import app.aaps.core.keys.interfaces.LongComposedNonPreferenceKey
import app.aaps.core.keys.interfaces.LongNonPreferenceKey
import app.aaps.core.keys.interfaces.NonPreferenceKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.StringComposedNonPreferenceKey
import app.aaps.core.keys.interfaces.StringNonPreferenceKey
import app.aaps.core.keys.interfaces.SyncDirection
import app.aaps.core.keys.interfaces.UnitDoublePreferenceKey
import app.aaps.implementation.maintenance.migration.FileKeyValueStore
import app.aaps.implementation.maintenance.migration.PreferenceMigrations
import app.aaps.implementation.sharedPreferences.PreferenceKeyResolverFactory
import dev.zacsweers.metro.Inject
import kotlin.math.max

/**
 * Applies an imported settings file to the live store.
 *
 * This replaces `sp.clear()` + "rewrite every key raw", which had three problems: the live store was
 * wiped (so every "keep the pump working" mechanism had to exist to put things back), the type of
 * each value was guessed from its text, and the writes went below `Preferences` so no
 * `observe(...)` flow ever saw them.
 *
 * Here the store is never cleared. Each name from the file is resolved to the key that owns it, the
 * value is parsed as that key's type, and only the keys that would actually CHANGE are written - in
 * one batch, followed by one [Preferences.reloadFromStore].
 *
 * ## What that means for what stays behind
 *
 * Not clearing turns an import from a REPLACE into a MERGE: a key set on this phone but absent from
 * the file survives. That is deliberate - it is what keeps the pump working - but the replace
 * semantics have to come back somewhere, as a removal rule that can tell trash from live data. That
 * rule does not exist yet (see `_docs/IMPORT.md`), so until it does, an import leaves unknown old
 * values alone rather than deleting them on a guess.
 *
 * ## Why the diff
 *
 * A `StateFlow` already conflates, so writing an unchanged value emits nothing - the diff is not
 * about observers. It is about `onLocalSyncedWrite`, which stamps and publishes a synced key
 * REGARDLESS of whether the value changed, and about the cost of the write itself. Mostly it is
 * about blast radius: an import becomes "these 12 settings changed", which can be shown to the user
 * before they commit and read back in a log afterwards.
 */
@Inject
class PreferenceImportApplier(
    private val store: KeyValueStore,
    private val preferences: Preferences,
    private val resolverFactory: PreferenceKeyResolverFactory,
    private val config: Config,
    private val aapsLogger: AAPSLogger,
    private val dateUtil: DateUtil,
    private val migrations: PreferenceMigrations
) {

    /** Works out what would change, and writes nothing. For the confirm screen. */
    fun preview(prefs: Prefs, keepPumpSettings: Boolean): ImportExportPrefs.ImportOutcome =
        run(prefs.values, keepPumpSettings, write = false)

    /**
     * Applies the file. Migrates it, writes once, then publishes once.
     *
     * The migrations run HERE and not in [preview], once per import. They are the same functions
     * start-up runs, and a function is free to do more than move a key - the loop mode one writes a
     * `RunningMode` row - so running them while the user is still looking at the confirm dialog would
     * change the device before they agreed to anything.
     *
     * The price is that [preview]'s counts are computed from the file's original names, so for a
     * backup old enough to need migrating they understate what will change.
     */
    suspend fun apply(prefs: Prefs, keepPumpSettings: Boolean): ImportExportPrefs.ImportOutcome {
        val migrated = FileKeyValueStore(prefs.values)
        migrations.migrate(migrated)
        return run(migrated.asTextMap(), keepPumpSettings, write = true)
    }

    private fun run(values: Map<String, String>, keepPumpSettings: Boolean, write: Boolean): ImportExportPrefs.ImportOutcome {
        val resolver = resolverFactory.create()
        val edits = mutableListOf<KeyValueStore.Editor.() -> Unit>()

        var unchanged = 0
        var pumpSkipped = 0
        var pumpWouldChange = 0
        var syncedSkipped = 0
        var notExportable = 0
        val unresolved = mutableListOf<String>()
        val unreadable = mutableListOf<String>()
        val syncedWritten = mutableListOf<NonPreferenceKey>()

        for ((name, value) in values) {
            val resolved = resolver.resolve(name)
            if (resolved == null) {
                // Not an error. A file from a newer AAPS carries keys this build never had, and a
                // client build never constructs the pump and APS plugins so their keys are registered
                // nowhere. Left in place rather than dropped: dropping would silently discard a whole
                // category that is legitimately in the file.
                unresolved += name
                continue
            }
            val key = resolved.key

            // The one line that enforces "device state never arrives from a file". Old files carry
            // keys whose flag has since changed, and hand-edited files carry anything at all.
            if (!key.exportable) {
                notExportable++
                continue
            }

            // On a client a SYNCED key belongs to the master, whichever direction it travels.
            // Writing it here would apply and then be silently reverted by the master's next cold
            // publish - `applySyncedPrefs` adopts with putRemote(.., 0L), unconditionally - which is
            // worse than never applying it.
            //
            // The test used to be `direction == Bidirectional`, which left MasterOnly keys through:
            // those are even more the master's own, and a client writing one is the same silent
            // revert with none of the ambiguity.
            if (config.AAPSCLIENT && key.sync != null) {
                syncedSkipped++
                continue
            }

            val isPumpConfiguration = resolved.category == KeyCategory.PumpInternal || resolved.isPumpSelection()
            if (keepPumpSettings && isPumpConfiguration) {
                pumpSkipped++
                // Counted, not written: the confirm screen says how many settings the checkbox is
                // protecting, which is the difference between an informed tick and a blind one.
                if (stage(name, key, value) is Staged.Write) pumpWouldChange++
                continue
            }

            when (val staged = stage(name, key, value)) {
                is Staged.Write     -> {
                    edits += staged.edit
                    // Remembered so the stamp can be advanced after the batch - see stampSyncedKeys.
                    if (key.sync?.direction == SyncDirection.Bidirectional) syncedWritten += key
                }
                Staged.Unchanged    -> unchanged++
                Staged.Unreadable   -> {
                    // Same rule as the start-up migrations: log it, skip it, never guess. One odd
                    // value must not stop an import.
                    aapsLogger.warn(LTag.CORE, "Import: cannot read '$value' as ${key::class.simpleName} for '$name', skipped")
                    unreadable += name
                }
            }
        }

        if (write && edits.isNotEmpty()) {
            // One batch, then one publish. Not ~500 puts: those are ~500 separate commits, they let a
            // collector see a half-imported store, and each one stamps a synced key on the way past.
            store.edit(commit = true) { edits.forEach { it() } }
            preferences.reloadFromStore()
            stampSyncedKeys(syncedWritten)
        }

        return ImportExportPrefs.ImportOutcome(
            changed = edits.size,
            unchanged = unchanged,
            pumpSkipped = pumpSkipped,
            pumpWouldChange = pumpWouldChange,
            syncedSkipped = syncedSkipped,
            notExportable = notExportable,
            unresolved = unresolved,
            unreadable = unreadable
        )
    }

    /**
     * Marks the imported synced keys as edited NOW, so a client's older copy cannot win them back.
     *
     * The batch writes below `Preferences`, deliberately - that is what makes it one commit instead of
     * five hundred - but it means `onLocalSyncedWrite` never runs and the stamp keeps whatever value
     * it had before the import. A client that edited the same key while it was offline then pushes
     * with a NEWER stamp, `ClientControlReceiver` reads `pushed.lastModified > ours`, accepts it, and
     * the imported value is silently replaced. The import log still says it applied.
     *
     * `max(previous + 1, now)` rather than a bare `now()`, matching `onLocalSyncedWrite`: the stamp
     * has to keep rising even on a device whose clock has gone backwards.
     *
     * Nothing is emitted on `syncedLocalChanges`. That flow drives the CLIENT publisher, and a client
     * skips synced keys entirely, so this list is empty there; on a master nothing collects it. The
     * master's own peers hear about the import through the cold republish that `reloadFromStore`
     * triggers.
     */
    private fun stampSyncedKeys(keys: List<NonPreferenceKey>) {
        keys.forEach { key ->
            val previous = preferences.get(LongComposedKey.SyncedPrefModified, key.key)
            preferences.put(LongComposedKey.SyncedPrefModified, key.key, value = max(previous + 1, dateUtil.now()))
        }
    }

    /**
     * A `ConfigBuilder_Enabled_*` entry for a pump driver.
     *
     * The argument is `PluginType.name + "_" + the plugin class simple name`, built by
     * `ConfigBuilderImpl.composedKeyFor`, so a pump's entry starts with `PUMP_`. Matching the prefix
     * rather than asking the plugin list is deliberate: the file can name a driver this build does
     * not have, and that still has to count as pump selection so the checkbox protects it.
     */
    private fun ResolvedKey.isPumpSelection(): Boolean =
        category == KeyCategory.ConfigBuilderEnabled &&
            this is ResolvedKey.Composed &&
            argument.startsWith(PluginType.PUMP.name + "_")

    private sealed interface Staged {
        class Write(val edit: KeyValueStore.Editor.() -> Unit) : Staged
        data object Unchanged : Staged
        data object Unreadable : Staged
    }

    /**
     * Reads the text as the key's type and says whether it differs from what is stored.
     *
     * The comparison reads the RAW store by name rather than going through `Preferences`. The name is
     * already the composed form, so this needs no argument typing - and it is the stored value that
     * the write would replace, which is exactly what "would this change anything" asks.
     */
    private fun stage(name: String, key: NonPreferenceKey, text: String): Staged = when (key) {
        is BooleanNonPreferenceKey         -> boolean(name, text, key.defaultValue)
        is BooleanComposedNonPreferenceKey -> boolean(name, text, key.defaultValue)
        is StringNonPreferenceKey          -> string(name, text, key.defaultValue)
        is StringComposedNonPreferenceKey  -> string(name, text, key.defaultValue)
        is IntNonPreferenceKey             -> int(name, text, key.defaultValue)
        is IntComposedNonPreferenceKey     -> int(name, text, key.defaultValue)
        is LongNonPreferenceKey            -> long(name, text, key.defaultValue)
        is LongComposedNonPreferenceKey    -> long(name, text, key.defaultValue)
        is DoubleNonPreferenceKey          -> double(name, text, key.defaultValue)
        is DoubleComposedNonPreferenceKey  -> double(name, text, key.defaultValue)
        // Stored as raw mg/dl; the unit conversion happens on the way out of `Preferences`, not here.
        is UnitDoublePreferenceKey         -> double(name, text, key.defaultValue)
        else                               -> Staged.Unreadable
    }

    /**
     * "The store already holds exactly this, so writing it would change nothing."
     *
     * Three things it has to get right, and the first two were wrong before:
     *
     * 1. **An ABSENT key is never a match**, whatever the default says. The comparison used to pass
     *    `key.defaultValue` as the fallback, but `Preferences.get` does not use that for the eight
     *    `calculatedDefaultValue` keys - it falls back to a value computed from simple mode, the
     *    user's age and so on. So a file value that happened to equal the static default was counted
     *    "unchanged", nothing was written, and the effective value stayed the COMPUTED default, which
     *    can be its opposite. `ns_allow_client_control` is the one that matters: an import saying
     *    remote control is off left it on. If the file names a key, the store ends up carrying it.
     * 2. **A read that throws is never a match.** The store is untyped and older builds wrote
     *    booleans as text, so reading by the key's declared type can throw `ClassCastException` -
     *    `SPImpl.getString` has no try/catch. That used to abort the whole import. Now it writes,
     *    which also repairs the wrongly-typed entry.
     * 3. It is only asked once the value has parsed, so an unreadable value never reaches it.
     */
    private fun alreadyStored(name: String, read: () -> Boolean): Boolean =
        store.contains(name) && runCatching(read).getOrDefault(false)

    private fun boolean(name: String, text: String, default: Boolean): Staged {
        val value = text.toBooleanStrictOrNull() ?: return Staged.Unreadable
        return if (alreadyStored(name) { store.getBoolean(name, default) == value }) Staged.Unchanged
        else Staged.Write { putBoolean(name, value) }
    }

    private fun string(name: String, text: String, default: String): Staged =
        if (alreadyStored(name) { store.getString(name, default) == text }) Staged.Unchanged
        else Staged.Write { putString(name, text) }

    private fun int(name: String, text: String, default: Int): Staged {
        val value = text.toIntOrNull() ?: return Staged.Unreadable
        return if (alreadyStored(name) { store.getInt(name, default) == value }) Staged.Unchanged
        else Staged.Write { putInt(name, value) }
    }

    private fun long(name: String, text: String, default: Long): Staged {
        val value = text.toLongOrNull() ?: return Staged.Unreadable
        return if (alreadyStored(name) { store.getLong(name, default) == value }) Staged.Unchanged
        else Staged.Write { putLong(name, value) }
    }

    /**
     * Doubles need the extra `toFloat` comparison or NOTHING is ever unchanged.
     *
     * Android stores a double as a float (`SPImpl.putDouble` calls `putFloat`) and reads it back
     * widened, while the export writes the shortest decimal of the original double. 5.5 survives;
     * 3.3 comes back as 3.299999952316284 and never equals the "3.3" in the file. So every double in
     * every import counted as a change - which is not wrong, but it makes the count meaningless and
     * stamps keys nobody touched. Comparing at float precision as well asks the question that
     * actually matters: would writing this alter what is stored?
     */
    private fun double(name: String, text: String, default: Double): Staged {
        val value = text.toDoubleOrNull() ?: return Staged.Unreadable
        return if (alreadyStored(name) {
                val stored = store.getDouble(name, default)
                stored == value || stored.toFloat() == value.toFloat()
            }
        ) Staged.Unchanged
        else Staged.Write { putDouble(name, value) }
    }
}
