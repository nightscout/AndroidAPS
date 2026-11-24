package app.aaps.core.interfaces.alerts

/**
 * What an event of a device does to dosing. Every driver sorts its events by this, and it decides the severity of the
 * alert and whether its kind is protected (decided 2026-11-24, `_docs/ALARMS.md`, section 6). Eversense was the first
 * driver to use it; the other drivers follow when they move to alerts.
 *
 * Dosing is affected when a CGM gives no reading or one that cannot be trusted, or when a pump gives no insulin or not
 * what AAPS thinks it gives. A kind with an event that affects dosing is protected: it always rings.
 */
enum class DosingEffect(
    /** The severity of its alert, or null when the driver raises none. */
    val severity: AlertSeverity?,
    val affectsDosing: Boolean
) {

    /** Dosing is affected now. Rings until the user answers. */
    NOW(AlertSeverity.CRITICAL, affectsDosing = true),

    /** Dosing will be affected soon unless the user acts: a battery nearly empty, a calibration due. One chime. */
    SOON(AlertSeverity.WARNING, affectsDosing = true),

    /** Dosing is not affected, but the user has to do something: a device that ends in some days. One chime. */
    ACTION(AlertSeverity.WARNING, affectsDosing = false),

    /** For information only. A card, no sound. */
    INFO(AlertSeverity.INFO, affectsDosing = false),

    /** A glucose alarm of a CGM. The driver raises none: AAPS's own BG alarms raise it from the readings. */
    GLUCOSE(null, affectsDosing = false)
}
