package app.aaps.plugins.automation

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Pins the deliberate emptiness of the paired device list on iOS.
 *
 * It looks like a stub waiting to be finished, and that is the danger: someone reading it later
 * could "fix" it into something that appears to work. It is empty because iOS genuinely cannot
 * answer - an app may not read the phone's paired devices.
 *
 * The other half of the Bluetooth story needs no class here. `BtConnectionSource` is implemented by
 * `AutomationRuntime` in commonMain, and it fills its buffer from `EventBTChange` on the bus. Nothing
 * posts that event on iOS - on Android a broadcast receiver does - so the list is empty there for the
 * same reason, without a second implementation to keep in step.
 *
 * The consequence, decided deliberately and recorded in `_docs/ios_blockers.md`: a Bluetooth
 * automation trigger can be configured on iOS and will never fire. This test exists so that stays a
 * decision rather than becoming a bug report.
 */
class IosBluetoothAbsenceTest {

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

    /**
     * Empty, not null.
     *
     * The interface reads null as "not allowed, the user can fix it". On iOS no permission would
     * fix it, so null would send someone hunting for a setting that does not exist.
     */
    @Test
    fun `paired devices are empty rather than unknown`() {
        val names = IosPairedBtDevices(SilentLogger).names()

        assertTrue(names != null, "null would claim a permission exists that could fix this")
        assertTrue(names.isEmpty())
    }

    /** Asking twice must not start accumulating anything. */
    @Test
    fun `repeated calls stay empty`() {
        val devices = IosPairedBtDevices(SilentLogger)
        repeat(3) { devices.names() }

        assertTrue(devices.names()?.isEmpty() == true)
    }
}
