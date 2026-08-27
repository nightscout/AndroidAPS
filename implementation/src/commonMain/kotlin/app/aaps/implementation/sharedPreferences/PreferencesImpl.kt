package app.aaps.implementation.sharedPreferences

import app.aaps.core.data.time.T
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.data.model.devAssert
import app.aaps.core.interfaces.concurrent.AapsLock
import app.aaps.core.interfaces.sharedPreferences.KeyValueStore
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.HardLimits
import app.aaps.core.interfaces.utils.SafeParse
import app.aaps.core.keys.BooleanComposedKey
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.BooleanNonKey
import app.aaps.core.keys.DoubleKey
import app.aaps.core.keys.IntComposedKey
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.IntNonKey
import app.aaps.core.keys.IntentKey
import app.aaps.core.keys.LongComposedKey
import app.aaps.core.keys.LongNonKey
import app.aaps.core.keys.ProfileComposedBooleanKey
import app.aaps.core.keys.ProfileComposedStringKey
import app.aaps.core.keys.ProfileIntKey
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.StringNonKey
import app.aaps.core.keys.UnitDoubleKey
import app.aaps.core.keys.interfaces.BooleanComposedNonPreferenceKey
import app.aaps.core.keys.interfaces.BooleanNonPreferenceKey
import app.aaps.core.keys.interfaces.BooleanPreferenceKey
import app.aaps.core.keys.interfaces.ComposedKey
import app.aaps.core.keys.interfaces.DoubleComposedNonPreferenceKey
import app.aaps.core.keys.interfaces.DoubleNonPreferenceKey
import app.aaps.core.keys.interfaces.DoublePreferenceKey
import app.aaps.core.keys.interfaces.IntComposedNonPreferenceKey
import app.aaps.core.keys.interfaces.IntNonPreferenceKey
import app.aaps.core.keys.interfaces.IntPreferenceKey
import app.aaps.core.keys.interfaces.LongComposedNonPreferenceKey
import app.aaps.core.keys.interfaces.LongNonPreferenceKey
import app.aaps.core.keys.interfaces.LongPreferenceKey
import app.aaps.core.keys.interfaces.NonPreferenceKey
import app.aaps.core.keys.interfaces.PreferenceKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.StringComposedNonPreferenceKey
import app.aaps.core.keys.interfaces.StringNonPreferenceKey
import app.aaps.core.keys.interfaces.StringPreferenceKey
import app.aaps.core.keys.interfaces.SyncDirection
import app.aaps.core.keys.interfaces.UnitDoublePreferenceKey
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import kotlin.math.max
import kotlin.math.min

@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
class PreferencesImpl @Inject constructor(
    private val sp: KeyValueStore,
    // Plain factories, not dagger.Lazy: that type is Dagger vocabulary. These three are only reached
    // through a lambda to break the cycle back to Preferences - all three are singletons, so calling
    // through each time returns the same instance and Lazy's caching bought nothing.
    private val profileUtil: () -> ProfileUtil,
    private val profileFunction: () -> ProfileFunction,
    private val hardLimits: () -> HardLimits,
    private val persistenceLayer: PersistenceLayer,
    private val config: Config,
    private val dateUtil: DateUtil,
) : Preferences {

    override val simpleMode: Boolean get() = sp.getBoolean(BooleanKey.GeneralSimpleMode.key, BooleanKey.GeneralSimpleMode.defaultValue)
    override val apsMode: Boolean = config.APS
    override val nsclientMode: Boolean = config.AAPSCLIENT
    override val pumpControlMode: Boolean = config.PUMPCONTROL

    // A set, not a list. registerPreferences runs once per plugin at startup and used to hold enum
    // classes, so it did about a dozen contains-checks in total. It now holds the key constants, and
    // a list would mean a linear scan per key against well over a thousand of them. LinkedHashSet
    // keeps insertion order, so everything that iterates this still sees the same sequence.
    private val prefsList: MutableSet<NonPreferenceKey> =
        (BooleanComposedKey.entries +
            BooleanKey.entries +
            BooleanNonKey.entries +
            DoubleKey.entries +
            IntentKey.entries +
            IntKey.entries +
            IntComposedKey.entries +
            IntNonKey.entries +
            LongComposedKey.entries +
            LongNonKey.entries +
            StringKey.entries +
            StringNonKey.entries +
            UnitDoubleKey.entries +
            ProfileComposedStringKey.entries +
            ProfileComposedBooleanKey.entries +
            ProfileIntKey.entries).toCollection(LinkedHashSet())

    // Emits a key on every LOCAL write to a Bidirectional-synced preference (not on putRemote), so
    // the client→master sync publisher can push local edits. Buffered + DROP_OLDEST so a pref write
    // never suspends or fails if no collector is attached.
    private val _syncedLocalChanges = MutableSharedFlow<NonPreferenceKey>(extraBufferCapacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val syncedLocalChanges: SharedFlow<NonPreferenceKey> get() = _syncedLocalChanges

    override fun getSyncKeys(): List<NonPreferenceKey> =
        prefsList.filter { it.sync != null }

    /** Local edit of a synced key: bump its monotonic modified stamp and signal the publisher. */
    private fun onLocalSyncedWrite(key: NonPreferenceKey) {
        if (key.sync?.direction != SyncDirection.Bidirectional) return
        put(LongComposedKey.SyncedPrefModified, key.key, value = max(get(LongComposedKey.SyncedPrefModified, key.key) + 1, dateUtil.now()))
        _syncedLocalChanges.tryEmit(key)
    }

    /** Applied-from-sync write of a synced key: adopt the incoming stamp, do NOT signal the publisher. */
    private fun onRemoteSyncedWrite(key: NonPreferenceKey, version: Long) {
        if (key.sync?.direction != SyncDirection.Bidirectional) return
        put(LongComposedKey.SyncedPrefModified, key.key, value = version)
    }

    private val booleanFlows = FlowCache<Boolean>()
    private val stringFlows = FlowCache<String>()
    private val doubleFlows = FlowCache<Double>()
    private val unitDoubleFlows = FlowCache<Double>()
    private val intFlows = FlowCache<Int>()
    private val longFlows = FlowCache<Long>()

    private fun isHidden(key: PreferenceKey): Boolean =
        if (apsMode && key.showInApsMode == false) true
        else if (nsclientMode && key.showInNsClientMode == false) true
        else if (pumpControlMode && key.showInPumpControlMode == false) true
        else false

    override fun get(key: BooleanNonPreferenceKey): Boolean =
        sp.getBoolean(key.key, key.defaultValue)

    override fun get(key: BooleanNonPreferenceKey, forSync: Boolean): Boolean =
        // forSync: fall back to the COMPUTED default (no simple-mode forcing) so the operative value travels.
        if (forSync && key is BooleanPreferenceKey && key.calculatedDefaultValue) sp.getBoolean(key.key, calculatedDefaultValue(key))
        else sp.getBoolean(key.key, key.defaultValue)

    override fun getIfExists(key: BooleanNonPreferenceKey): Boolean? =
        if (sp.contains(key.key)) sp.getBoolean(key.key, key.defaultValue) else null

    override fun put(key: BooleanNonPreferenceKey, value: Boolean) {
        sp.putBoolean(key.key, value)
        booleanFlows[key.key]?.value = value
        onLocalSyncedWrite(key)
    }

    override fun putRemote(key: BooleanNonPreferenceKey, value: Boolean, version: Long) {
        sp.putBoolean(key.key, value)
        booleanFlows[key.key]?.value = value
        onRemoteSyncedWrite(key, version)
    }

    override fun observe(key: BooleanNonPreferenceKey): StateFlow<Boolean> =
        booleanFlows.getOrCreate(key.key) { MutableStateFlow(get(key)) }

    override fun get(key: BooleanPreferenceKey): Boolean =
        if (!config.isEngineeringMode() && key.engineeringModeOnly) key.defaultValue
        else if (simpleMode && key.defaultedBySM) calculatedDefaultValue(key)
        // Recompute-when-hidden is for LOCAL values only; a synced key is remote-authoritative, so its stored value wins (visibility ≠ value).
        else if (key.calculatedDefaultValue && isHidden(key) && key.sync == null) calculatedDefaultValue(key)
        else sp.getBoolean(key.key, calculatedDefaultValue(key))

    override fun get(key: StringNonPreferenceKey): String =
        sp.getString(key.key, key.defaultValue)

    override fun get(key: StringPreferenceKey): String =
        if (simpleMode && key.defaultedBySM) key.defaultValue
        else sp.getString(key.key, key.defaultValue)

    override fun getIfExists(key: StringNonPreferenceKey): String? =
        if (sp.contains(key.key)) sp.getString(key.key, key.defaultValue) else null

    override fun put(key: StringNonPreferenceKey, value: String) {
        sp.putString(key.key, value)
        stringFlows[key.key]?.value = value
        onLocalSyncedWrite(key)
        if (key.key == StringKey.GeneralUnits.key) refreshUnitDoubleFlows()
    }

    override fun putRemote(key: StringNonPreferenceKey, value: String, version: Long) {
        sp.putString(key.key, value)
        stringFlows[key.key]?.value = value
        onRemoteSyncedWrite(key, version)
        if (key.key == StringKey.GeneralUnits.key) refreshUnitDoubleFlows()
    }

    override fun observe(key: StringNonPreferenceKey): StateFlow<String> =
        stringFlows.getOrCreate(key.key) { MutableStateFlow(get(key)) }

    override fun get(key: DoubleNonPreferenceKey): Double =
        sp.getDouble(key.key, key.defaultValue)

    override fun get(key: DoublePreferenceKey): Double =
        if (simpleMode && key.calculatedBySM) calculatePreference(key)
        else if (simpleMode && key.defaultedBySM) key.defaultValue
        else sp.getDouble(key.key, key.defaultValue)

    override fun getIfExists(key: DoublePreferenceKey): Double? =
        if (sp.contains(key.key)) sp.getDouble(key.key, key.defaultValue) else null

    override fun put(key: DoubleNonPreferenceKey, value: Double) {
        sp.putDouble(key.key, value)
        doubleFlows[key.key]?.value = value
        onLocalSyncedWrite(key)
    }

    override fun putRemote(key: DoubleNonPreferenceKey, value: Double, version: Long) {
        sp.putDouble(key.key, value)
        doubleFlows[key.key]?.value = value
        onRemoteSyncedWrite(key, version)
    }

    override fun observe(key: DoubleNonPreferenceKey): StateFlow<Double> =
        doubleFlows.getOrCreate(key.key) { MutableStateFlow(get(key)) }

    override fun get(key: DoubleComposedNonPreferenceKey, vararg arguments: Any): Double =
        sp.getDouble(key.composeKey(*arguments), key.defaultValue)

    override fun getIfExists(key: DoubleComposedNonPreferenceKey, vararg arguments: Any): Double? =
        if (sp.contains(key.composeKey(*arguments))) sp.getDouble(key.composeKey(*arguments), key.defaultValue) else null

    override fun put(key: DoubleComposedNonPreferenceKey, vararg arguments: Any, value: Double) {
        val composedKey = key.composeKey(*arguments)
        sp.putDouble(composedKey, value)
        doubleFlows[composedKey]?.value = value

    }

    override fun observe(key: DoubleComposedNonPreferenceKey, vararg arguments: Any): StateFlow<Double> {
        val composedKey = key.composeKey(*arguments)
        return doubleFlows.getOrCreate(composedKey) { MutableStateFlow(get(key, *arguments)) }
    }

    override fun get(key: UnitDoublePreferenceKey): Double =
        if (simpleMode && key.defaultedBySM) profileUtil().valueInCurrentUnitsDetect(key.defaultValue)
        else profileUtil().valueInCurrentUnitsDetect(sp.getDouble(key.key, key.defaultValue))

    override fun getIfExists(key: UnitDoublePreferenceKey): Double? =
        if (sp.contains(key.key)) profileUtil().valueInCurrentUnitsDetect(sp.getDouble(key.key, key.defaultValue)) else null

    override fun put(key: UnitDoublePreferenceKey, value: Double) {
        sp.putDouble(key.key, value)
        unitDoubleFlows[key.key]?.value = get(key)
        onLocalSyncedWrite(key)
    }

    override fun putRemote(key: UnitDoublePreferenceKey, value: Double, version: Long) {
        sp.putDouble(key.key, value)
        unitDoubleFlows[key.key]?.value = get(key)
        onRemoteSyncedWrite(key, version)
    }

    // Raw stored mg/dl, no display-unit conversion (unlike get()). Used for 1:1 sync.
    override fun getRaw(key: UnitDoublePreferenceKey): Double =
        sp.getDouble(key.key, key.defaultValue)

    override fun observe(key: UnitDoublePreferenceKey): StateFlow<Double> =
        unitDoubleFlows.getOrCreate(key.key) { MutableStateFlow(get(key)) }

    private fun refreshUnitDoubleFlows() {
        unitDoubleFlows.forEach { (keyString, flow) ->
            (get(keyString) as? UnitDoublePreferenceKey)?.let { flow.value = get(it) }
        }
    }

    override fun get(key: IntNonPreferenceKey): Int =
        sp.getInt(key.key, key.defaultValue)

    override fun get(key: IntNonPreferenceKey, forSync: Boolean): Int =
        if (forSync && key is IntPreferenceKey && key.calculatedDefaultValue) sp.getInt(key.key, calculatedDefaultValue(key))
        else sp.getInt(key.key, key.defaultValue)

    override fun getIfExists(key: IntNonPreferenceKey): Int? =
        if (sp.contains(key.key)) sp.getInt(key.key, key.defaultValue) else null

    override fun put(key: IntNonPreferenceKey, value: Int) {
        sp.putInt(key.key, value)
        intFlows[key.key]?.value = value
        onLocalSyncedWrite(key)
    }

    override fun putRemote(key: IntNonPreferenceKey, value: Int, version: Long) {
        sp.putInt(key.key, value)
        intFlows[key.key]?.value = value
        onRemoteSyncedWrite(key, version)
    }

    override fun observe(key: IntNonPreferenceKey): StateFlow<Int> =
        intFlows.getOrCreate(key.key) { MutableStateFlow(get(key)) }

    override fun inc(key: IntNonPreferenceKey) {
        sp.incInt(key.key)
        intFlows[key.key]?.let { it.value = get(key) }

    }

    override fun get(key: IntPreferenceKey): Int =
        if (!config.isEngineeringMode() && key.engineeringModeOnly) key.defaultValue
        else if (simpleMode && key.defaultedBySM) calculatedDefaultValue(key)
        // Recompute-when-hidden is for LOCAL values only; a synced key is remote-authoritative, so its stored value wins (visibility ≠ value).
        else if (key.calculatedDefaultValue && isHidden(key) && key.sync == null) calculatedDefaultValue(key)
        else sp.getInt(key.key, calculatedDefaultValue(key))

    override fun get(key: IntComposedNonPreferenceKey, vararg arguments: Any): Int =
        sp.getInt(key.composeKey(*arguments), key.defaultValue)

    override fun put(key: IntComposedNonPreferenceKey, vararg arguments: Any, value: Int) {
        val composedKey = key.composeKey(*arguments)
        sp.putInt(composedKey, value)
        intFlows[composedKey]?.value = value

    }

    override fun observe(key: IntComposedNonPreferenceKey, vararg arguments: Any): StateFlow<Int> {
        val composedKey = key.composeKey(*arguments)
        return intFlows.getOrCreate(composedKey) { MutableStateFlow(get(key, *arguments)) }
    }

    override fun get(key: LongNonPreferenceKey): Long =
        sp.getLong(key.key, key.defaultValue)

    override fun inc(key: LongNonPreferenceKey) {
        sp.incLong(key.key)
        longFlows[key.key]?.let { it.value = get(key) }

    }

    override fun getIfExists(key: LongNonPreferenceKey): Long? =
        if (sp.contains(key.key)) sp.getLong(key.key, key.defaultValue) else null

    override fun put(key: LongNonPreferenceKey, value: Long) {
        sp.putLong(key.key, value)
        longFlows[key.key]?.value = value

    }

    override fun observe(key: LongNonPreferenceKey): StateFlow<Long> =
        longFlows.getOrCreate(key.key) { MutableStateFlow(get(key)) }

    override fun get(key: LongPreferenceKey): Long =
        if (!config.isEngineeringMode() && key.engineeringModeOnly) key.defaultValue
        else if (simpleMode && key.defaultedBySM) calculatedDefaultValue(key)
        // Recompute-when-hidden is for LOCAL values only; a synced key is remote-authoritative, so its stored value wins (visibility ≠ value).
        else if (key.calculatedDefaultValue && isHidden(key) && key.sync == null) calculatedDefaultValue(key)
        else sp.getLong(key.key, calculatedDefaultValue(key))

    override fun remove(key: NonPreferenceKey) {
        sp.remove(key.key)
    }

    override fun get(key: LongComposedNonPreferenceKey, vararg arguments: Any): Long =
        sp.getLong(key.composeKey(*arguments), key.defaultValue)

    override fun getIfExists(key: LongComposedNonPreferenceKey, vararg arguments: Any): Long? =
        if (sp.contains(key.composeKey(*arguments))) sp.getLong(key.composeKey(*arguments), key.defaultValue) else null

    override fun put(key: LongComposedNonPreferenceKey, vararg arguments: Any, value: Long) {
        val composedKey = key.composeKey(*arguments)
        sp.putLong(composedKey, value)
        longFlows[composedKey]?.value = value

    }

    override fun observe(key: LongComposedNonPreferenceKey, vararg arguments: Any): StateFlow<Long> {
        val composedKey = key.composeKey(*arguments)
        return longFlows.getOrCreate(composedKey) { MutableStateFlow(get(key, *arguments)) }
    }

    override fun remove(key: ComposedKey, vararg arguments: Any) {
        sp.remove(key.composeKey(*arguments))
    }

    override fun isUnitDependent(key: String): Boolean =
        UnitDoubleKey.entries.any { it.key == key }

    override fun get(key: String): NonPreferenceKey? =
        prefsList
            .find { it.key == key }

    override fun getIfExists(key: String): NonPreferenceKey? =
        prefsList
            .find { it.key == key }

    override fun get(key: BooleanComposedNonPreferenceKey, vararg arguments: Any): Boolean =
        sp.getBoolean(key.composeKey(*arguments), key.defaultValue)

    override fun get(key: BooleanComposedNonPreferenceKey, vararg arguments: Any, defaultValue: Boolean): Boolean =
        sp.getBoolean(key.composeKey(*arguments), defaultValue)

    override fun getIfExists(key: BooleanComposedNonPreferenceKey, vararg arguments: Any): Boolean? =
        if (sp.contains(key.composeKey(*arguments))) sp.getBoolean(key.composeKey(*arguments), key.defaultValue) else null

    override fun put(key: BooleanComposedNonPreferenceKey, vararg arguments: Any, value: Boolean) {
        val composedKey = key.composeKey(*arguments)
        sp.putBoolean(composedKey, value)
        booleanFlows[composedKey]?.value = value

    }

    override fun observe(key: BooleanComposedNonPreferenceKey, vararg arguments: Any): StateFlow<Boolean> {
        val composedKey = key.composeKey(*arguments)
        return booleanFlows.getOrCreate(composedKey) { MutableStateFlow(get(key, *arguments)) }
    }

    override fun get(key: StringComposedNonPreferenceKey, vararg arguments: Any): String =
        sp.getString(key.composeKey(*arguments), key.defaultValue)

    override fun getIfExists(key: StringComposedNonPreferenceKey, vararg arguments: Any): String? =
        if (sp.contains(key.composeKey(*arguments))) sp.getString(key.composeKey(*arguments), key.defaultValue) else null

    override fun put(key: StringComposedNonPreferenceKey, vararg arguments: Any, value: String) {
        val composedKey = key.composeKey(*arguments)
        sp.putString(composedKey, value)
        stringFlows[composedKey]?.value = value

    }

    override fun observe(key: StringComposedNonPreferenceKey, vararg arguments: Any): StateFlow<String> {
        val composedKey = key.composeKey(*arguments)
        return stringFlows.getOrCreate(composedKey) { MutableStateFlow(get(key, *arguments)) }
    }

    override fun registerPreferences(keys: List<NonPreferenceKey>) {
        prefsList.addAll(keys)
    }

    override fun allMatchingStrings(key: ComposedKey): List<String> =
        mutableListOf<String>().also {
            devAssert(key.format == "%s")
            val keys: Map<String, *> = sp.getAll()
            for ((singleKey, _) in keys)
                if (singleKey.startsWith(key.key)) it.add(singleKey.split(key.key)[1])
        }

    override fun allMatchingInts(key: ComposedKey): List<Int> =
        mutableListOf<Int>().also {
            devAssert(key.format == "%d")
            val keys: Map<String, *> = sp.getAll()
            for ((singleKey, _) in keys)
                if (singleKey.startsWith(key.key)) it.add(SafeParse.stringToInt(singleKey.split(key.key)[1]))
        }

    override fun isExportableKey(key: String): Boolean {
        prefsList
            .forEach {
                if (it.key == key && it.exportable) return true
                if (it is ComposedKey && key.startsWith(it.key) && it.exportable) return true
            }
        return false
    }

    private fun calculatedDefaultValue(key: IntPreferenceKey): Int =
        if (key.calculatedDefaultValue)
            when (key) {
                IntKey.AutosensPeriod ->
                    when (get(StringKey.SafetyAge)) {
                        hardLimits().ageEntryValues()[HardLimits.AgeType.TEENAGE.ordinal] -> 4
                        hardLimits().ageEntryValues()[HardLimits.AgeType.CHILD.ordinal]   -> 4
                        else                                                                  -> 24
                    }

                else                  -> error("Unsupported default value calculation")
            }
        else key.defaultValue

    private fun calculatedDefaultValue(key: LongPreferenceKey): Long =
        if (key.calculatedDefaultValue)
            when (key) {
                else -> error("Unsupported default value calculation")
            }
        else key.defaultValue

    private fun calculatedDefaultValue(key: BooleanPreferenceKey): Boolean =
        if (key.calculatedDefaultValue)
            when (key) {
                BooleanKey.OverviewKeepScreenOn                    -> config.AAPSCLIENT
                BooleanKey.NsClientNotificationsFromAlarms         -> config.AAPSCLIENT
                BooleanKey.NsClientNotificationsFromAnnouncements  -> config.AAPSCLIENT
                BooleanKey.NsClientLogAppStart                     -> config.APS
                BooleanKey.NsClientCreateAnnouncementsFromErrors   -> config.APS
                BooleanKey.NsClientCreateAnnouncementsFromCarbsReq -> config.APS
                BooleanKey.NsClientAllowClientControl              -> simpleMode // client control on by default in simple mode, opt-in otherwise
                else                                               -> error("Unsupported default value calculation")
            }
        else key.defaultValue

    private fun calculatePreference(key: DoublePreferenceKey): Double =
        limit(
            key, when (key) {
                DoubleKey.ApsMaxBasal  -> runBlocking { profileFunction().getProfile() }?.getMaxDailyBasal()?.let { it * 3 } ?: key.defaultValue
                DoubleKey.ApsSmbMaxIob -> recentMaxBolus() + (runBlocking { profileFunction().getProfile() }?.getMaxDailyBasal()?.let { it * 3 } ?: key.defaultValue)
                DoubleKey.ApsAmaMaxIob -> runBlocking { profileFunction().getProfile() }?.getMaxDailyBasal()?.let { it * 3 } ?: key.defaultValue
                else                   -> error("Unsupported key calculation")
            })

    private fun limit(key: DoublePreferenceKey, calculated: Double) = min(key.max, max(key.min, calculated))
    private fun recentMaxBolus(): Double =
        runBlocking {
            persistenceLayer
                .getBolusesFromTime(dateUtil.now() - T.days(7).msecs(), true)
                .maxOfOrNull { it.amount }
                ?: hardLimits().maxBolus()
        }

    override fun getAllPreferenceKeys(): List<PreferenceKey> =
        prefsList.filterIsInstance<PreferenceKey>()

    /**
     * The per key [MutableStateFlow] cache, kept behind a lock.
     *
     * This replaces a `ConcurrentHashMap`, which is JVM only. The single thing that has to hold is
     * that two callers asking for the same key get the *same* flow: if they got different ones, an
     * observer would attach to a flow that never receives the next write, and simply stop updating.
     * `getOrCreate` is therefore atomic, which is what `computeIfAbsent` gave before.
     */
    private class FlowCache<T> {

        private val lock = AapsLock()
        private val values = mutableMapOf<String, MutableStateFlow<T>>()

        operator fun get(key: String): MutableStateFlow<T>? {
            lock.lock()
            try {
                return values[key]
            } finally {
                lock.unlock()
            }
        }

        fun getOrCreate(key: String, create: (String) -> MutableStateFlow<T>): MutableStateFlow<T> {
            lock.lock()
            try {
                return values.getOrPut(key) { create(key) }
            } finally {
                lock.unlock()
            }
        }

        /**
         * Visits every cached flow.
         *
         * The entries are copied before [action] runs, so a caller that writes to a flow - which is
         * what the refresh passes do - cannot deadlock against the lock this cache holds.
         */
        fun forEach(action: (Map.Entry<String, MutableStateFlow<T>>) -> Unit) {
            lock.lock()
            val snapshot = try {
                values.entries.toList()
            } finally {
                lock.unlock()
            }
            snapshot.forEach(action)
        }
    }
}
