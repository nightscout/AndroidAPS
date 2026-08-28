package app.aaps.plugins.automation

/**
 * The Bluetooth devices this phone is paired with.
 *
 * Only the reading of the list is platform specific, so it sits behind this interface. The trigger
 * that compares against a device name ([app.aaps.plugins.automation.triggers.Trigger]) stays plain
 * Kotlin.
 */
interface PairedBtDevices {

    /**
     * Names of the paired devices, or `null` when the app is not allowed to read them.
     *
     * `null` and an empty list mean different things: `null` is a missing permission, which the user
     * can fix, while an empty list means the phone really has no paired device. The caller shows a
     * message only for the first case.
     */
    fun names(): List<String>?
}
