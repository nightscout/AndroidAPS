package app.aaps.implementation.resources

/**
 * Never consulted on Android.
 *
 * `ResourceHelperImpl` is the bound [app.aaps.core.interfaces.resources.TextResolver] here and
 * answers from the `isTablet` resource, which needs a `Context` a top level function cannot reach.
 * [GeneratedTextResolver] is built for this target only because it lives in `commonMain`; nothing
 * binds it. An actual is required all the same, so this is the value that would be least wrong.
 */
actual fun isCompactScreen(): Boolean = false
