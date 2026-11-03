package app.aaps.core.utils

/**
 * The git remote a build came from, in the short form a crash report carries: `github:owner/repo`.
 *
 * A crash report has to say which repository the build came from, because a report from a fork is
 * read differently from one built from upstream. The clone URL itself is long for a custom key, and
 * the scheme, the port and the `.git` suffix say nothing about which repository it is.
 *
 * Shared rather than written once per platform. Android and iOS both send this key, and two copies
 * would let the same repository be written two ways in the same console. For the same reason an ssh
 * remote is folded onto the https form: `git@github.com:owner/repo` and
 * `https://github.com/owner/repo` are one repository, and how somebody chose to clone it is not a
 * difference a crash report should show.
 *
 * ## Why this matches a shape instead of trimming pieces off
 *
 * The part before `@` in a clone URL is a user name, or an access token -
 * `https://ghp_xxx@github.com/owner/repo` is what `git remote get-url` returns after cloning with
 * one. On iOS this string is compiled into the app and uploaded with every crash report, so a token
 * left in it would travel to the crash console.
 *
 * Trimming is the wrong tool for that, because it fails open: an address in a shape the trimming
 * did not expect keeps whatever was not trimmed. So an address has to match a known shape to be
 * reported at all, and anything else becomes [UNKNOWN]. A remote that cannot be read is a small
 * loss; a token in a crash report is not.
 *
 * A local path is reported as [LOCAL] for the same reason - it carries the account name of whoever
 * built it, and names no repository anybody else could open.
 *
 * Lowercased without a locale on purpose. The Android copy this replaces used the default locale,
 * which in Turkish maps `I` to `ı`, so a phone set to Turkish reported a remote that matched no
 * other phone's.
 */
fun gitRemoteShort(remote: String): String {
    val text = remote.trim().lowercase()
    if (text.isEmpty()) return ""

    // A bare word carries no address and so cannot hide a credential - "nogitsystemavailable" is
    // the one that matters, and it has to survive to say why there is no remote.
    if (text.none { it == '@' || it == '/' || it == ':' || it == '\\' }) return text

    if (isLocalPath(text)) return LOCAL

    val match = REMOTE.matchEntire(text) ?: return UNKNOWN
    val (host, path) = match.destructured.let { (h, p) -> h to p }
    // Anchored, so a repository called "foo.github" keeps its name. The old code replaced ".git"
    // anywhere in the string.
    return "${shortHost(host)}:${path.removeSuffix(".git")}"
}

/** A path on the machine that built it, in either platform's spelling. */
private fun isLocalPath(text: String): Boolean =
    text.startsWith("/") ||
        text.startsWith("file://") ||
        text.contains('\\') ||
        Regex("""^[a-z]:[/\\]""").containsMatchIn(text)

/**
 * `github.com` becomes `github`. Only the suffixes that were always folded, so a host the rule does
 * not know keeps its full name rather than being cut in a way that loses which host it was.
 */
private fun shortHost(host: String): String {
    for (suffix in listOf(".com", ".org", ".net")) if (host.endsWith(suffix)) return host.dropLast(suffix.length)
    return host
}

/** Reported when the address is in no shape this knows, so that nothing unread is passed on. */
private const val UNKNOWN = "unknown"

/** Reported for a clone from a path on the build machine. */
private const val LOCAL = "local"

/**
 * scheme, user name, host, port, separator, path - of which only the host and the path are kept.
 *
 * Neither the user name nor the path may hold an `@`. That costs the rare repository with one in
 * its name, which becomes [UNKNOWN], and it is what closes the hole: a credential containing an
 * unencoded `/` would otherwise match as if it were part of the path and be passed on in full.
 * Letting an address go unread is the cheaper mistake.
 */
private val REMOTE = Regex(
    """^(?:[a-z][a-z0-9+.\-]*://)?(?:[^/@]*@)?([a-z0-9][a-z0-9.\-]*)(?::\d+)?[:/]([a-z0-9._\-]+(?:/[a-z0-9._\-]+)*)/?$"""
)
