import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Turns a module's `res/values/strings.xml` into two generated Kotlin files, so preference keys can
 * name a string without naming an Android resource id.
 *
 * Why this exists: a key enum that says `titleResId = R.string.x` carries an `Int` that only means
 * something on Android, which is what stops the module compiling for iOS or a desktop JVM. The
 * generated `KeysStrings` object replaces that `Int` with a `TextRef.Named("x")` - a plain string
 * name that every platform can hold - while the generated id map keeps Android resolving through
 * AAPT exactly as before.
 *
 * The pair is generated from the same XML in one pass, so the two sides cannot drift: a string
 * deleted from `strings.xml` disappears from both, and any call site that still names it stops
 * compiling.
 *
 * This is deliberately not `Resources.getIdentifier()`. That is a reflective lookup R8 cannot see,
 * it would keep every string alive, and a typo would silently return 0. A generated map is plain
 * code: R8 sees literal `R.string.x` references, and a typo is a compile error.
 *
 * It also reports translation completeness per locale, which nothing else in this build does -
 * `MissingTranslation` and `ExtraTranslation` lint are disabled for every library module.
 */
abstract class GenerateKeyStringsTask : DefaultTask() {

    /** The module's `src/main/res` (or `src/androidMain/res`) directory. */
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val resDir: DirectoryProperty

    /** Package for both generated files. Must match the module's namespace, so `R` resolves. */
    @get:Input
    abstract val packageName: Property<String>

    /**
     * Which module owns these names, for example `keys` or `ui`.
     *
     * A string name is only unique inside one module - `ns_wifi_ssids` exists in both `:core:keys`
     * and `:core:ui` with different translations - so the resolver needs to know which map to look
     * in rather than guessing by order.
     */
    @get:Input
    abstract val owner: Property<String>

    /** Name of the platform neutral object, for example `KeysStrings`. */
    @get:Input
    abstract val objectName: Property<String>

    /** Name of the Android id map object, for example `KeysStringIds`. */
    @get:Input
    abstract val idsObjectName: Property<String>

    /** Holds the generated object of [TextRef] names. Platform neutral - no Android types. */
    @get:OutputDirectory
    abstract val commonOutputDir: DirectoryProperty

    /** Holds the generated name to `R.string` id map. Android only. */
    @get:OutputDirectory
    abstract val androidOutputDir: DirectoryProperty

    /** Per locale completeness report. */
    @get:OutputFile
    abstract val reportFile: RegularFileProperty

    @TaskAction
    fun generate() {
        val res = resDir.get().asFile
        val baseDir = File(res, "values")
        if (!baseDir.isDirectory) throw GradleException("No values/ directory under $res")

        val names = readStringNames(baseDir)
        if (names.isEmpty()) throw GradleException("No <string> elements under $baseDir")

        val duplicates = names.groupBy { it }.filterValues { it.size > 1 }.keys
        if (duplicates.isNotEmpty()) {
            // AAPT would reject these too, so this cannot fire on a project that builds.
            throw GradleException("Duplicate string names under $baseDir: ${duplicates.sorted()}")
        }

        val unsafe = names.filterNot { it.isSafeKotlinIdentifier() }
        if (unsafe.isNotEmpty()) {
            // A name that is not a valid identifier cannot become a property on the generated
            // object. Renaming it in strings.xml is the fix, but that also renames it in Crowdin,
            // so it has to be a deliberate act rather than something the generator papers over.
            throw GradleException(
                "These string names cannot be generated as Kotlin properties: ${unsafe.sorted()}. " +
                    "Rename them under $baseDir."
            )
        }

        writeCommon(names.sorted())
        writeAndroid(names.sorted())
        writeReport(res, names.toSet())
    }

    private fun writeCommon(names: List<String>) {
        val dir = commonOutputDir.get().asFile
        dir.deleteRecursively()
        val pkg = packageName.get()
        val obj = objectName.get()
        val file = File(dir, pkg.replace('.', '/') + "/$obj.kt")
        file.parentFile.mkdirs()
        file.writeText(
            buildString {
                append(GENERATED_HEADER)
                append("package $pkg\n\n")
                append("import app.aaps.core.keys.interfaces.TextRef\n\n")
                append("/**\n")
                append(" * Every string this module owns, as a platform neutral [TextRef].\n")
                append(" *\n")
                append(" * Generated from `res/values/strings.xml`. Use these instead of `R.string.*` so the\n")
                append(" * declaring code stays free of Android resource ids.\n")
                append(" */\n")
                append("object $obj {\n\n")
                val ownerName = owner.get()
                names.forEach { append("    val $it: TextRef = TextRef.Named(\"$ownerName\", \"$it\")\n") }
                append("}\n")
            }
        )
    }

    private fun writeAndroid(names: List<String>) {
        val dir = androidOutputDir.get().asFile
        dir.deleteRecursively()
        val pkg = packageName.get()
        val obj = idsObjectName.get()
        val named = objectName.get()
        val file = File(dir, pkg.replace('.', '/') + "/$obj.kt")
        file.parentFile.mkdirs()
        file.writeText(
            buildString {
                append(GENERATED_HEADER)
                append("package $pkg\n\n")
                append("/**\n")
                append(" * Maps the names in [$named] back to this module's AAPT resource ids.\n")
                append(" *\n")
                append(" * This is what keeps Android on its own resource system: a `TextRef.Named` is resolved to\n")
                append(" * an `R.string` id here and then read through `Resources`, so locale matching, the\n")
                append(" * always English lookup and `MissingTranslation` lint all behave exactly as they did when\n")
                append(" * the keys carried ids directly.\n")
                append(" */\n")
                append("object $obj {\n\n")
                append("    private val ids: Map<String, Int> = mapOf(\n")
                names.forEach { append("        \"$it\" to R.string.$it,\n") }
                append("    )\n\n")
                append("    /**\n")
                append("     * The resource id for [name], or null when this module does not own that name.\n")
                append("     *\n")
                append("     * Null means someone built a `TextRef.Named` by hand instead of taking it from\n")
                append("     * [$named]. Callers show the raw name rather than crashing, which makes the mistake\n")
                append("     * visible on screen without taking the app down.\n")
                append("     */\n")
                append("    fun idOf(name: String): Int? = ids[name]\n")
                append("}\n")
            }
        )
    }

    private fun writeReport(res: File, expected: Set<String>) {
        val report = StringBuilder()
        report.append("Translation completeness for ${res.parentFile.parentFile.parentFile.name}\n")
        report.append("Base locale has ${expected.size} strings.\n\n")

        val locales = res.listFiles()
            ?.filter { it.isDirectory && it.name.startsWith("values-") }
            ?.sortedBy { it.name }
            .orEmpty()

        var empty = 0
        var partial = 0
        locales.forEach { dir ->
            val present = readStringNames(dir).toSet()
            val missing = expected - present
            val extra = present - expected
            val state = when {
                present.isEmpty() -> { empty++; "EMPTY" }
                missing.isEmpty() && extra.isEmpty() -> "complete"
                else               -> { partial++; "PARTIAL" }
            }
            report.append("%-18s %4d/%d  %s\n".format(dir.name, present.size, expected.size, state))
            if (extra.isNotEmpty()) report.append("    not in base locale: ${extra.sorted()}\n")
        }

        report.append("\n${locales.size} locales: ${locales.size - empty - partial} complete, $partial partial, $empty empty.\n")

        val out = reportFile.get().asFile
        out.parentFile.mkdirs()
        out.writeText(report.toString())

        // A warning, not a failure. 19 of these locales are empty today for reasons that predate
        // this task, so failing here would make the build red on the first run. The point is that
        // the number is now visible and can be tightened later.
        if (empty > 0 || partial > 0) {
            logger.warn("$name: $partial partial and $empty empty locales - see ${out.absolutePath}")
        }
    }

    /**
     * Every `<string>` in EVERY xml file of a `values*` directory, because that is what AAPT does.
     *
     * Reading only `strings.xml` is the obvious shortcut and it is wrong: `:core:ui` keeps 1059
     * strings in `strings.xml`, 32 in `protection.xml` and 60 in `strings_scene_wizard.xml`, and a
     * generator that missed the other two would leave 92 names unresolvable while looking correct.
     * Files with no `<string>` at all - colors, styles, layout aliases - simply contribute nothing.
     */
    private fun readStringNames(dir: File): List<String> {
        val files = dir.listFiles { f: File -> f.isFile && f.extension.equals("xml", ignoreCase = true) }
            ?.sortedBy { it.name }
            .orEmpty()
        val builder = DocumentBuilderFactory.newInstance()
            .apply { isNamespaceAware = false }
            .newDocumentBuilder()
        return files.flatMap { file ->
            val nodes = builder.parse(file).getElementsByTagName("string")
            (0 until nodes.length).mapNotNull { i ->
                nodes.item(i).attributes?.getNamedItem("name")?.nodeValue
            }
        }
    }

    private fun String.isSafeKotlinIdentifier(): Boolean =
        matches(Regex("[A-Za-z_][A-Za-z0-9_]*")) && this !in KOTLIN_KEYWORDS

    private companion object {

        const val GENERATED_HEADER =
            "// Generated by GenerateKeyStringsTask from res/values/strings.xml. Do not edit.\n\n"

        val KOTLIN_KEYWORDS = setOf(
            "as", "break", "class", "continue", "do", "else", "false", "for", "fun", "if", "in",
            "interface", "is", "null", "object", "package", "return", "super", "this", "throw",
            "true", "try", "typealias", "typeof", "val", "var", "when", "while"
        )
    }
}
