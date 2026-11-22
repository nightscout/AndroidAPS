package app.aaps.core.interfaces.pump

/**
 * How long the command queue keeps the pump link open after its last command, when the driver does not
 * say otherwise ([Pump.waitForDisconnectionInSeconds]).
 *
 * The wait lets a command that comes a few seconds later use the same link. Android keeps it. iOS does
 * not: there a locked phone gives the app only about 30 seconds of background time per wake, and the wait
 * would spend part of it for nothing - the loop sends its temporary basal, its SMB and the status read in
 * one go, so on a real phone no command ever came during the wait.
 */
internal expect val defaultDisconnectWaitSeconds: Int
