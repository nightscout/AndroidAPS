package app.aaps.core.keys

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * The resolver is what lets an import write through `Preferences` instead of the raw store, so a
 * wrong answer here does not fail loudly - it writes a value under a key the user never set.
 *
 * Built from the real key enums rather than fixtures, so these stay honest if a key moves.
 */
class PreferenceKeyResolverTest {

    private val sut = PreferenceKeyResolver(
        BooleanKey.entries + BooleanComposedKey.entries + IntComposedKey.entries +
            StringKey.entries + LongComposedKey.entries
    )

    @Test fun `a plain key resolves to itself`() {
        val resolved = sut.resolve(BooleanKey.GeneralSimpleMode.key)

        assertThat(resolved).isEqualTo(ResolvedKey.Plain(BooleanKey.GeneralSimpleMode, KeyCategory.General))
    }

    @Test fun `a composed key resolves with its argument`() {
        val stored = IntComposedKey.WidgetOpacity.composeKey(12)

        val resolved = sut.resolve(stored)

        assertThat(resolved).isEqualTo(ResolvedKey.Composed(IntComposedKey.WidgetOpacity, "12", KeyCategory.General))
    }

    /**
     * The collision that `ComposedKeyPrefixTest` was written for. `appwidget_use_black_0` used to
     * match both `appwidget_use_black_` and `appwidget_`, and which one answered depended on
     * registration order - resolving to the opacity key reads a Boolean as an Int. The flag now lives
     * under `widget_use_black_`, and this pins that the two no longer meet.
     */
    @Test fun `the widget flag and the widget opacity do not resolve to each other`() {
        val black = BooleanComposedKey.WidgetUseBlack.composeKey(0)
        val opacity = IntComposedKey.WidgetOpacity.composeKey(0)

        assertThat(black).doesNotContain("appwidget_")
        assertThat(sut.resolve(black)).isEqualTo(ResolvedKey.Composed(BooleanComposedKey.WidgetUseBlack, "0", KeyCategory.General))
        assertThat(sut.resolve(opacity)).isEqualTo(ResolvedKey.Composed(IntComposedKey.WidgetOpacity, "0", KeyCategory.General))
    }

    @Test fun `a name that only looks like a composed key does not resolve`() {
        // Starts with the appwidget_ prefix but the argument is not a whole number, and %d says it
        // must be. Without this check it would resolve and then fail at the put.
        assertThat(sut.resolve("appwidget_not_a_number")).isNull()
        // The bare prefix has no argument at all.
        assertThat(sut.resolve(IntComposedKey.WidgetOpacity.key)).isNull()
    }

    @Test fun `a negative argument round-trips`() {
        // %d means "a whole number", which is what composeKey accepts, so the resolver must not be
        // stricter than the thing it inverts.
        val stored = IntComposedKey.WidgetOpacity.composeKey(-3)

        assertThat(sut.resolve(stored)).isEqualTo(ResolvedKey.Composed(IntComposedKey.WidgetOpacity, "-3", KeyCategory.General))
    }

    @Test fun `a percent-s key takes any non-empty argument`() {
        val stored = BooleanComposedKey.Log.composeKey("PUMP")

        assertThat(sut.resolve(stored)).isEqualTo(ResolvedKey.Composed(BooleanComposedKey.Log, "PUMP", KeyCategory.General))
    }

    // ---- classification (plan 4.1.6) ----

    private val pumpKey = StringKey.GeneralUnits          // stand-ins: any key, put in the owner set
    private val otherKey = BooleanKey.OverviewUseBolusAdvisor

    private val classified = PreferenceKeyResolver(
        keys = BooleanKey.entries + StringKey.entries + BooleanComposedKey.entries + IntComposedKey.entries,
        pumpOwned = listOf(pumpKey),
        otherPluginOwned = listOf(otherKey)
    )

    @Test fun `a key owned by a pump driver is pump internal`() {
        assertThat(classified.resolve(pumpKey.key)?.category).isEqualTo(KeyCategory.PumpInternal)
    }

    @Test fun `a key owned by another plugin is other plugin internal`() {
        assertThat(classified.resolve(otherKey.key)?.category).isEqualTo(KeyCategory.OtherPluginInternal)
    }

    @Test fun `a key owned by no plugin is general`() {
        assertThat(classified.resolve(BooleanKey.GeneralSimpleMode.key)?.category).isEqualTo(KeyCategory.General)
    }

    /**
     * It lives in a core enum, so by ownership it is General - but it is not a value, it decides
     * whether a plugin runs. Writing it as a value would leave the store and the running plugins
     * disagreeing, so the identity check must win over ownership.
     */
    @Test fun `ConfigBuilderEnabled is its own category, whoever owns it`() {
        val stored = BooleanComposedKey.ConfigBuilderEnabled.composeKey("PUMP_SomePumpPlugin")

        val resolved = classified.resolve(stored)

        assertThat(resolved?.category).isEqualTo(KeyCategory.ConfigBuilderEnabled)
        assertThat((resolved as ResolvedKey.Composed).argument).isEqualTo("PUMP_SomePumpPlugin")
    }

    @Test fun `ConfigBuilderEnabled still wins when it is also in an owner set`() {
        val withIt = PreferenceKeyResolver(
            keys = BooleanComposedKey.entries,
            pumpOwned = listOf(BooleanComposedKey.ConfigBuilderEnabled)
        )
        val stored = BooleanComposedKey.ConfigBuilderEnabled.composeKey("PUMP_SomePumpPlugin")

        assertThat(withIt.resolve(stored)?.category).isEqualTo(KeyCategory.ConfigBuilderEnabled)
    }

    /**
     * An empty pump set must not quietly mean "no pump keys": that would make a "keep the pump
     * settings" import skip nothing and overwrite the lot. It classifies as General, and the caller
     * is responsible for passing the sets - see the note on PreferenceKeyResolver.
     */
    @Test fun `with no owner sets everything is general`() {
        assertThat(sut.resolve(pumpKey.key)?.category).isEqualTo(KeyCategory.General)
        assertThat(sut.resolve(otherKey.key)?.category).isEqualTo(KeyCategory.General)
    }

    @Test fun `an unknown name resolves to nothing rather than guessing`() {
        // Normal, not an error: a newer AAPS export, or a client build that never constructed the
        // plugin that owns this key.
        assertThat(sut.resolve("some_key_from_a_newer_version")).isNull()
        assertThat(sut.canResolve("some_key_from_a_newer_version")).isFalse()
    }

    @Test fun `resolution does not depend on the order keys are handed in`() {
        val forward = PreferenceKeyResolver(IntComposedKey.entries + BooleanComposedKey.entries)
        val backward = PreferenceKeyResolver((IntComposedKey.entries + BooleanComposedKey.entries).reversed())
        val stored = BooleanComposedKey.WidgetUseBlack.composeKey(7)

        assertThat(forward.resolve(stored)).isEqualTo(backward.resolve(stored))
    }
}
