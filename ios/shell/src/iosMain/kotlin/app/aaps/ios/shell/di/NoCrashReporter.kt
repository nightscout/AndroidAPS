package app.aaps.ios.shell.di

import app.aaps.core.interfaces.utils.fabric.CrashReporter

/**
 * The crash reporter for graphs built without Swift: the startup probes and the iOS tests.
 *
 * Those call sites build a graph from Kotlin alone, so there is no Swift class to hand in. They are
 * not running a user's app and nothing they do should be reported anywhere.
 *
 * [isAvailable] answers false, which is the whole point. A stub that claimed to work would read as
 * a working reporter that happened to find nothing, and that is how a stub survives into a release.
 */
internal object NoCrashReporter : CrashReporter {

    override fun isAvailable(): Boolean = false
    override fun setCollectionEnabled(enabled: Boolean) {}
    override fun log(message: String) {}
    override fun setCustomKey(key: String, value: String) {}
    override fun recordException(type: String, message: String, stackTrace: List<String>) {}
}
