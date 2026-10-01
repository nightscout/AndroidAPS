package app.aaps.keys

import app.aaps.core.keys.PreferenceKeyResolver
import app.aaps.core.keys.ResolvedKey
import app.aaps.core.keys.interfaces.ComposedKey
import app.aaps.core.keys.interfaces.NonPreferenceKey
import app.aaps.di.metro.AppRootGraph
import app.aaps.shared.tests.aapsClassesOnClasspath
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Every key this build has must survive the trip a settings import puts it through: composed into a
 * stored name on the way out, and resolved back to the same key on the way in.
 *
 * `PreferenceKeyResolverTest` checks the rules on a handful of keys it names. This one checks all of
 * them, which is the part that catches a key added next month in a module nobody thought about. The
 * two are not redundant: that one says the resolver is right, this one says the key set is
 * resolvable - and the second can break without the first changing at all.
 *
 * A failure here does NOT mean the resolver is broken. It almost certainly means a new key collides
 * with an existing prefix, or uses a format the resolver deliberately refuses. Read
 * `ComposedKeyPrefixTest` and the note on `PreferenceKeyResolver` before changing anything here.
 */
class PreferenceKeyRoundTripTest {

    private fun allKeys(): List<NonPreferenceKey> =
        aapsClassesOnClasspath(listOf(AppRootGraph::class.java))
            .filter { NonPreferenceKey::class.java.isAssignableFrom(it) && it.isEnum }
            .flatMap { it.enumConstants.orEmpty().toList() }
            .filterIsInstance<NonPreferenceKey>()

    private fun name(key: NonPreferenceKey) = key::class.java.simpleName + "." + key

    /** Arguments chosen to be legal for both formats, and awkward enough to catch a lazy match. */
    private fun samples(key: ComposedKey): List<Any> =
        if (key.format == "%d") listOf(0, 7, 1234) else listOf("a", "PUMP", "one_with_underscores")

    @Test
    fun `every key composes to a name that resolves back to it`() {
        val keys = allKeys()
        check(keys.size > 200) { "Found only ${keys.size} keys - the classpath scan broke" }
        val sut = PreferenceKeyResolver(keys)

        val failures = keys.flatMap { key ->
            if (key is ComposedKey)
                samples(key).mapNotNull { argument ->
                    val stored = key.composeKey(argument)
                    when (val resolved = sut.resolve(stored)) {
                        null                     -> "'$stored' (${name(key)}) resolves to nothing"
                        !is ResolvedKey.Composed -> "'$stored' (${name(key)}) resolved as ${resolved::class.simpleName}, expected Composed"
                        else                     ->
                            if (resolved.key !== key) "'$stored' (${name(key)}) resolved to ${name(resolved.key)}"
                            else if (resolved.argument != argument.toString()) "'$stored' (${name(key)}) gave back argument '${resolved.argument}', expected '$argument'"
                            else null
                    }
                }
            else
                listOfNotNull(
                    when (val resolved = sut.resolve(key.key)) {
                        null                  -> "'${key.key}' (${name(key)}) resolves to nothing"
                        !is ResolvedKey.Plain -> "'${key.key}' (${name(key)}) resolved as ${resolved::class.simpleName}, expected Plain"
                        else                  -> if (resolved.key !== key) "'${key.key}' (${name(key)}) resolved to ${name(resolved.key)}" else null
                    }
                )
        }

        assertThat(failures).isEmpty()
    }

    /**
     * The snapshot is the checked-in record of what an export can contain, so anything in it that the
     * resolver cannot read is a key the import would have to fall back to a raw write for. Today that
     * set should be empty; if it ever is not, the reason belongs next to the entry that caused it.
     */
    @Test
    fun `every exportable key in the snapshot is resolvable`() {
        val keys = allKeys()
        val sut = PreferenceKeyResolver(keys)

        val unresolvable = keys
            .filter { it.exportable }
            .mapNotNull { key ->
                val stored = if (key is ComposedKey) key.composeKey(samples(key).first()) else key.key
                if (sut.canResolve(stored)) null else "'$stored' (${name(key)})"
            }

        assertThat(unresolvable).isEmpty()
    }
}
