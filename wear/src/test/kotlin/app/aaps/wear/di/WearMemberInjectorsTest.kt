package app.aaps.wear.di

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File

/**
 * Guards [WearMemberInjectors] against drift.
 *
 * The map is hand written, one `@ClassKey` line per Android entry point, and nothing at compile time
 * says an entry is missing. A class that is absent still builds; it fails at runtime, when that screen
 * is opened, with "No Metro binding for ...". `dagger.android` had the same hazard and this module had
 * no test for it at all - `:app` has sixteen such guards, `:wear` had none.
 *
 * What this covers: every concrete class that extends one of the module's Metro bases must be in the
 * map. That is the case that drifts - someone adds an activity, copies a base class, and forgets the
 * one line here.
 *
 * What it does NOT cover: classes that call `injectMetroMembers` themselves rather than through a base
 * (watch faces, tiles, complication services). They reach the same map, but finding them reliably needs
 * more than a source scan, so they are still only caught at runtime.
 */
class WearMemberInjectorsTest {

    private val sourceRoot = File("src/main/kotlin").absoluteFile
    private val injectorSource = File("src/main/kotlin/app/aaps/wear/di/WearMemberInjectors.kt").absoluteFile

    private val mappedClasses: Set<String> by lazy {
        CLASS_KEY.findAll(injectorSource.readText()).map { it.groupValues[1] }.toSet()
    }

    /** Concrete classes declaring `: WearMetroActivity()` or `: WearMetroService()`. */
    private val baseExtenders: Map<String, String> by lazy {
        sourceRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file ->
                EXTENDS_BASE.findAll(file.readText()).map { it.groupValues[1] to file.name }
            }
            .toMap()
    }

    @Test
    fun `the guard itself has something to check`() {
        assertThat(injectorSource.isFile).isTrue()
        assertThat(mappedClasses).isNotEmpty()
        assertThat(baseExtenders).isNotEmpty()
    }

    @Test
    fun `every class extending a Metro base has an injector entry`() {
        val missing = baseExtenders.filterKeys { it !in mappedClasses }
        assertThat(missing).isEmpty()
    }

    /**
     * A stale entry is harmless at runtime but means the map no longer describes the module, and it
     * hides the real count. Metro already rejects an entry whose class has no injections at all, so
     * this only catches a class that was deleted or renamed.
     */
    @Test
    fun `no injector entry names a class that no longer exists`() {
        val allDeclared = sourceRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { DECLARES_CLASS.findAll(it.readText()).map { m -> m.groupValues[1] } }
            .toSet()
        val stale = mappedClasses.filter { it !in allDeclared }
        assertThat(stale).isEmpty()
    }

    private companion object {

        val CLASS_KEY = Regex("""@ClassKey\((\w+)::class\)""")
        val EXTENDS_BASE = Regex("""^(?:internal\s+)?class\s+(\w+)[^\n:]*:\s*WearMetro(?:Activity|Service)\(\)""", RegexOption.MULTILINE)
        val DECLARES_CLASS = Regex("""^(?:\s*)(?:open\s+|abstract\s+|internal\s+|sealed\s+)*class\s+(\w+)""", RegexOption.MULTILINE)
    }
}
