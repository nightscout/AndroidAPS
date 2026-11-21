package app.aaps.core.interfaces.ui

import kotlin.reflect.KClass

/**
 * The two activities that code in other modules has to be able to start.
 *
 * Android only, and that is the point of it being its own interface. These were members of [UiInteraction], which is
 * shared code, so every platform had to answer them: the desktop implementation pointed both at a class that is not
 * an activity at all, purely to compile. Every real reader is Android - `UiInteractionImpl`,
 * `AlarmNotificationManager`, `NotificationHolderImpl` and `AndroidPumpAlertPlatform`.
 *
 * Usage: `startActivity(Intent(context, androidActivities.main.java))`.
 */
interface AndroidActivities {

    /** The main activity of the application. */
    val main: KClass<*>

    /** The alarm screen, shown over whatever the user is looking at when an alarm rings. */
    val alarm: KClass<*>
}
