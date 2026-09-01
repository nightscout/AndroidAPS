package app.aaps.pump.virtual.keys

import app.aaps.core.keys.interfaces.StringNonPreferenceKey

/** Values the virtual pump keeps for itself, outside the preference screen. */
enum class VirtualStringNonPreferenceKey(
    override val key: String,
    override val defaultValue: String,
    override val exportable: Boolean = true
) : StringNonPreferenceKey {

    /**
     * The serial the virtual pump reports, generated once on first use.
     *
     * Exported with the rest of the preferences on purpose: restoring a backup should bring the same
     * serial back, so the restored install keeps reporting the pump the history was recorded against.
     */
    SerialNumber("virtual_pump_serial_number", ""),
}
