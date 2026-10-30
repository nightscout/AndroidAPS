import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.register
import java.io.Serializable

/**
 * One module that owns strings.
 *
 * Only the owner and the package are written down. The object names follow from the owner - `sync`
 * gives `SyncStrings` and `SyncStringIds` - so they cannot be spelled two different ways in two places.
 */
data class StringOwnerModule(
    val owner: String,
    val packageName: String
) : Serializable {

    /** The generated object of `TextRef` names, for example `SyncStrings`. */
    val objectName: String get() = owner.replaceFirstChar { it.uppercase() } + "Strings"

    /** The generated `name -> R.string id` map, for example `SyncStringIds`. Android only. */
    val idsObjectName: String get() = owner.replaceFirstChar { it.uppercase() } + "StringIds"

    /** The generated `name -> English text` map. Lives in commonMain, so every platform has it. */
    val valuesObjectName: String get() = objectName + "Values"

    companion object {

        private const val serialVersionUID: Long = 2L
    }
}

/**
 * Every module that owns strings, in one place.
 *
 * ## Why this list exists
 *
 * Each platform has to tell the resolver which module owns which names, and that used to be a hand
 * written list per platform - `MainApp.registerStringOwners()`, its copy in `BaseTestApp`,
 * `IosStringOwners` and `DesktopStringOwners`. Four lists of the same thing, and nothing checked
 * them against each other or against the build.
 *
 * They drifted exactly as you would expect. The desktop one had five of sixteen modules, so the
 * plugin list rendered `objectives_shortname` instead of plugin names - visible only by running it.
 * The Android pair were split across two files for historical reasons, which is its own trap: adding
 * a module meant knowing which of the two to edit.
 *
 * So the list lives here, and each shell gets a registry generated from it rather than a hand written
 * one. A module generates its strings with [registerKeyStrings], which reads its entry from this list,
 * so the two cannot disagree in either direction:
 * - a module that generates strings but is missing here fails the build in [registerKeyStrings];
 * - a module listed here that does not generate strings fails the build in the shells, because the
 *   generated registry imports an object that does not exist.
 *
 * Adding a module with strings means one line here and one [registerKeyStrings] call in its build file.
 */
object StringOwnerModules {

    val ALL: List<StringOwnerModule> = listOf(
        StringOwnerModule("interfaces", "app.aaps.core.interfaces"),
        StringOwnerModule("keys", "app.aaps.core.keys"),
        StringOwnerModule("coreUi", "app.aaps.core.ui"),
        StringOwnerModule("implementation", "app.aaps.implementation"),
        StringOwnerModule("ui", "app.aaps.ui"),
        StringOwnerModule("aps", "app.aaps.plugins.aps"),
        StringOwnerModule("automation", "app.aaps.plugins.automation"),
        StringOwnerModule("calibration", "app.aaps.plugins.calibration"),
        StringOwnerModule("configuration", "app.aaps.plugins.configuration"),
        StringOwnerModule("constraints", "app.aaps.plugins.constraints"),
        StringOwnerModule("main", "app.aaps.plugins.main"),
        StringOwnerModule("sensitivity", "app.aaps.plugins.sensitivity"),
        StringOwnerModule("smoothing", "app.aaps.plugins.smoothing"),
        StringOwnerModule("source", "app.aaps.plugins.source"),
        StringOwnerModule("sync", "app.aaps.plugins.sync"),
        StringOwnerModule("virtual", "app.aaps.pump.virtual")
    )
}

/**
 * Registers [GenerateKeyStringsTask] for this module, with every value taken from the entry for
 * [stringOwner] in [StringOwnerModules.ALL].
 *
 * The strings stay in `src/androidMain/res`. The caller still adds the two output directories to its
 * `commonMain` and `androidMain` source sets.
 */
fun Project.registerKeyStrings(stringOwner: String): TaskProvider<GenerateKeyStringsTask> {
    val module = StringOwnerModules.ALL.singleOrNull { it.owner == stringOwner }
        ?: throw GradleException(
            "String owner '$stringOwner' of $path is not in StringOwnerModules.ALL (buildSrc). Add it there, " +
                "or no platform shell registers it and its screens show string names instead of text."
        )
    val dirName = module.objectName.replaceFirstChar { it.lowercase() }
    return tasks.register<GenerateKeyStringsTask>("generate${module.objectName}") {
        resDir.set(layout.projectDirectory.dir("src/androidMain/res"))
        packageName.set(module.packageName)
        owner.set(module.owner)
        objectName.set(module.objectName)
        idsObjectName.set(module.idsObjectName)
        reportFile.set(layout.buildDirectory.file("reports/$dirName/translations.txt"))
        // Two directories, not one: each half deletes its own directory before it writes, so a shared
        // one would lose the file written first.
        commonOutputDir.set(layout.buildDirectory.dir("generated/$dirName/common"))
        androidOutputDir.set(layout.buildDirectory.dir("generated/$dirName/android"))
    }
}
