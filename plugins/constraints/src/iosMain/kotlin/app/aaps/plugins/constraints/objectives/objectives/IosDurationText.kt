package app.aaps.plugins.constraints.objectives.objectives

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/**
 * Durations without plural forms on iOS. See [PlainDurationText] for why this is not translated.
 *
 * A thin binding rather than a copy: the formatting lives in the shared class, and this only says
 * that iOS uses it. Android does not - it has real plural resources.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class IosDurationText @Inject constructor() : DurationText by PlainDurationText()
