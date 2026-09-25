package app.aaps.keys

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Production code reads preferences through `Preferences`, not through the raw store.
 *
 * ## Why
 *
 * A key reached through `Preferences` is a registered key: the registry knows it, the snapshot in
 * `prefs-schema.txt` records it, and `isExportableKey` can answer for it. A key reached through `SP`
 * or `SharedPreferences` directly is invisible to all three - and an import cannot tell a key it has
 * never heard of from rubbish an old version left behind. It leaves both alone today (see
 * `_docs/IMPORT.md`), so an invisible key is one that nothing can ever clean up.
 *
 * That is not hypothetical. CareLevo kept a running patch's state - `carelevo_patch_info`, the four
 * infusion records - in raw keys until 2026-09-22. They are registered now, and this test is what
 * stops them, or anything else, drifting back.
 *
 * ## The allowlist
 *
 * Every entry needs a reason, and the reasons are of three kinds: code that IS the store, code that
 * moves the whole store rather than reading a setting, and code that runs before DI exists. A fourth
 * kind - "work to do" - is listed separately in [toDo] so it is visibly a debt and can only shrink.
 *
 * ## What it cannot see
 *
 * Source text. A helper called through an interface, or reflection, is invisible to it. `wear` is
 * excluded entirely rather than allowlisted file by file: it is a separate app with its own
 * `PreferencesImpl` and its own registration, so the rule here does not apply to it.
 */
class RawPreferenceStoreScanTest {

    /** Reaching the store without going through `Preferences`. */
    private val patterns = listOf(
        Regex("""\bsharedPreferences\.SP\b"""),
        Regex("""\bgetSharedPreferences\s*\("""),
        Regex("""\bandroid\.content\.SharedPreferences\b"""),
        Regex("""\bsharedPreferences\.KeyValueStore\b""")
    )

    /**
     * Allowed, each with the reason it is allowed. Keyed by path suffix so a file move is caught as a
     * stale entry rather than silently continuing to allow something.
     */
    private val allowed: Map<String, String> = mapOf(
        // --- It IS the store ---
        "shared/impl/src/androidMain/kotlin/app/aaps/shared/impl/sharedPreferences/SPImpl.kt" to "the Android store implementation",
        "desktop/shell/src/main/kotlin/app/aaps/desktop/shell/prefs/DesktopSp.kt" to "the desktop store implementation",
        "ios/shell/src/iosMain/kotlin/app/aaps/ios/shell/prefs/IosSp.kt" to "the iOS store implementation",
        "implementation/src/commonMain/kotlin/app/aaps/implementation/sharedPreferences/PreferencesImpl.kt" to "Preferences itself, which is what everything else is supposed to use",
        "shared/tests/src/androidMain/kotlin/app/aaps/shared/tests/SharedPreferencesMock.kt" to "the test double for the store; it lives in a main source set so tests in other modules can use it",

        // --- Moves the whole store, not a setting ---
        // These are the import, the export and the cloud copies of them. They are SUPPOSED to see every
        // key, including ones they have never heard of, so going through `Preferences` would defeat
        // the purpose rather than serve it.
        "implementation/src/androidMain/kotlin/app/aaps/implementation/maintenance/ImportExportPrefsImpl.kt" to "the import/export itself - it copies the whole store by design",
        "implementation/src/commonMain/kotlin/app/aaps/implementation/maintenance/LocalImportExportPrefs.kt" to "the multiplatform half of the same import/export",
        "implementation/src/commonMain/kotlin/app/aaps/implementation/maintenance/formats/PrefsTransfer.kt" to "reads and writes the whole store for a transfer",
        // Deliberately below `Preferences`, and the reason is the opposite of carelessness. It writes
        // an import as ONE `edit(commit = true)` and then calls `Preferences.reloadFromStore()` once.
        // Going through `preferences.put` per key would be ~500 separate commits, would let a live
        // collector see a half-imported store, and would stamp and publish every Bidirectional key on
        // the way past. It resolves each name to its key first, so the values are typed - it is not
        // bypassing the key system, only the per-key write.
        "implementation/src/commonMain/kotlin/app/aaps/implementation/maintenance/PreferenceImportApplier.kt" to
            "applies an import as one batched write, then republishes through Preferences.reloadFromStore()",
        "implementation/src/commonMain/kotlin/app/aaps/implementation/maintenance/cloud/CloudStorageManager.kt" to "moves export files to and from cloud storage",
        "implementation/src/commonMain/kotlin/app/aaps/implementation/maintenance/cloud/GoogleDriveProvider.kt" to "cloud export, shared part",
        "implementation/src/androidMain/kotlin/app/aaps/implementation/maintenance/cloud/AndroidGoogleDriveProvider.kt" to "cloud export, Android part",
        "implementation/src/iosMain/kotlin/app/aaps/implementation/maintenance/cloud/IosGoogleDriveProvider.kt" to "cloud export, iOS part",
        "implementation/src/jvmMain/kotlin/app/aaps/implementation/maintenance/cloud/DesktopGoogleDriveProvider.kt" to "cloud export, desktop part",
        "implementation/src/commonMain/kotlin/app/aaps/implementation/maintenance/cloud/GoogleTokens.kt" to "holds the cloud token store the providers above use",

        // --- Runs before DI exists ---
        "implementation/src/androidMain/kotlin/app/aaps/implementation/utils/fabric/FabricPrivacyImpl.kt" to "reads before the graph is built (DI cycle)",
        "implementation/src/iosMain/kotlin/app/aaps/implementation/utils/fabric/FabricPrivacyImpl.kt" to "same, on iOS",
        "core/ui/src/androidMain/kotlin/app/aaps/core/ui/locale/LocaleHelper.kt" to "reads the language before DI exists",

        // --- Wiring, which hands the store to the things above ---
        "app/src/main/kotlin/app/aaps/di/metro/AppAndroidBindings.kt" to "DI wiring",
        "app/src/main/kotlin/app/aaps/di/metro/AppRootGraph.kt" to "DI wiring",
        "app/src/main/kotlin/app/aaps/di/metro/MetroGraphs.kt" to "DI wiring",
        "app/src/main/kotlin/app/aaps/di/metro/SharedImplBindings.kt" to "DI wiring",
        "desktop/shell/src/main/kotlin/app/aaps/desktop/shell/di/DesktopPlatformBindings.kt" to "DI wiring, desktop",
        "ios/shell/src/iosMain/kotlin/app/aaps/ios/shell/di/IosPlatformBindings.kt" to "DI wiring, iOS",
        "ios/shell/src/iosMain/kotlin/app/aaps/ios/shell/di/IosProbeGraph.kt" to "DI wiring, iOS probe graph",
        "shared/clientbindings/src/commonMain/kotlin/app/aaps/shared/clientbindings/ClientGraphBindings.kt" to "DI wiring, shared client bindings",
    )

    /**
     * Known debt, and the only part of this list that is allowed to exist. Each entry is a pump driver
     * keeping real state in unregistered keys, which is exactly what 4.1 decision 4's removal rule
     * would delete. **This map may only ever get shorter.**
     */
    private val toDo: Map<String, String> = mapOf(
        "pump/combov2/src/main/kotlin/info/nightscout/pump/combov2/AAPSPumpStateStore.kt" to
            "ComboV2 pump state. Deferred WITH a migration (agreed 2026-09-22) - the values are live pump state, so they have to be carried across, not abandoned. Plan 3.1.2.",
        "pump/combov2/src/main/kotlin/info/nightscout/pump/combov2/ComboV2Plugin.kt" to "ComboV2, same migration",
        "pump/combov2/src/main/kotlin/info/nightscout/pump/combov2/Delegates.kt" to "ComboV2, same migration",
        "pump/insight/src/main/kotlin/app/aaps/pump/insight/utils/PairingDataStorage.kt" to
            "Insight pump pairing. Open question 6.2: moving it into registered keys would make 'restore pump configuration' work after a reinstall, but it would also put a pump secret in the export file. Needs an answer before it moves.",
    )

    @Test
    fun `production code does not reach the preference store directly`() {
        val hits = scan()

        val unexpected = hits.filterNot { hit ->
            allowed.keys.any { hit.endsWith(it) } || toDo.keys.any { hit.endsWith(it) }
        }

        assertThat(unexpected).isEmpty()
    }

    /**
     * An allowlist entry that matches nothing is worse than no entry: it reads as a permission that is
     * still needed, and it hides the fact that the file moved or the use went away.
     */
    @Test
    fun `no allowlist entry is stale`() {
        val hits = scan()

        val stale = (allowed.keys + toDo.keys).filterNot { entry -> hits.any { it.endsWith(entry) } }

        assertThat(stale).isEmpty()
    }

    /** Production Kotlin, excluding tests, build output, and `wear` - which has its own store. */
    private fun scan(): List<String> {
        val skip = listOf("/build/", "/src/test", "/src/androidTest", "/src/androidHostTest", "/src/commonTest", "/src/iosTest", "/wear/")
        val root = repoRoot()
        val files = root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .map { it to it.invariantSeparatorsPath }
            .filterNot { (_, path) -> skip.any { path.contains(it) } }
            .toList()
        check(files.size > 500) { "Found only ${files.size} source files - the walk broke" }

        return files
            .filter { (file, _) ->
                val code = stripComments(file.readText())
                patterns.any { it.containsMatchIn(code) }
            }
            .map { (_, path) -> path }
            .sorted()
    }

    /** A KDoc that NAMES `android.content.SharedPreferences` is not a use of it - `NonPreferenceKey` does exactly that. */
    private fun stripComments(text: String): String {
        val out = StringBuilder(text.length)
        var inBlock = false
        for (line in text.lineSequence()) {
            var i = 0
            val sb = StringBuilder()
            while (i < line.length) {
                if (inBlock) {
                    val end = line.indexOf("*/", i)
                    if (end < 0) i = line.length else { inBlock = false; i = end + 2 }
                } else {
                    val block = line.indexOf("/*", i)
                    val lineComment = line.indexOf("//", i)
                    when {
                        lineComment >= 0 && (block < 0 || lineComment < block) -> { sb.append(line, i, lineComment); i = line.length }
                        block >= 0                                             -> { sb.append(line, i, block); inBlock = true; i = block + 2 }
                        else                                                   -> { sb.append(line, i, line.length); i = line.length }
                    }
                }
            }
            out.append(sb).append('\n')
        }
        return out.toString()
    }

    private fun repoRoot(): File {
        var dir: File? = File(System.getProperty("user.dir"))
        while (dir != null && !File(dir, "settings.gradle").exists() && !File(dir, "settings.gradle.kts").exists()) dir = dir.parentFile
        return checkNotNull(dir) { "Could not find the repository root above ${System.getProperty("user.dir")}" }
    }
}
