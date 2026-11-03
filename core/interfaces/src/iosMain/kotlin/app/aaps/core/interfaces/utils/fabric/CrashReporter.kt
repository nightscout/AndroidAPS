package app.aaps.core.interfaces.utils.fabric

/**
 * The crash reporter of the iOS app, implemented in Swift.
 *
 * Kotlin cannot reach Firebase on Apple by itself. Rather than cinterop, this follows the bridge the
 * app already uses for the Nightscout socket: Kotlin declares the shape, `ios/shell` exports it into
 * the `AapsShared` framework header as an Obj-C protocol, a Swift class conforms to it, and the
 * instance is handed in through the one exported entry point. So no Kotlin module gains a Firebase
 * dependency, and `ios-ci.yml`, which never runs `xcodebuild`, keeps working unchanged.
 *
 * It lives in `iosMain` on purpose. Android has its own path straight to the Firebase SDK and desktop
 * has no reporter at all, so a shared declaration would be a type two platforms never use.
 *
 * [recordException] takes the exception in pieces rather than as a `Throwable`, because a Kotlin
 * `Throwable` is not a Swift `Error` and only Kotlin can format its own frames. The caller does the
 * formatting; Swift only forwards it.
 */
interface CrashReporter {

    /**
     * Whether reports are really going anywhere.
     *
     * False when the app has no usable Firebase configuration, which is the normal state of a build
     * nobody has set up. Answering truthfully matters: a reporter that silently drops everything
     * while saying it works is worse than one that is plainly absent.
     */
    fun isAvailable(): Boolean

    /**
     * Turns the whole reporter on or off, including the part Kotlin cannot gate.
     *
     * Crashlytics catches a fatal crash with its own signal handler, so a crash is sent whatever
     * Kotlin does or does not call. Checking the opt-out at each call site would therefore cover the
     * non-fatals and quietly miss the crashes - which is the bug this app already has on Android,
     * where turning the switch off keeps uploading until the next restart.
     *
     * So the stored answer is pushed down to the SDK at startup, and again whenever it changes.
     */
    fun setCollectionEnabled(enabled: Boolean)

    /** A breadcrumb kept with the next crash report, not a message of its own. */
    fun log(message: String)

    /** A name and value attached to every later report, for example the build or the active pump. */
    fun setCustomKey(key: String, value: String)

    /** Records a caught problem as a non-fatal report. [stackTrace] is one frame per entry. */
    fun recordException(type: String, message: String, stackTrace: List<String>)
}
