package app.aaps.implementation.maintenance.cloud

/**
 * Where a sign in page is shown.
 *
 * A separate thing from the app's ordinary "open this link" because it has a requirement an ordinary
 * link does not: **the app has to stay in front while the page is open**.
 *
 * The sign in ends with a redirect to a port this app is listening on. Handing the URL to the system
 * browser switches away from AAPS, and a backgrounded app is suspended within seconds - taking the
 * listener with it, so the redirect arrives at a closed port and the sign in simply never completes.
 * An implementation therefore presents the page *over* the app: `SFSafariViewController` or
 * `ASWebAuthenticationSession` on iOS, a Custom Tab on Android, an embedded window on the desktop.
 *
 * This is the one part of the flow where doing the obvious thing - reusing `UrlOpener` - is wrong.
 */
interface AuthBrowser {

    /** Shows [url] over the app. Returns false when no page could be shown. */
    fun show(url: String): Boolean

    /** Closes it, once the redirect has been caught or the wait has been given up. */
    fun dismiss()
}
