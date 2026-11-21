package app.aaps.implementations

import app.aaps.ComposeMainActivity
import app.aaps.core.interfaces.ui.AndroidActivities
import app.aaps.ui.activities.ErrorActivity
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlin.reflect.KClass

/**
 * The two activities other modules start, named in the one module that can see both.
 *
 * This is all that is left of `UiInteractionImpl` here. The rest of it - 150 lines of activity starts, notification
 * channels, an in-process re-ring timer and the watch events - is `app.aaps.implementation.ui.UiInteractionImpl` in
 * shared code now, over `Alerts`, so the same alarms work on iOS. See `_docs/PUMP_DESIGN.md`, section 8.
 */
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Inject
class AndroidActivitiesImpl : AndroidActivities {

    override val main: KClass<*> = ComposeMainActivity::class
    override val alarm: KClass<*> = ErrorActivity::class
}
