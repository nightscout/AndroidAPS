package app.aaps.di.metro

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.io.File

/**
 * No instrumented test may `@Inject` a type that Metro owns.
 *
 * Dagger does not read Metro's `@SingleIn`. If a class has an `@Inject` constructor, Hilt will happily
 * build the test **its own second copy** - and for a pump plugin that means the test drives an object
 * the running app has never heard of, while the real one sits in the plugin list. Nothing fails to
 * compile and no assertion in the test itself can notice.
 *
 * That is not hypothetical. When the pump drivers moved to `@SingleIn`, eight e2e tests went on
 * injecting `DanaRv2Plugin`, `DanaRSPlugin`, `EquilPumpPlugin`, `DanaPump` and `EquilManager` from
 * Hilt. Only a *different* symptom made it visible: six sibling types had moved into Metro binding
 * containers, so Hilt had no binding at all for those and the graph failed with
 * `[Dagger/MissingBinding]`. Had those six not moved in the same change, the duplicate plugins would
 * have shipped silently. The fix for both is the same - read through `MetroGraphs.pumps`.
 *
 * ## Why this test reads source instead of using reflection
 *
 * Every other guard here walks compiled classes. It cannot be done for this one: `androidTest` classes
 * are not on the unit-test classpath, so there is nothing to reflect over. Reading the source is the
 * only option available from a JVM test, and it is the reason for the two `check(...)`s below - a
 * source scan that finds no files, or a rule list that is empty, reports perfect coverage of nothing.
 *
 * The rule list is [PumpAccessors]' own accessor types, so this widens automatically as that interface
 * grows. It deliberately does not try to know about every Metro-owned type in the tree; the pump
 * objects are where the damage is, because they hold live pump state.
 */
class HiltTestInjectionTest {

    @Test
    fun `no instrumented test injects a Metro owned pump type`() {
        val guarded = PumpAccessors::class.java.declaredMethods
            .map { it.returnType.simpleName }
            .toSet()
        check(guarded.isNotEmpty()) { "PumpAccessors exposes nothing - this guard has stopped guarding" }

        val sources = androidTestSources()
        check(sources.isNotEmpty()) { "Found no androidTest sources - the file walk broke" }

        val offenders = sources.flatMap { file ->
            file.readLines()
                .filter { INJECTED_FIELD.containsMatchIn(it) }
                .mapNotNull { line -> INJECTED_FIELD.find(line)?.groupValues?.get(1) }
                // The declared type, minus any Provider<>/Lazy<> wrapper and any package prefix.
                .map { it.substringAfterLast('<').substringBefore('>').substringAfterLast('.').trim() }
                .filter { it in guarded }
                .map { "${file.name} injects $it from Hilt - read it from MetroGraphs.pumps instead" }
        }

        assertThat(offenders).isEmpty()
    }

    private fun androidTestSources(): List<File> =
        // From the compiled output back to the module root, rather than a hard-coded path: the source
        // set layout moves when a module is flipped to multiplatform, and a wrong literal path finds
        // nothing and passes.
        generateSequence(File(javaClass.protectionDomain.codeSource.location.toURI())) { it.parentFile }
            .firstOrNull { File(it, "src/androidTest").isDirectory }
            ?.let { File(it, "src/androidTest").walkTopDown().filter { f -> f.extension == "kt" }.toList() }
            .orEmpty()

    private companion object {

        val INJECTED_FIELD = Regex("""@Inject\s+lateinit\s+var\s+\w+\s*:\s*([\w.<>@ ]+)""")
    }
}
