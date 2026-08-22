package app.aaps.pump.virtual

/**
 * The serial number the virtual pump reports.
 *
 * There is no hardware to ask, so this is a stable per-install identifier instead. It has to stay
 * stable, because `pumpSync` keys its records on the serial and a changed value looks like a
 * different pump.
 *
 * Platform specific only because the identifier itself is: Android has used the Firebase
 * installation id since long before this was multiplatform, and that value must not change.
 */
internal expect fun virtualPumpSerialNumber(): String
