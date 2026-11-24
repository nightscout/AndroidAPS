package app.aaps.desktop.shell.platform

import app.aaps.core.interfaces.alerts.Alert
import app.aaps.core.interfaces.alerts.AlertPlatform
import app.aaps.core.interfaces.alerts.Alerts
import app.aaps.core.interfaces.alerts.BgAlarms
import app.aaps.core.interfaces.alerts.NightscoutAnnouncements
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.AlarmSound
import app.aaps.core.interfaces.notifications.AlarmSoundPlayer
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.implementation.alerts.AlertId
import app.aaps.implementation.alerts.AlertsImpl
import app.aaps.implementation.alerts.BgAlarmsImpl
import app.aaps.implementation.alerts.NightscoutAnnouncementsImpl
import app.aaps.implementation.ui.UiInteractionImpl
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The desktop binds the shared [AlertsImpl] (`_docs/ALARMS.md`, step 4), as Android (`AlertsAndroidBindings`) and iOS
 * (`AlertsIosBindings`) do: the same cards, kinds, choices and silences on every platform. What raises through the
 * alerts is here with them.
 */
@ContributesTo(AppScope::class)
@BindingContainer
object AlertsDesktopBindings {

    @Provides
    fun alerts(impl: AlertsImpl): Alerts = impl

    /** The app's own alarms are alerts too. */
    @Provides
    fun uiInteraction(impl: UiInteractionImpl): UiInteraction = impl

    /** The BG alarms raise through the alerts, so they are here where the alerts are. */
    @Provides
    fun bgAlarms(impl: BgAlarmsImpl): BgAlarms = impl

    /** A Nightscout announcement is an alert, so it is here where the alerts are. */
    @Provides
    fun nightscoutAnnouncements(impl: NightscoutAnnouncementsImpl): NightscoutAnnouncements = impl
}

/**
 * The desktop half of the alerts, beyond the card list, which already owns the card and its beep.
 *
 * - **The alarm screen** is the shared `AlertScreen`, drawn by the app's window in place of the app while
 *   [alarmScreen] is set, as on iOS. The window is not brought to the front: the card's beep and the tray are what
 *   reach a user who is in another window.
 * - **The one sound of a WARNING** is a message in the tray and a beep.
 * - **The end of a silence and a booked alert** need nothing here: the core's own timer rings them, and the desktop
 *   app runs as long as its window is open. Closed, it rings nothing, as every other timer of the desktop.
 */
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Inject
class DesktopAlertPlatform(
    private val aapsLogger: AAPSLogger,
    private val rh: TextResolver,
    private val tray: DesktopSystemNotificationPlatform
) : AlertPlatform {

    private val _alarmScreen = MutableStateFlow<AlertId?>(null)

    /** The alert whose alarm screen should be up, or null. The window shows it; nothing here draws anything. */
    val alarmScreen: StateFlow<AlertId?> = _alarmScreen.asStateFlow()

    override fun showAlarmScreen(alert: Alert) {
        aapsLogger.debug(LTag.NOTIFICATION, "Alarm screen asked for: ${alert.source}/${alert.key}")
        _alarmScreen.value = AlertId(alert.source, alert.key)
    }

    /** The alarm screen was closed by the user. The alert stays; only the request goes. */
    fun alarmScreenClosed(source: String, key: String) {
        if (_alarmScreen.value == AlertId(source, key)) _alarmScreen.value = null
    }

    // The desktop has one sound, so [sound] and [ringtoneUri] choose nothing here
    override fun chime(alert: Alert, sound: AlarmSound, ringtoneUri: String?) {
        tray.toast(rh.gs(alert.title), alert.text?.let { rh.gs(it) } ?: "")
        tray.beep()
    }

    override fun snoozed(alert: Alert, until: Long) = Unit

    override fun withdrawn(source: String, key: String) = alarmScreenClosed(source, key)

    override fun book(alert: Alert, at: Long, ring: Boolean): Boolean = true

    override fun unbook(source: String, key: String) = Unit
}

/** The Test button of an alert kind: the beep, which is the only sound a desktop has. A beep is over at once. */
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Inject
class DesktopAlarmSoundPlayer(private val tray: DesktopSystemNotificationPlatform) : AlarmSoundPlayer {

    override fun play(sound: AlarmSound, ownerTag: String, breakThroughDnd: Boolean, ringtoneUri: String?) = tray.beep()

    override fun stop(ownerTag: String) = Unit
}
