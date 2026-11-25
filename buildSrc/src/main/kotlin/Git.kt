import org.gradle.api.provider.ProviderFactory

/**
 * The git facts that the app shells put in their builds, read in the way Gradle allows while a build
 * is being set up.
 *
 * Starting a process directly at that time is deprecated and will be an error in Gradle 11.
 * `ProviderFactory.exec` runs the same command, and the configuration cache keeps its output as an
 * input, so a new commit still gives a new build stamp.
 *
 * `:app`, `:wear`, `:desktop:shell` and `:ios:shell` all use these, so the rules below are the same
 * on every platform.
 */

/** Written in place of a git fact when git could not answer. */
const val NO_GIT = "NoGitSystemAvailable"

/**
 * Runs git and returns its trimmed output, or null when git failed or is not installed.
 *
 * The exit code decides, not the text. git run outside a work tree exits non-zero with empty
 * output, while `git status -s` on a clean tree is empty with a zero exit and is a real answer.
 * Only the exit code can tell the two apart.
 */
fun ProviderFactory.git(vararg args: String): String? = try {
    val run = exec {
        commandLine(listOf("git") + args)
        isIgnoreExitValue = true
    }
    if (run.result.get().exitValue == 0) run.standardOutput.asText.get().trim() else null
} catch (_: Exception) {
    null
}

/**
 * The short commit, for example `3.3.2.1-dev-12-g1a2b3c4`.
 *
 * `--exclude=ios-testflight-*`, because such a tag names one past iOS submission rather than this
 * build. `git describe` takes the nearest annotated tag whatever the distance, so a single TestFlight
 * tag made every later build announce itself as "ios-testflight-20260906-085036-44-g45d9d88" - in the
 * About dialog and in every log line.
 */
fun ProviderFactory.gitDescribe(): String =
    git("describe", "--always", "--abbrev=7", "--exclude=ios-testflight-*")?.ifEmpty { null } ?: NO_GIT

/** Where this was cloned from, so a crash report can say whether it came from a fork. */
fun ProviderFactory.gitRemoteUrl(): String =
    git("remote", "get-url", "origin")?.ifEmpty { null } ?: NO_GIT

/** Whether git is installed and can be started. */
fun ProviderFactory.gitAvailable(): Boolean = !git("--version").isNullOrEmpty()

/**
 * Whether the working tree has no changes to tracked files.
 *
 * Untracked files do not count - a clone usually has some, and treating them as changes would mark
 * every build dirty and make the flag say nothing. Changes under `.idea/codeStyles` do not count
 * either. Returns false when git cannot answer, so an unknown answer never reads as "clean".
 */
fun ProviderFactory.gitAllCommitted(): Boolean {
    val status = git("status", "-s") ?: return false
    return status
        .replace(Regex("""(?m)^\s*(M|A|D|\?\?)\s*.*?\.idea/codeStyles/.*?\s*$"""), "")
        .replace(Regex("""(?m)^\s*(\?\?)\s*.*?\s*$"""), "")
        .trim()
        .isEmpty()
}
