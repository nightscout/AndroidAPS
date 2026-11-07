package app.aaps.core.keys.interfaces

/**
 * Which shell the code is running in.
 *
 * Lives in `:core:keys` because that is the lowest module - it has no project dependencies at all -
 * so both a preference key and `Config` can name it.
 *
 * ## What this is for
 *
 * Some things exist on one platform and not another: the "don't kill my app" link is Android's
 * problem, an Exit menu item only makes sense on a desktop, and no permission will ever let an iOS
 * app read the phone's paired Bluetooth devices. Before this, each of those was decided at its own
 * call site, or not decided at all - which is how the trigger editor came to ask a desktop user for
 * a Bluetooth permission that does not exist, and how a settings row that does nothing came to be
 * drawn on two platforms.
 *
 * The rule is that the **thing declares where it belongs**, once, in its own definition - see
 * [PreferenceKey.platforms]. There is no separate table mapping features to platforms to keep in
 * step with the code.
 *
 * ## Two things this is not
 *
 * **Not "where the setting takes effect".** A client shows settings that configure the *master*. A
 * `Bidirectional` synced key on an iPhone may be a working remote control for an Android master, so
 * restricting it by the platform doing the *displaying* would hide real function. Only restrict a
 * key that is not synced. `PreferencePlatformRulesTest` holds that line.
 *
 * **Not a device capability.** This says which shell is running, not what the hardware can do. A
 * phone-versus-tablet question is `isCompactScreen()`, and an "is this pump a patch pump" question
 * is `VisibilityContext`. Keep those separate - a platform is a fixed fact about the build, while
 * the others change under the app.
 */
enum class AppPlatform {

    Android,
    Ios,
    Desktop;

    companion object {

        /** Everywhere, which is what anything that does not say otherwise means. */
        val ALL: Set<AppPlatform> = entries.toSet()

        /** The common case: something the other two shells have no counterpart for. */
        val ANDROID_ONLY: Set<AppPlatform> = setOf(Android)
    }
}
