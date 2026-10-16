package app.aaps.core.interfaces.protection

/**
 * The platform side of remembering the export password.
 *
 * `ExportPasswordDataStore` decides everything that matters - how long a remembered password stays
 * valid, when it is about to expire, and when it has to be dropped because the master password
 * changed. None of that is platform specific, so it lives in shared code. This interface is the small
 * part that cannot: where the encrypted secret is kept.
 *
 * What is stored is the **encrypted** envelope produced by [SecureEncrypt], never a password in the
 * clear, so an implementation does not need secure storage of its own - it needs somewhere durable
 * that survives a restart and is private to this user.
 *
 * Android uses Jetpack DataStore, and the desktop JVM a file in the AAPS directory. Both keep the
 * value only on that machine, which is intended: a remembered password must not travel to another
 * device.
 */
interface ExportPasswordPlatform {

    /** The stored secret with the time it was written, or null when nothing is stored. */
    fun read(): Stored?

    /** Replaces the stored secret, which must already be encrypted, and its timestamp. */
    fun write(secret: String, timestamp: Long)

    /** Forgets the stored secret. */
    fun clear()

    /**
     * A shortened validity window for testing, or null for the normal one.
     *
     * Waiting five weeks to see a password expire is not a test anyone runs, so a developer build can
     * shorten the window to minutes. It sits here because how that is switched on is platform
     * specific - Android looks for a marker file in its export directory - and because it must be
     * impossible on a release build.
     *
     * This goes away with the debug mode it serves; it is not part of the feature.
     */
    fun shortenedValidity(): Validity?

    /** An encrypted secret as it was stored, with the time it was written. */
    data class Stored(val secret: String, val timestamp: Long)

    /** How long a remembered password lasts, and how long before that the user is warned. */
    data class Validity(val window: Long, val gracePeriod: Long)
}
