package info.nightscout.sdk.exceptions

/**
 * Will be thrown if the server responds with 401 UNAUTHORIZED due to the Date Header being off
 * more than one hour.
 * In practice this will happen if the server time and the phone time are off.
 *
 */
class DateHeaderOutOfToleranceException : NightscoutException()
