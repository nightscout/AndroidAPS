package app.aaps.core.interfaces.ui

/**
 * Opens a web address in whatever the platform uses for browsing.
 *
 * Exists so a screen can offer a link without naming an `Intent` or a `UIApplication`, which is what
 * kept the about dialog on Android.
 */
interface UrlOpener {

    /**
     * Opens [url], or does nothing if the platform has nothing that can handle it.
     *
     * Deliberately fire and forget: no caller here needs to know whether a browser appeared, and a
     * device with no browser at all is not an error worth interrupting the user for.
     */
    fun open(url: String)
}
