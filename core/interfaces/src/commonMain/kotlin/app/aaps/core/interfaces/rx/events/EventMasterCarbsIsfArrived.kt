package app.aaps.core.interfaces.rx.events

/**
 * AAPSClient only. A device status from the master has just brought the master's ISF for carbs after
 * there was none, normally the first device status after a start. The client's COB calculation reads
 * that value, so whatever it calculated before this used the profile ISF instead.
 */
class EventMasterCarbsIsfArrived : Event()
