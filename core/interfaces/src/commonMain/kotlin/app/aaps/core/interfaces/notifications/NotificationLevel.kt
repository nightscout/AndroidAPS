package app.aaps.core.interfaces.notifications

enum class NotificationLevel(val priority: Int) {
    /**
     * Alarm tier: plays sound with volume ramp and takes over the screen (full-screen alarm).
     * Reserved for acute, active insulin-delivery failures, critical BG conditions, and
     * user-configured alarms. This is the only level that makes noise.
     */
    URGENT(0),

    /** Important, but not an alarm: shown silently at the top of the in-app notification list. */
    IMPORTANT(1),
    NORMAL(2),
    LOW(3),
    INFO(4),
    ANNOUNCEMENT(5)
}
