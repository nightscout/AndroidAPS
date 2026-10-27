package app.aaps.core.interfaces.rx.events

/**
 * AAPSCLIENT only. A device status from the master has brought the master's ISF for carbs for the first
 * time in this run. Sent once, because every later device status carries the value too.
 *
 * The COB calculation reads that ISF per bucket out of the results the client stores, so a client that
 * already had some stored is right from the start and does nothing here. Only one that had none - a new
 * install, or after a database reset - had to use the profile ISF, and starts over. See
 * [app.aaps.core.interfaces.iob.IobCobCalculator.usedProfileIsfForCarbs].
 */
class EventMasterCarbsIsfArrived : Event()
