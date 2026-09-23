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
    // The whole test runtime classpath, not just the outputs the anchors happen to live in. A binding
    // container owns what it provides wherever it is declared, so a scan tied to one output reports
    // "nobody owns this" the moment a container moves to the module that owns the type - which is where
    // it belongs. That is not hypothetical: ten of them moved out of `:app` into their pump modules and
    // this guard failed for fifteen types that had lost nothing but proximity.
    //
    // [anchors] now only say which class loader to resolve through, and keep the call sites honest about
    // which graphs a caller means.
    val classes = classesOnClasspath(anchors)
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

/**
 * Every AAPS class on the test runtime classpath, loaded through the [anchors]' class loader.
 *
 * `java.class.path` is what the Gradle test worker was launched with, so it holds every module output
 * and dependency jar - the pump modules included, which is the point.
 *
 * Public so a guard can ask what is actually on this build's classpath rather than restating a list.
 * A test that names the modules it expects fails to compile the moment one is removed from
 * `settings.gradle`, which makes the list of modules a dependency of the app's own tests.
 */
fun aapsClassesOnClasspath(anchors: List<Class<*>>): List<Class<*>> = classesOnClasspath(anchors)

private fun classesOnClasspath(anchors: List<Class<*>>): List<Class<*>> {
    val loader = anchors.firstOrNull()?.classLoader ?: ClassLoader.getSystemClassLoader()
    val roots = System.getProperty("java.class.path").orEmpty()
        .split(File.pathSeparatorChar)
        .map(::File)
        .filter { it.exists() }
    // The anchors' own outputs too: a class loaded from somewhere off java.class.path would otherwise
    // be missed, and it costs nothing to add them.
    val anchorRoots = anchors.mapNotNull { anchor ->
        runCatching { File(anchor.protectionDomain.codeSource.location.toURI()) }.getOrNull()
    }

    return (roots + anchorRoots).distinct()
        // The Android plugin hands unit tests a jar of a library's classes and a directory for others.
        .flatMap { root -> runCatching { if (root.isDirectory) classNamesInDirectory(root) else classNamesInJar(root) }.getOrDefault(emptyList()) }
        .distinct()
        .filter { it.startsWith("app.aaps.") || it.startsWith("info.nightscout.") }
        .filterNot { it.contains('$') } // synthetic, anonymous and Kotlin lambda classes
        // initialize = false: loading must not run static initialisers, several of which touch Android.
        .mapNotNull { name -> runCatching { Class.forName(name, false, loader) }.getOrNull() }
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
