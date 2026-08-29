package app.aaps.shared.tests

import java.io.File
import java.util.jar.JarFile

/**
 * Finds classes that Dagger and Metro would each build a copy of.
 *
 * A class with a javax `@Singleton` and an `@Inject` constructor is buildable by both frameworks, and
 * **a javax scope does not cross them**: the graph in `:app` runs without Dagger interop, so it ignores
 * `@Singleton` and builds a fresh instance for every injection point. Neither half is wrong on its own
 * and nothing fails to compile - the damage is at runtime, when one half writes to an object the other
 * half never reads.
 *
 * That is not hypothetical. Three shipped this way in one day:
 *
 *  - `ProfileSwitchSilentGate` - `SceneExecutor` (Metro) marked the flag, `CommandQueueImplementation`
 *    (Dagger) consumed it, so a scene profile switch raised the notification the gate exists to hide.
 *  - `ReceiverDelegate` - `TidepoolPlugin` read an upload gate that nothing updated.
 *  - `RateLimit` - the same plugin held an empty map, so nothing was ever rate limited.
 *
 * The fix is always to name an owner: give the class Metro's `@SingleIn` and hand it to Dagger with a
 * `@Provides` delegate, or leave it Dagger's and hand it to Metro through a leaf. Either way there is
 * one object; this reports the classes where nobody has chosen yet.
 *
 * @param daggerOwned types a leaf hands from Dagger to Metro. **Only leaves count as safe.** A
 *   `CoreObjectsModule` delegate does not: it returns whatever Metro built at that moment, so if the
 *   Metro side is unscoped, Dagger caches one instance while every other Metro consumer gets its own -
 *   still split, just harder to see. A delegated class must carry `@SingleIn` as well, which is what
 *   this function checks for.
 * @param metroAnnotations the annotations that mark a class Metro builds itself
 */
fun unbridgedSingletons(
    anchors: List<Class<*>>,
    daggerOwned: Set<Class<*>>,
    metroAnnotations: List<String> = DEFAULT_METRO_ANNOTATIONS
): List<String> {
    val classes = anchors.flatMap { classesIn(it) }.distinct()
    // Guard the guard: an empty scan reports perfect coverage of nothing.
    check(classes.isNotEmpty()) { "Found no classes to scan - the class walk broke" }

    val metroMarkers = metroAnnotations.mapNotNull { name ->
        @Suppress("UNCHECKED_CAST")
        runCatching { Class.forName(name) as Class<out Annotation> }.getOrNull()
    }
    check(metroMarkers.isNotEmpty()) { "None of $metroAnnotations resolved - Metro annotations are not on the test classpath" }

    val singleIn = metroMarkers.first { it.name.endsWith(".SingleIn") }
    val javaxSingleton = Class.forName("javax.inject.Singleton") as Class<out Annotation>

    // A class can also be owned by a scoped `@Provides` in a `@BindingContainer` rather than by an
    // annotation on itself - that is how the Android-only types are built. Metro still holds exactly one,
    // so those are owned too, and missing this reported DataInbox as a split when it is not.
    val scopedByContainer = scopedContainerProviders(classes, singleIn)

    // What Metro constructs: anything it is told to contribute or scope, minus anything a leaf hands
    // over - a leaf means Dagger built it and Metro only borrows, so its dependencies are Dagger's too.
    val metroBuilt = classes.filter { candidate ->
        metroMarkers.any { candidate.isAnnotationPresent(it) } && candidate !in daggerOwned
    }
    check(metroBuilt.isNotEmpty()) { "Found no Metro-built classes - the annotation lookup broke" }

    return metroBuilt.flatMap { owner ->
        owner.declaredConstructors.flatMap { it.parameterTypes.toList() }
            .distinct()
            .filter { param ->
                param.isAnnotationPresent(javaxSingleton) &&
                    !param.isAnnotationPresent(singleIn) &&
                    param !in daggerOwned &&
                    param !in scopedByContainer
            }
            .map { "${it.name} is javax @Singleton but Metro builds it for ${owner.simpleName}" }
    }.distinct().sorted()
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
