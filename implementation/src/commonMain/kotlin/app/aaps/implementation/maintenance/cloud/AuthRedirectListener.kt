package app.aaps.implementation.maintenance.cloud

/**
 * The part of a sign in that has to be written per platform: listening for the browser to come back.
 *
 * Everything else about Google sign in is shared - the PKCE, the URL, the token swap, the Drive
 * calls - because it is arithmetic and HTTP. This is not: it needs a socket, and each platform has
 * its own way to open one. iOS uses POSIX sockets, Android and the desktop a `ServerSocket`.
 *
 * ## Why it is a socket at all
 *
 * The redirect goes to `http://localhost:<port>/oauth/callback`, which is the flow AAPS already
 * registered with Google. Keeping it means one client id and one redirect for every platform. The
 * alternative - a custom URL scheme per app - would need its own registration and would leave two
 * sign in paths to keep in step.
 *
 * ## What an implementation must do
 *
 * Bind **loopback only**. Listening on every interface asks a phone for permission to find devices
 * on the local network, which is a strange thing to ask for a sign in that never leaves the device.
 * And close the listener when the wait ends, however it ends: a well known port left open for the
 * life of the app keeps accepting anything that arrives with a `code` on it.
 */
interface AuthRedirectListener {

    /** Starts listening, and says whether it managed to. A port already in use is a false, not a throw. */
    fun start(port: Int): Boolean

    /**
     * Waits for the browser, and hands back only a callback that proves it is ours.
     *
     * @param expectedState the `state` that was sent. Compared here, because a loopback listener will
     *   accept a connection from anything else on the device, and the code alone is not evidence that
     *   this app asked for it.
     * @return the result, or null when nothing arrived in time.
     */
    suspend fun awaitCallback(expectedState: String, timeoutMs: Long): OAuthCallback.Result?

    /** Stops listening and frees the port. Safe to call when it was never started. */
    fun stop()
}
