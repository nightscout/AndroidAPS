package app.aaps.keys

import app.aaps.core.keys.interfaces.ComposedKey
import app.aaps.core.keys.interfaces.NonPreferenceKey
import app.aaps.di.metro.AppRootGraph
import app.aaps.shared.tests.aapsClassesOnClasspath
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * A stored key must belong to exactly one [ComposedKey].
 *
 * ## Why this is a test and not a rule someone remembers
 *
 * A [ComposedKey] stores many values under one prefix - `appwidget_` plus a widget id, `log_` plus a
 * tag - so the only way to get from a stored string back to the key that owns it is to match the
 * prefix. `PreferencesImpl.isExportableKey` already does exactly that, with a bare
 * `key.startsWith(it.key)`, and the replacement for `sp.clear()` needs the same match to decide what
 * an imported key IS before it can write it with the right type.
 *
 * That match is only sound while no prefix swallows another. It is not a property anybody can hold in
 * their head: it is a property of all the key enums taken together, and it breaks when someone adds a
 * perfectly reasonable key to one file without reading the others.
 *
 * It had already broken. `IntComposedKey.WidgetOpacity` was `appwidget_` and
 * `BooleanComposedKey.WidgetUseBlack` was `appwidget_use_black_`, so the stored key
 * `appwidget_use_black_0` matched both - and `prefsList` is a `LinkedHashSet`, so which one answered
 * depended on registration order. Resolving to the wrong one reads a Boolean as an Int. The fix was
 * to move the black-background flag off the `appwidget_` prefix entirely.
 *
 * ## What this does NOT check
 *
 * Two prefixes end without a separator: `snoozedTo` (`LongComposedKey`) and `dana_ble5_pairingkey`
 * (`DanaStringComposedKey`). Neither collides today, so neither is asserted here - but both are one
 * new key away from colliding, and this test is what would catch it. Do not "fix" them by renaming
 * without a migration: the stored values are live.
 */
class ComposedKeyPrefixTest {

    private fun allKeys(): List<NonPreferenceKey> =
        aapsClassesOnClasspath(listOf(AppRootGraph::class.java))
            .filter { NonPreferenceKey::class.java.isAssignableFrom(it) && it.isEnum }
            .flatMap { it.enumConstants.orEmpty().toList() }
            .filterIsInstance<NonPreferenceKey>()

    private fun name(key: NonPreferenceKey) = key::class.java.simpleName + "." + key

    @Test
    fun `no composed key prefix swallows another`() {
        val composed = allKeys().filterIsInstance<ComposedKey>()
        check(composed.size > 10) { "Found only ${composed.size} composed keys - the classpath scan broke" }

        val clashes = composed.flatMap { outer ->
            composed
                .filter { it !== outer && it.key.startsWith(outer.key) }
                .map { inner ->
                    "'${inner.key}${inner.format}' (${name(inner as NonPreferenceKey)}) is hidden by " +
                        "'${outer.key}${outer.format}' (${name(outer as NonPreferenceKey)}) - " +
                        "a stored '${inner.key}<arg>' matches both, and which one answers depends on registration order"
                }
        }

        assertThat(clashes).isEmpty()
    }

    @Test
    fun `no plain key starts with a composed key prefix`() {
        val keys = allKeys()
        val composed = keys.filterIsInstance<ComposedKey>()
        val plain = keys.filter { it !is ComposedKey }
        check(plain.size > 200) { "Found only ${plain.size} plain keys - the classpath scan broke" }

        val clashes = plain.flatMap { p ->
            composed
                .filter { p.key.startsWith(it.key) }
                .map { c ->
                    "plain key '${p.key}' (${name(p)}) starts with composed prefix " +
                        "'${c.key}${c.format}' (${name(c as NonPreferenceKey)}) - the prefix match would claim it"
                }
        }

        assertThat(clashes).isEmpty()
    }
}
