package app.aaps.shared.tests

import java.io.File
import java.lang.reflect.ParameterizedType
import java.util.jar.JarFile

/**
 * Finds classes that need a Metro member injector entry but do not have one.
 *
 * Pump packets cannot take their dependencies in a constructor, so their base class calls
 * `injector.injectMembers(this)` and throws when the class is missing from the map. The map is written by
 * hand, one entry per packet, which is exactly the kind of list that drifts as packets are added.
 *
 * Nothing else catches that drift. Unit tests pass a fake injector that returns `true` for anything, so the
 * check inside the packet never fires there, and the real map is only assembled in the app graph - which
 * means a missing entry first appears as a crashed pump service on a device.
 *
 * That gap was real twice over: three DanaR Korean packets whose names end in `_k` were missed while the
 * ones ending in `K` were added, and it only showed up as a crashed process in the DanaR emulator test.
 *
 * This reads the compiled classes rather than a written list, so a new packet is covered as soon as it is
 * added. Finding fewer classes than the map holds is fine and expected - a packet built directly by service
 * code is correct to register even though it appears in no other list.
 *
 * @param container the `@BindingContainer` object holding the `@ClassKey` entries
 * @param base the packet base class whose subclasses all need an entry
 * @return the names of subclasses with no entry, sorted, empty when the map is complete
 */
fun missingMemberInjectorEntries(container: Class<*>, base: Class<*>): List<String> {
    val registered = registeredClasses(container)
    // Guard the guard, both halves. An empty container means the reflection below stopped matching; an
    // empty scan means the class walk broke. Either one silently reports full coverage of nothing, so both
    // fail loudly instead.
    check(registered.isNotEmpty()) { "Found no member injector entries in ${container.name}" }
    val subclasses = subclassesOf(base)
    check(subclasses.isNotEmpty()) { "Found no subclasses of ${base.name} to check" }

    return subclasses.filterNot { it in registered }.map { it.name }.sorted()
}

/** The classes named by `MembersInjector<X>` parameters of the container's provider functions. */
private fun registeredClasses(container: Class<*>): Set<Class<*>> =
    container.declaredMethods
        .mapNotNull { method ->
            val parameter = method.genericParameterTypes.firstOrNull() as? ParameterizedType
            parameter?.actualTypeArguments?.firstOrNull() as? Class<*>
        }
        .toSet()

/** Every compiled subclass of [base] in the same output as [base] itself. */
private fun subclassesOf(base: Class<*>): List<Class<*>> {
    val root = File(base.protectionDomain.codeSource.location.toURI())
    // The Android plugin hands unit tests a jar of the library classes, but a plain directory when the same
    // code is compiled for the JVM. Both shapes have to work.
    val classNames = if (root.isDirectory) classNamesInDirectory(root) else classNamesInJar(root)

    return classNames
        .filterNot { it.contains('$') } // skip synthetic and anonymous classes
        .mapNotNull { name -> runCatching { Class.forName(name, false, base.classLoader) }.getOrNull() }
        .filter { base.isAssignableFrom(it) && it != base }
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
