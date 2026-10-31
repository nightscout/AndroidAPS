package app.aaps.plugins.sync.smsCommunicator

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.smsCommunicator.Sms
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins SMS as switched off on iOS rather than merely unimplemented.
 *
 * The distinction is the whole point. An app on iOS cannot send an SMS without a person tapping
 * send, and cannot read an incoming one at all, so a remote command sent to this phone would simply
 * never arrive. A user relying on that would get no warning - which is why [isEnabled] has to say no
 * rather than the send methods quietly failing one at a time.
 *
 * The plugin itself is always disabled on iOS, so in practice nothing reaches this at all - the
 * binding exists only because other code injects [app.aaps.core.interfaces.smsCommunicator.SmsCommunicator]
 * regardless of whether the plugin is on. These tests pin the answers it gives if something does
 * ask, so the class is not later "finished" into something that looks like it works.
 */
class IosSmsCommunicatorTest {

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

    private val communicator = IosSmsCommunicator(SilentLogger)

    /**
     * The one that matters: callers check this before offering the feature at all, so saying no
     * here is what makes SMS visibly absent instead of present and broken.
     */
    @Test
    fun `it reports itself as not enabled`() {
        assertFalse(communicator.isEnabled())
    }

    @Test
    fun `sending a message fails rather than pretending`() {
        assertFalse(communicator.sendSMS(Sms("+420123456789", "test")))
    }

    @Test
    fun `notifying every number fails rather than pretending`() {
        assertFalse(communicator.sendNotificationToAllNumbers("test"))
    }

    /** Nothing can arrive on iOS, so the log of received messages stays empty. */
    @Test
    fun `no messages are ever recorded`() {
        communicator.sendSMS(Sms("+420123456789", "test"))
        communicator.sendNotificationToAllNumbers("test")

        assertTrue(communicator.messages.isEmpty())
    }

    /** A caller that ignores isEnabled gets a false back, not an exception. */
    @Test
    fun `repeated sends stay harmless`() {
        repeat(5) { communicator.sendSMS(Sms("+420123456789", "test $it")) }

        assertTrue(communicator.messages.isEmpty())
        assertFalse(communicator.isEnabled())
    }
}
