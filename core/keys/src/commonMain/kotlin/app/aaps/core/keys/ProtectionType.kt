package app.aaps.core.keys

/**
 * Protection types for settings/application/bolus protection.
 * Must be kept in sync with app.aaps.core.interfaces.protection.ProtectionCheck.ProtectionType
 */
enum class ProtectionType {

    NONE,
    BIOMETRIC,
    MASTER_PASSWORD,
    CUSTOM_PASSWORD,
    CUSTOM_PIN
}
