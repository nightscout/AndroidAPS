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
     *
     * There is no third meaning, and this is the trap. A platform that has no paired-device list at
     * all must return an **empty list**, not `null` - because `null` makes the trigger editor ask the
     * user for a Bluetooth permission, and on such a platform there is no permission to grant. The
     * desktop implementation read `null` as "cannot tell" and produced exactly that: a red error on
     * every visit to the editor, pointing at nothing. Only answer `null` if granting something would
     * change the answer.
     */
    fun names(): List<String>?
}
