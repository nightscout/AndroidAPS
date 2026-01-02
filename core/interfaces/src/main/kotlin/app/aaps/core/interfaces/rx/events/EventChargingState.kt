package app.aaps.core.interfaces.rx.events

/**
 * Fired when the charging state or battery level of the device changes.
 *
 * @param isCharging True if the device is currently charging.
 * @param batteryLevel The current battery level as a percentage.
 */
class EventChargingState(val isCharging: Boolean, val batteryLevel: Int) : Event()