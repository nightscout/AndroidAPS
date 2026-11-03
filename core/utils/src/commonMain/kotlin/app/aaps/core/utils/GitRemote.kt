package app.aaps.core.utils

/**
 * The git remote a build came from, in the short form a crash report carries: `github:owner/repo`.
 *
 * A crash report has to say which repository the build came from, because a report from a fork is
 * read differently from one built from upstream. The full clone URL is long for a custom key, and
 * the scheme and the `.git` suffix say nothing, so both are dropped.
 *
 * Shared rather than written once per platform. Android and iOS both send this key, and two copies
 * would let the same repository be written two ways in the same console.
 *
 * Lowercased without a locale on purpose. The Android copy this replaces used the default locale,
 * which in Turkish maps `I` to `ı` - so a phone set to Turkish reported a remote that matched no
 * other phone's.
 */
fun gitRemoteShort(remote: String): String =
    remote.lowercase()
        .replace("https://", "")
        .replace("http://", "")
        .replace(".git", "")
        .replace(".com/", ":")
        .replace(".org/", ":")
        .replace(".net/", ":")
