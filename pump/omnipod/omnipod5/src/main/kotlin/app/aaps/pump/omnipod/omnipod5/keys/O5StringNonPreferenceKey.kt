package app.aaps.pump.omnipod.omnipod5.keys

import app.aaps.core.keys.interfaces.StringNonPreferenceKey

/**
 * Preference key(s) for persisting Omnipod 5 pod state, mirroring [DashStringNonPreferenceKey].
 */
enum class O5StringNonPreferenceKey(
    override val key: String,
    override val defaultValue: String,
    override val exportable: Boolean = true
) : StringNonPreferenceKey {

    PodState("AAPS.Omnipod5.pod_state", ""),

    /** Holds Keystore-encrypted O5 registration (credential) data - never exported, since
     *  unlike pod connection state, this contains actual key material. */
    RegistrationData("AAPS.Omnipod5.registration_data_encrypted", "", exportable = false),
}
