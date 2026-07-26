package app.aaps.wear.sharedPreferences

import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.IntComposedKey
import app.aaps.core.keys.IntNonKey
import app.aaps.core.keys.LongNonKey
import app.aaps.core.keys.UnitDoubleKey
import app.aaps.core.keys.interfaces.PreferenceKey
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Unit tests for [PreferencesImpl] (wear module).
 *
 * Two groups of tests:
 *  - Pure reflection helpers over the real enum key classes registered in `prefsList`
 *    (deterministic, only need a mock [SP]).
 *  - SP-delegating value logic with the [kotlinx.coroutines.flow.MutableStateFlow] cache,
 *    driven by an in-memory HashMap-backed [SP] mock so that put/inc round-trips through
 *    `observe(...)`.
 */
internal class PreferencesImplTest {

    private lateinit var sp: SP
    private lateinit var sut: PreferencesImpl

    /** In-memory backing store for the [SP] mock. */
    private val store: MutableMap<String, Any> = HashMap()

    @BeforeEach
    fun setup() {
        sp = mock()
        store.clear()

        whenever(sp.contains(any<String>())).doAnswer { store.containsKey(it.getArgument<String>(0)) }
        whenever(sp.getAll()).doAnswer { store.toMap() }

        whenever(sp.getBoolean(any<String>(), any())).doAnswer {
            (store[it.getArgument<String>(0)] as? Boolean) ?: it.getArgument<Boolean>(1)
        }
        whenever(sp.getInt(any<String>(), any())).doAnswer {
            (store[it.getArgument<String>(0)] as? Int) ?: it.getArgument<Int>(1)
        }
        whenever(sp.getLong(any<String>(), any())).doAnswer {
            (store[it.getArgument<String>(0)] as? Long) ?: it.getArgument<Long>(1)
        }
        whenever(sp.getDouble(any<String>(), any())).doAnswer {
            (store[it.getArgument<String>(0)] as? Double) ?: it.getArgument<Double>(1)
        }
        whenever(sp.getString(any<String>(), any())).doAnswer {
            (store[it.getArgument<String>(0)] as? String) ?: it.getArgument<String>(1)
        }

        doAnswer { store[it.getArgument<String>(0)] = it.getArgument<Boolean>(1); null }.whenever(sp).putBoolean(any<String>(), any())
        doAnswer { store[it.getArgument<String>(0)] = it.getArgument<Int>(1); null }.whenever(sp).putInt(any<String>(), any())
        doAnswer { store[it.getArgument<String>(0)] = it.getArgument<Long>(1); null }.whenever(sp).putLong(any<String>(), any())
        doAnswer { store[it.getArgument<String>(0)] = it.getArgument<Double>(1); null }.whenever(sp).putDouble(any<String>(), any())
        doAnswer { store[it.getArgument<String>(0)] = it.getArgument<String>(1); null }.whenever(sp).putString(any<String>(), any())

        doAnswer { val k = it.getArgument<String>(0); store[k] = ((store[k] as? Int) ?: 0) + 1; null }.whenever(sp).incInt(any<String>())
        doAnswer { val k = it.getArgument<String>(0); store[k] = ((store[k] as? Long) ?: 0L) + 1L; null }.whenever(sp).incLong(any<String>())

        sut = PreferencesImpl(sp)
    }

    // ---------------------------------------------------------------------------------------------
    // Reflection helpers over real enum keys
    // ---------------------------------------------------------------------------------------------

    @Test
    fun isUnitDependentTrueForUnitDoubleKeysAndFalseOtherwise() {
        assertThat(sut.isUnitDependent("low_mark")).isTrue()
        assertThat(sut.isUnitDependent("high_mark")).isTrue()
        assertThat(sut.isUnitDependent("lgsThreshold")).isTrue()
        // A regular BooleanKey is not unit-dependent
        assertThat(sut.isUnitDependent("simple_mode")).isFalse()
        // Unknown key is not unit-dependent
        assertThat(sut.isUnitDependent("no_such_key")).isFalse()
    }

    @Test
    fun getByStringResolvesKnownKeyAndReturnsNullForUnknown() {
        assertThat(sut.get("simple_mode")).isEqualTo(BooleanKey.GeneralSimpleMode)
        assertThat(sut.getIfExists("simple_mode")).isEqualTo(BooleanKey.GeneralSimpleMode)
        assertThat(sut.get("appwidget_")).isEqualTo(IntComposedKey.WidgetOpacity)
        assertThat(sut.get("definitely_not_a_key")).isNull()
        assertThat(sut.getIfExists("definitely_not_a_key")).isNull()
    }

    @Test
    fun isExportableKeyMatchesExactKeysAndComposedPrefixesButNotJunk() {
        // Exact enum key match
        assertThat(sut.isExportableKey("simple_mode")).isTrue()
        // ComposedKey prefix match (IntComposedKey.WidgetOpacity has key "appwidget_")
        assertThat(sut.isExportableKey("appwidget_0")).isTrue()
        assertThat(sut.isExportableKey("appwidget_12")).isTrue()
        // Junk that neither matches an exact key nor a composed prefix
        assertThat(sut.isExportableKey("totally_unrelated_key")).isFalse()
    }

    @Test
    fun getDependingOnReturnsDependencyAndNegativeDependencyChildren() {
        val wearControlDependents = sut.getDependingOn("wearcontrol")
        assertThat(wearControlDependents).containsAtLeast(
            BooleanKey.WearWizardBg,
            BooleanKey.WearWizardTt,
            BooleanKey.WearWizardTrend,
            BooleanKey.WearWizardCob,
            BooleanKey.WearWizardIob
        )

        // ApsUseAutosens declares a negativeDependency on ApsUseDynamicSensitivity (key "use_dynamic_sensitivity")
        val dynSensDependents = sut.getDependingOn("use_dynamic_sensitivity")
        assertThat(dynSensDependents).contains(BooleanKey.ApsUseAutosens)
    }

    @Test
    fun getAllPreferenceKeysReturnsOnlyPreferenceKeysAndExcludesNonPreferenceEnums() {
        val all = sut.getAllPreferenceKeys()
        assertThat(all).isNotEmpty()
        // Every element must be a PreferenceKey
        assertThat(all.all { it is PreferenceKey }).isTrue()
        // Contains a well-known PreferenceKey enum
        assertThat(all).contains(BooleanKey.GeneralSimpleMode)
        // IntNonKey / LongNonKey are NonPreferenceKey-only enums and must be absent
        assertThat(all.map { it.key }).doesNotContain(IntNonKey.TddCycleOffset.key)
        assertThat(all.map { it.key }).doesNotContain(LongNonKey.LastCleanupRun.key)
    }

    @Test
    fun registerPreferencesIsIdempotentForAlreadyRegisteredClass() {
        val before = sut.getAllPreferenceKeys().size
        // BooleanKey is already registered in the default prefsList -> adding again is a no-op
        sut.registerPreferences(BooleanKey::class.java)
        val after = sut.getAllPreferenceKeys().size
        assertThat(after).isEqualTo(before)
    }

    // ---------------------------------------------------------------------------------------------
    // SP-delegating value logic + StateFlow cache
    // ---------------------------------------------------------------------------------------------

    @Test
    fun putUpdatesSpAndCachedObserveFlow() {
        val key = BooleanKey.WearWizardBg

        // observe() seeds a cached flow from the current SP value (default = true)
        val flow = sut.observe(key)
        assertThat(flow.value).isTrue()

        // observe() is stable across calls (getOrPut returns the same instance)
        assertThat(sut.observe(key)).isSameInstanceAs(flow)

        // put() writes to SP and refreshes the cached flow
        sut.put(key, false)
        verify(sp).putBoolean(eq(key.key), eq(false))
        assertThat(flow.value).isFalse()
        assertThat(sut.observe(key).value).isFalse()

        // Round-trip: reading back through get() reflects the stored value
        assertThat(sut.get(key)).isFalse()
    }

    @Test
    fun getIfExistsReturnsNullWhenAbsentAndValueWhenPresent() {
        val key = IntNonKey.TddCycleOffset

        // Absent -> null (sp.contains == false)
        assertThat(sut.getIfExists(key)).isNull()

        // Present -> stored value
        sut.put(key, 7)
        verify(sp).putInt(eq(key.key), eq(7))
        assertThat(sut.getIfExists(key)).isEqualTo(7)
    }

    @Test
    fun incIntCallsSpIncAndRefreshesCachedFlow() {
        val key = IntNonKey.TddCycleOffset
        sut.put(key, 4)

        // Cache the flow via observe before inc so refresh path is exercised
        val flow = sut.observe(key)
        assertThat(flow.value).isEqualTo(4)

        sut.inc(key)
        verify(sp).incInt(eq(key.key))
        assertThat(flow.value).isEqualTo(5)
    }

    @Test
    fun incLongCallsSpIncAndRefreshesCachedFlow() {
        val key = LongNonKey.LastCleanupRun
        sut.put(key, 10L)

        val flow = sut.observe(key)
        assertThat(flow.value).isEqualTo(10L)

        sut.inc(key)
        verify(sp).incLong(eq(key.key))
        assertThat(flow.value).isEqualTo(11L)
    }

    @Test
    fun allMatchingIntsSplitsComposedKeysByPrefix() {
        // Seed SP with composed keys sharing the IntComposedKey.WidgetOpacity prefix ("appwidget_")
        // plus an unrelated key that must be ignored.
        sut.put(IntComposedKey.WidgetOpacity, 0, value = 25)
        sut.put(IntComposedKey.WidgetOpacity, 12, value = 50)
        sut.put(BooleanKey.GeneralSimpleMode, false)

        val matching = sut.allMatchingInts(IntComposedKey.WidgetOpacity)
        assertThat(matching).containsExactly(0, 12)
    }

    @Test
    fun unitDoublePreferenceKeyGetThrowsButPutDelegatesToSp() {
        val key = UnitDoubleKey.OverviewLowMark

        val getError = runCatching { sut.get(key) }.exceptionOrNull()
        assertThat(getError).isInstanceOf(IllegalStateException::class.java)
        assertThat(getError).hasMessageThat().isEqualTo("Not implemented")

        val getIfExistsError = runCatching { sut.getIfExists(key) }.exceptionOrNull()
        assertThat(getIfExistsError).isInstanceOf(IllegalStateException::class.java)
        assertThat(getIfExistsError).hasMessageThat().isEqualTo("Not implemented")

        // put() still delegates to sp.putDouble even though get() is not implemented
        sut.put(key, 99.0)
        verify(sp).putDouble(eq(key.key), eq(99.0))
        assertThat(store[key.key]).isEqualTo(99.0)
    }

    @Test
    fun observeReturnsStableCachedFlowSeededFromSpValue() {
        val key = BooleanKey.WearWizardCob // default value = true
        store[key.key] = false // pre-seed SP so the flow is seeded from stored value

        val first = sut.observe(key)
        assertThat(first.value).isFalse()

        // getOrPut => same instance on subsequent calls
        val second = sut.observe(key)
        assertThat(second).isSameInstanceAs(first)

        // A put that targets the SAME key updates the single cached flow
        sut.put(key, true)
        verify(sp, times(1)).putBoolean(eq(key.key), eq(true))
        assertThat(first.value).isTrue()
    }
}
