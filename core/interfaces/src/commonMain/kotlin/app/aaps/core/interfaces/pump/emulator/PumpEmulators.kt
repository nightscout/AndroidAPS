package app.aaps.core.interfaces.pump.emulator

import app.aaps.core.interfaces.concurrent.AapsLock
import app.aaps.core.interfaces.concurrent.withLock

/**
 * The emulated pumps of one app run, kept outside the app graph.
 *
 * A test that kills the app starts it again in a new process. A real pump runs on meanwhile, so that test puts the
 * saved emulator in here before the graph is built, with the bolus it is giving. Production makes a new holder for
 * its one graph, so each app start has new emulated pumps, as before.
 */
class PumpEmulators {

    /** One key for each kind of emulator. The type is what the binding stores under it. */
    class Key<T : Any>(val name: String)

    private val lock = AapsLock()
    private val emulators = HashMap<Key<*>, Any>()

    /** Only this puts a value in, always under a key of the same type, so the cast cannot fail. */
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> getOrCreate(key: Key<T>, create: () -> T): T = lock.withLock { emulators.getOrPut(key, create) as T }
}
