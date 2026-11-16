package app.aaps.receivers

/**
 * Forward received SMS intents. This is a separate class, because unlike local broadcasts handled by DataReceiver,
 * receiving SMS requires a special permission in the manifest, which necessitates a separate receiver.
 */
class SmsReceiver : DataReceiver()