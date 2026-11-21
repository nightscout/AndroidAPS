package app.aaps.implementation.alerts

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.alerts.Alert
import app.aaps.core.interfaces.alerts.AlertKind
import app.aaps.core.interfaces.alerts.AlertOrigin
import app.aaps.core.interfaces.alerts.AlertSeverity
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.keys.interfaces.TextRef
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.StandardTestDispatcher
import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Which alert the alarm screen should be showing, which is the whole of what this class decides by itself.
 *
 * Everything else hands work to `UNUserNotificationCenter`, which a test binary has no app bundle for, so it cannot
 * be checked here - that it posts at all is what the simulator run shows. The screen request can be checked, and it
 * matters: iOS cannot open a window from a background class, so this flow is the only thing that brings the alarm
 * screen up. Get it wrong and a CRITICAL alert rings with nothing on screen to silence or act on it.
 */
class IosAlertPlatformTest {

    private object SilentLogger : AAPSLogger {

        override fun debug(message: String) {}
        override fun debug(enable: Boolean, tag: LTag, message: String) {}
        override fun debug(tag: LTag, message: String) {}
        override fun debug(tag: LTag, accessor: () -> String) {}
        override fun debug(tag: LTag, format: String, vararg arguments: Any?) {}
        override fun warn(tag: LTag, message: String) {}
        override fun warn(tag: LTag, format: String, vararg arguments: Any?) {}
        override fun info(tag: LTag, message: String) {}
        override fun info(tag: LTag, format: String, vararg arguments: Any?) {}
        override fun error(tag: LTag, message: String) {}
        override fun error(tag: LTag, message: String, throwable: Throwable) {}
        override fun error(tag: LTag, format: String, vararg arguments: Any?) {}
        override fun error(message: String) {}
        override fun error(message: String, throwable: Throwable) {}
        override fun error(format: String, vararg arguments: Any?) {}
        override fun debug(className: String, methodName: String, lineNumber: Int, tag: LTag, message: String) {}
        override fun info(className: String, methodName: String, lineNumber: Int, tag: LTag, message: String) {}
        override fun warn(className: String, methodName: String, lineNumber: Int, tag: LTag, message: String) {}
        override fun error(className: String, methodName: String, lineNumber: Int, tag: LTag, message: String) {}
    }

    /** Nothing here resolves a string; the notification content is the part that cannot be checked anyway. */
    private object LiteralResolver : TextResolver {

        override fun gs(ref: TextRef): String = (ref as? TextRef.Literal)?.text ?: ref.toString()
        override fun gs(ref: TextRef, vararg args: Any?): String = gs(ref)
        override fun gsNotLocalised(ref: TextRef): String = gs(ref)
    }

    /**
     * The provider throws: building a real `AlertsImpl` needs ten collaborators, none of which would be read
     * here, and Kotlin/Native has no Mockito. It is only reached from the foreground observer, which does not run in
     * a test binary - the UIKit read behind it needs a main queue nobody drains - so this records a call that must
     * not happen rather than a value.
     */
    private class AlertsAsked : Exception("the platform asked for AlertsImpl")

    private val scope = CoroutineScope(StandardTestDispatcher() + SupervisorJob())
    private val platform = IosAlertPlatform(SilentLogger, LiteralResolver, { throw AlertsAsked() }, scope)

    /** A pump's kind of alert, under [source]. */
    private class PumpKind(override val source: String) : AlertKind {

        override val id: String = "$source.occlusion"
        override val origin: AlertOrigin = AlertOrigin.PUMP
        override val title: TextRef = TextRef.Literal("Occlusion")
        override val isProtected: Boolean = false
    }

    private fun alert(source: String = "carelevo", key: String = "occlusion") = Alert(
        kind = PumpKind(source),
        key = key,
        severity = AlertSeverity.CRITICAL,
        title = TextRef.Literal("Occlusion"),
        text = TextRef.Literal("Replace the patch."),
        raisedAt = 1_700_000_000_000L
    )

    @Test
    fun `nothing asks for the alarm screen to start with`() {
        assertNull(platform.alarmScreen.value)
    }

    @Test
    fun `a critical alert asks for the alarm screen`() {
        platform.showAlarmScreen(alert())

        assertEquals(AlertId("carelevo", "occlusion"), platform.alarmScreen.value)
    }

    /**
     * Silence-and-close: the screen goes, the alert stays on the pump and in the notification list.
     *
     * This is also how the alert *ending* drops the screen: `withdrawn` calls this first and then clears what the
     * notification centre holds. `withdrawn` itself cannot be called here - the centre raises an Objective-C
     * exception without an app bundle, which ends the process rather than failing a test - so the order inside it is
     * what makes this test cover both.
     */
    @Test
    fun `closing the screen drops the request`() {
        platform.showAlarmScreen(alert())
        platform.alarmScreenClosed("carelevo", "occlusion")

        assertNull(platform.alarmScreen.value)
    }

    /**
     * The guard that matters: a second alert's screen must not be closed by the first one's close.
     *
     * Only one screen is up at a time, so without the identity check a close arriving late - from a screen the user
     * had already left - would take down the alarm that replaced it, and that one would be ringing with nothing to
     * silence it.
     */
    @Test
    fun `a close for another alert leaves the request alone`() {
        platform.showAlarmScreen(alert(key = "occlusion"))
        platform.alarmScreenClosed("carelevo", "empty-reservoir")

        assertEquals(AlertId("carelevo", "occlusion"), platform.alarmScreen.value)
    }

    /** A later alert replaces the one before it, as there is only one screen. */
    @Test
    fun `a second alert takes the screen over`() {
        platform.showAlarmScreen(alert(key = "occlusion"))
        platform.showAlarmScreen(alert(key = "empty-reservoir"))

        assertEquals(AlertId("carelevo", "empty-reservoir"), platform.alarmScreen.value)
    }

    // ---------------------------------------------------------------------------------------------
    // When the re-ring is scheduled for
    // ---------------------------------------------------------------------------------------------

    /** A silence that is still running is scheduled for roughly when it ends. */
    @Test
    fun `a future silence end is a positive number of seconds away`() {
        val now = (NSDate().timeIntervalSince1970 * 1000).toLong()
        val seconds = platform.secondsFromNow(now + 30 * 60_000L)

        assertTrue(seconds > 29 * 60 && seconds < 31 * 60, "about half an hour, was $seconds")
    }

    /**
     * A silence that is already over gets no notification. iOS refuses a trigger of zero or less, and `checkSilences`
     * ends such a silence the ordinary way on the next wake, so there is nothing to schedule.
     */
    @Test
    fun `a silence end in the past is not in the future`() {
        assertTrue(platform.secondsFromNow(0L) < 1.0)
    }
}
