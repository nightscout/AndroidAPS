package app.aaps.keys

import app.aaps.core.keys.interfaces.ComposedKey
import app.aaps.core.keys.interfaces.NonPreferenceKey
import app.aaps.di.metro.AppRootGraph
import app.aaps.shared.tests.aapsClassesOnClasspath
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.io.File

/**
 * A checked-in record of every preference key and whether it leaves the phone.
 *
 * ## Why this exists
 *
 * `exportable` decides whether a key is written into a settings export - and therefore whether
 * another phone's copy of it can arrive in an import. It is DEFAULTED to `true` on
 * [NonPreferenceKey], which is right about 91% of the time (25 of roughly 287 entries opt out), so
 * the default is kept rather than making 287 entries declare it by hand.
 *
 * That decision is only safe with this file. The three pump-runtime keys in `StringNonKey` were
 * exportable for a long time with nobody typing it: they sat in that enum beside fourteen neighbours
 * that carried the `exportable = false` they were missing, and nothing ever showed the difference.
 * A default is not the problem; an INVISIBLE default is. So the classification is written down here,
 * a new key lands in a reviewable diff, and flipping one cannot pass CI in silence.
 *
 * ## How it works, and what to do when it fails
 *
 * Like Room's exported schema. A local run **rewrites** the snapshot and fails, so the change shows
 * up in `git status` and gets committed with the key that caused it. CI only compares. If this test
 * fails on your machine: read the diff, satisfy yourself the change is what you meant - especially
 * any line whose `exportable` flipped to `true` - then commit the file.
 *
 * ## What it deliberately does NOT do yet
 *
 * 3.3 asks for one snapshot per module, migration-step cross-checks and tombstones. Those belong
 * with 4.2 steps 2 and 4, and they need `kind`, which does not exist. This is the part that unblocks
 * step 2 on its own. One global snapshot also sidesteps 8.D's fourth finding by construction: there
 * is no per-module file left behind when a module leaves the build - its keys simply stop being
 * scanned, which shows up here as a removal to confirm.
 *
 * When `kind` lands, add it as a third column. The mechanism is the same.
 */
class PreferenceKeySnapshotTest {

    /**
     * Compiled classes rather than the graph: reading the live registry would mean constructing every
     * plugin, and `PreferencesImpl` only learns a plugin's keys when that plugin is built. The scan
     * sees them all without Android.
     */
    private fun allKeys(): List<NonPreferenceKey> =
        aapsClassesOnClasspath(listOf(AppRootGraph::class.java))
            .filter { NonPreferenceKey::class.java.isAssignableFrom(it) && it.isEnum }
            .flatMap { it.enumConstants.orEmpty().toList() }
            .filterIsInstance<NonPreferenceKey>()

    /** `<stored key>|<kotlin type>|<exportable>`, sorted, one per line. */
    private fun render(keys: List<NonPreferenceKey>): String =
        keys.map { key ->
            // A composed key stores many values under one prefix, so record the format it composes
            // with - the prefix alone would not say which stored strings belong to it.
            val stored = if (key is ComposedKey) key.key + key.format else key.key
            val type = key::class.java.simpleName
            "$stored|$type|${key.exportable}"
        }.distinct().sorted().joinToString("\n")

    @Test
    fun `every preference key and its exportable flag is recorded`() {
        val keys = allKeys()
        check(keys.size > 200) { "Found only ${keys.size} keys - the classpath scan broke, and an empty scan would rewrite the snapshot to nothing" }

        val rendered = render(keys) + "\n"
        val snapshot = File(repoRoot(), "app/src/testFull/resources/prefs-schema.txt")

        // Compared with the line endings normalised. Git checks this file out with CRLF on Windows
        // while the text built above always uses LF, so a byte comparison fails after every checkout,
        // rewrites the file, and fails again after the next one - committing the rewrite does not
        // help, because git re-normalises it on the way back out. Only the content is the record.
        val onDisk = snapshot.takeIf { it.exists() }?.readText()?.replace("\r\n", "\n")

        if (onDisk != rendered) {
            snapshot.parentFile.mkdirs()
            snapshot.writeText(rendered)
            // Fail on purpose: a rewritten snapshot that passed would be committed by accident, or
            // not committed at all, and CI would then be comparing against a file nobody read.
            assertThat("snapshot rewritten at ${snapshot.path} - read the diff and commit it").isEmpty()
        }
    }

    /**
     * The stored key is the only thing connecting a user's saved value to the code that reads it, so
     * two keys sharing one string means one of them reads the other's value.
     */
    @Test
    fun `no two keys share a stored key string`() {
        val duplicates = allKeys()
            .groupBy { if (it is ComposedKey) it.key + it.format else it.key }
            .filter { (_, sharing) -> sharing.map { it::class.java.name + "." + it }.distinct().size > 1 }
            .keys

        assertThat(duplicates).isEmpty()
    }

    private fun repoRoot(): File {
        var dir: File? = File(System.getProperty("user.dir"))
        while (dir != null && !File(dir, "settings.gradle").exists() && !File(dir, "settings.gradle.kts").exists()) dir = dir.parentFile
        return checkNotNull(dir) { "Could not find the repository root above ${System.getProperty("user.dir")}" }
    }
}
