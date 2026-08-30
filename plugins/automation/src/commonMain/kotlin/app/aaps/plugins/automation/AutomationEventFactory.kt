package app.aaps.plugins.automation

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.plugins.automation.actions.ActionFactory
import app.aaps.plugins.automation.triggers.TriggerDeps
import app.aaps.plugins.automation.triggers.TriggerFactory
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/**
 * Builds [AutomationEventObject]s and holds what they need.
 *
 * Events are stored as JSON, so Dagger cannot build them. They used to be handed a
 * `HasAndroidInjector` and inject themselves, which needed a generated members injector - Java, and
 * therefore impossible in a multiplatform module.
 *
 * This holds the dependencies instead and passes them in. The event reads them back through the
 * factory, so a new event can be created from an existing one without carrying a second bundle.
 */
@SingleIn(AppScope::class)
class AutomationEventFactory @Inject constructor(
    val aapsLogger: AAPSLogger,
    val dateUtil: DateUtil,
    val actionFactory: ActionFactory,
    val triggerFactory: TriggerFactory,
    val triggerDeps: TriggerDeps
) {

    /** A new, empty event. */
    fun newEvent(): AutomationEventObject = AutomationEventObject(this)

    /** An event rebuilt from its stored form. */
    fun fromJSON(data: String): AutomationEventObject = AutomationEventObject(this).fromJSON(data)
}
