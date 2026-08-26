package app.aaps.ios.shell

/**
 * A marker the iOS side can call to prove the framework loaded and runs.
 *
 * The link itself is what this module is for, so this stays deliberately small. It only gives an
 * Xcode project something to call, so that "the framework builds" and "the app can reach it" can be
 * checked apart from each other.
 */
object ShellInfo {

    /** Name of the framework, so a caller can print something it did not hard code itself. */
    const val NAME: String = "AapsShared"
}
