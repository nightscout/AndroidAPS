package app.aaps.di

import app.aaps.core.interfaces.configuration.ExternalOptions

/**
 * Extra [ExternalOptions] to report as enabled, on top of what the production file lookup finds.
 *
 * Production passes [NONE]. The instrumented tests pass a live one so they can drive the in-tree pump
 * emulators, which are selected purely on `config.isEnabled(EMULATE_*)`.
 *
 * ## Why this is a graph input rather than a replaced binding
 *
 * Hilt did this with `@TestInstallIn(replaces = [AppModule.AppBindings::class])`, swapping `Config` for
 * a decorator. Metro has `replaces` too, but it is **unusable from androidTest**: `AppRootGraph` is
 * compiled during `:app:compileFullDebugKotlin`, and androidTest is a later, separate compilation that
 * is not on its classpath, so nothing contributed there ever reaches the graph. Verified - a deliberate
 * duplicate binding contributed from androidTest compiled clean, with no `DuplicateBinding`.
 *
 * So the only channel into the one graph is its factory, which is what this travels through. It is the
 * same shape `DatabaseConfig.IN_MEMORY` already uses, and keeping **one** graph matters more than
 * keeping the seam out of production: a second graph declared in androidTest could drift from the real
 * one silently, which is the exact class of bug this migration has kept producing.
 *
 * A function, not a value: the tests set their options **per test**, long after the application has
 * built the graph, so this has to be read on each call rather than captured at construction.
 */
fun interface ExternalOptionsOverride {

    fun enabled(): Set<ExternalOptions>

    companion object {

        /** Production: the file lookup decides everything. */
        val NONE = ExternalOptionsOverride { emptySet() }
    }
}
