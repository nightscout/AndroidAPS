package app.aaps.pump.carelevo.common

/**
 * Puts a Carelevo alarm in the phone's notification list, for a user who is not looking at AAPS.
 * [CarelevoAlarmNotifier] decides when and builds the text; this only shows it. A tap on it opens AAPS.
 *
 * Each platform that registers the Carelevo plugin implements it (Android: `AndroidCarelevoSystemNotifier`).
 */
interface CarelevoSystemNotifier {

    /** Shows [title] and [text] for the alarm [alarmId]. A second call for the same alarm replaces the first. */
    fun show(alarmId: String, title: String, text: String)
}
