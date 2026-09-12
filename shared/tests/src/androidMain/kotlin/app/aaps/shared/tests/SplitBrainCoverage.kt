package app.aaps.shared.tests

import java.io.File
import java.util.jar.JarFile

/**
 * Reflection helpers for checking who owns a singleton.
 * ## What used to be here, and why it is gone
 * What survives is the one piece that is about Metro alone: telling "nobody owns this" from "a
 * container owns it".
 */

/**
 * Types a Metro binding container provides with a scope - i.e. Metro owns exactly one of them.
 * A class can be owned by Metro without carrying `@SingleIn` itself: a `@Provides` in a
 * `@BindingContainer` constructs it, and a scope on that provider means one instance. That is how the
 * classes Metro cannot generate a factory for are owned - the Java eros managers, and the Omnipod
 * common BLE classes whose module does not apply the Metro plugin.
 * Exposed so a guard can tell "nobody owns this" from "a container owns it". Missing this distinction
 * once reported `DataInbox` as a split when it is not.
 */
fun metroScopedProviderTypes(
    anchors: List<Class<*>>,
    metroAnnotations: List<String> = DEFAULT_METRO_ANNOTATIONS
): Set<Class<*>> {
    val classes = anchors.flatMap { classesIn(it) }.distinct()
    // Guard the guard: an empty scan reports perfect coverage of nothing.
    check(classes.isNotEmpty()) { "Found no classes to scan - the class walk broke" }

    @Suppress("UNCHECKED_CAST")
    val singleIn = metroAnnotations.mapNotNull { runCatching { Class.forName(it) as Class<out Annotation> }.getOrNull() }
        .firstOrNull { it.name.endsWith(".SingleIn") }
    check(singleIn != null) { "Metro's @SingleIn is not on the test classpath" }

    return scopedContainerProviders(classes, singleIn)
}

/** Return types of `@SingleIn`-annotated provider functions on Metro binding containers. */
private fun scopedContainerProviders(classes: List<Class<*>>, singleIn: Class<out Annotation>): Set<Class<*>> {
    val container = runCatching {
        @Suppress("UNCHECKED_CAST")
        Class.forName("dev.zacsweers.metro.BindingContainer") as Class<out Annotation>
    }.getOrNull() ?: return emptySet()

    return classes
        .filter { it.isAnnotationPresent(container) }
        .flatMap { it.declaredMethods.filter { method -> method.isAnnotationPresent(singleIn) } }
        .map { it.returnType }
        .toSet()
}

private val DEFAULT_METRO_ANNOTATIONS = listOf(
    "dev.zacsweers.metro.SingleIn",
    "dev.zacsweers.metro.ContributesBinding",
    "dev.zacsweers.metro.ContributesIntoMap",
    "dev.zacsweers.metro.ContributesIntoSet"
)

/** Every AAPS class in the same compiled output as [anchor]. */
private fun classesIn(anchor: Class<*>): List<Class<*>> {
    val root = File(anchor.protectionDomain.codeSource.location.toURI())
    // The Android plugin hands unit tests a jar of a library's classes and a directory for others.
    val names = if (root.isDirectory) classNamesInDirectory(root) else classNamesInJar(root)

    return names
        .filter { it.startsWith("app.aaps.") || it.startsWith("info.nightscout.") }
        .filterNot { it.contains('$') } // synthetic, anonymous and Kotlin lambda classes
        // initialize = false: loading must not run static initialisers, several of which touch Android.
        .mapNotNull { name -> runCatching { Class.forName(name, false, anchor.classLoader) }.getOrNull() }
}

private fun classNamesInDirectory(root: File): List<String> =
    root.walkTopDown()
        .filter { it.isFile && it.extension == "class" }
        .map { it.relativeTo(root).path.removeSuffix(".class").replace(File.separatorChar, '.') }
        .toList()

private fun classNamesInJar(jar: File): List<String> =
    JarFile(jar).use { open ->
        open.entries().asSequence()
            .filter { !it.isDirectory && it.name.endsWith(".class") }
            .map { it.name.removeSuffix(".class").replace('/', '.') }
            .toList()
    }
