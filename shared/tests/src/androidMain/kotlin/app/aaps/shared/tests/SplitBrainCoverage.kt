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

/**
 * The mirror of [unbridgedSingletons]: classes **Metro** owns that **Dagger** is still asked to build.
 *
 * [unbridgedSingletons] looks one way only - a javax `@Singleton` that Metro also builds. The opposite
 * is just as damaging and was invisible until it cost a real defect: when the pump drivers moved to
 * `@SingleIn`, `DanaModules.provideRfcommTransport` went on taking `DanaRPlugin`, `DanaRKoreanPlugin`
 * and `DanaRv2Plugin` as parameters. Dagger does not understand `@SingleIn`; it saw an `@Inject`
 * constructor and built its own copies. That provider calls `setPluginEnabled` on them, so with a Dana
 * emulator option on, the auto-switch enabled objects that were not the ones in the plugin list.
 * Nothing failed to compile, and no test noticed.
 *
 * The rule this enforces: **a Dagger `@Provides` or `@Binds` may not take a parameter whose type Metro
 * owns.** To read a Metro owned object from the Dagger side, take the graph (`MetroGraphs`) and ask it
 * for the instance - that is what the `CoreObjectsModule` delegates do.
 *
 * `Provider<X>` and `Lazy<X>` parameters are unwrapped, because deferring the lookup does not change
 * who builds the object.
 *
 * @param anchors one class per compiled output to scan, as for [unbridgedSingletons]
 * @param daggerAnnotations fully qualified names of `dagger.Module`, `dagger.Provides`, `dagger.Binds`
 * @param graphTypes types that are legitimate parameters because they *are* the bridge (MetroGraphs)
 */
fun metroOwnedRebuiltByDagger(
    anchors: List<Class<*>>,
    daggerAnnotations: List<String> = listOf("dagger.Module", "dagger.Provides", "dagger.Binds"),
    graphTypes: Set<Class<*>> = emptySet(),
    metroAnnotations: List<String> = DEFAULT_METRO_ANNOTATIONS
): List<String> {
    val classes = anchors.flatMap { classesIn(it) }.distinct()
    check(classes.isNotEmpty()) { "Found no classes to scan - the class walk broke" }

    @Suppress("UNCHECKED_CAST")
    val dagger = daggerAnnotations.map { Class.forName(it) as Class<out Annotation> }
    check(dagger.size == 3) { "Expected Module, Provides and Binds - got $daggerAnnotations" }
    val (moduleAnn, providesAnn, bindsAnn) = dagger

    @Suppress("UNCHECKED_CAST")
    val singleIn = metroAnnotations.mapNotNull { runCatching { Class.forName(it) as Class<out Annotation> }.getOrNull() }
        .firstOrNull { it.name.endsWith(".SingleIn") }
    check(singleIn != null) { "Metro's @SingleIn is not on the test classpath" }

    val modules = classes.filter { it.isAnnotationPresent(moduleAnn) }
    check(modules.isNotEmpty()) { "Found no Dagger modules to scan - this guard has stopped guarding" }

    return modules.flatMap { module ->
        module.declaredMethods
            .filter { it.isAnnotationPresent(providesAnn) || it.isAnnotationPresent(bindsAnn) }
            .flatMap { method ->
                method.genericParameterTypes.map { unwrapDeferred(it) }
                    .filterNotNull()
                    .filter { it !in graphTypes && it.isAnnotationPresent(singleIn) }
                    .map { "${it.name} is Metro owned (@SingleIn) but ${module.simpleName}.${method.name} makes Dagger build it" }
            }
    }.distinct().sorted()
}

/**
 * `Provider<X>` / `Lazy<X>` to `X`; anything else to itself. Deferring does not change who builds it.
 *
 * **Wildcards have to be resolved, not cast away.** Kotlin emits `Provider<? extends X>` for a
 * parameter of this shape, so the type argument is a `WildcardType` rather than a `Class`, and an
 * `as? Class<*>` on it quietly yields null. That is exactly how the first version of this guard
 * managed to detect nothing at all while looking perfectly correct - caught only by mutating a real
 * class and watching the test still pass.
 */
private fun unwrapDeferred(type: java.lang.reflect.Type): Class<*>? = when (type) {
    is Class<*>                            -> type
    is java.lang.reflect.WildcardType      -> type.upperBounds.firstOrNull()?.let(::unwrapDeferred)
    is java.lang.reflect.ParameterizedType -> {
        val raw = type.rawType as? Class<*>
        if (raw?.name == "javax.inject.Provider" || raw?.name == "dagger.Lazy")
            type.actualTypeArguments.firstOrNull()?.let(::unwrapDeferred)
        else raw
    }

    else                                   -> null
}

/**
 * Types a Metro binding container provides with a scope - i.e. Metro owns exactly one of them.
 *
 * A class can be owned by Metro without carrying `@SingleIn` itself: a `@Provides` in a
 * `@BindingContainer` constructs it, and a scope on that provider means one instance. That is how the
 * Java classes are owned, since Metro cannot read an `@Inject` constructor it has no Kotlin IR for.
 *
 * Exposed so a guard can tell "nobody owns this" from "a container owns it".
 */
fun metroScopedProviderTypes(
    anchors: List<Class<*>>,
    metroAnnotations: List<String> = DEFAULT_METRO_ANNOTATIONS
): Set<Class<*>> {
    val classes = anchors.flatMap { classesIn(it) }.distinct()
    check(classes.isNotEmpty()) { "Found no classes to scan - the class walk broke" }

    @Suppress("UNCHECKED_CAST")
    val singleIn = metroAnnotations.mapNotNull { runCatching { Class.forName(it) as Class<out Annotation> }.getOrNull() }
        .firstOrNull { it.name.endsWith(".SingleIn") }
    check(singleIn != null) { "Metro's @SingleIn is not on the test classpath" }

    return scopedContainerProviders(classes, singleIn)
}
