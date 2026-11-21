package app.aaps.implementation.pump.alerts

import app.aaps.core.data.model.TE
import app.aaps.core.interfaces.concurrent.AapsLock
import app.aaps.core.interfaces.concurrent.withLock
import app.aaps.core.interfaces.di.ApplicationScope
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.AlarmSound
import app.aaps.core.interfaces.notifications.NotificationAction
import app.aaps.core.interfaces.notifications.NotificationHandle
import app.aaps.core.interfaces.notifications.NotificationId
import app.aaps.core.interfaces.notifications.NotificationLevel
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.pump.alerts.PumpAlert
import app.aaps.core.interfaces.pump.alerts.PumpAlertLifetime
import app.aaps.core.interfaces.pump.alerts.PumpAlertPlatform
import app.aaps.core.interfaces.pump.alerts.PumpAlertSeverity
import app.aaps.core.interfaces.pump.alerts.PumpAlerts
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventShowSnackbar
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.StringNonKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.CoreUiStrings
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import kotlin.coroutines.cancellation.CancellationException

/**
 * The shared implementation of [PumpAlerts]. Each alert is one card in the notification list (`NotificationId.PUMP_ALERT`),
 * and that list is the only thing that plays alarm sound: a CRITICAL alert that rings is an URGENT card with a sound,
 * a silenced one is the same card without it. The alarm screen and the one-time sound of a WARNING come from
 * [PumpAlertPlatform]. See `_docs/PUMP_DESIGN.md`, section 2.
 *
 * Not contributed from shared code: each platform binds it once it has a [PumpAlertPlatform], so a platform without
 * one has no pump alerts rather than alerts that do nothing.
 */
@SingleIn(AppScope::class)
@Inject
class PumpAlertsImpl(
    private val aapsLogger: AAPSLogger,
    private val notificationManager: NotificationManager,
    private val platform: PumpAlertPlatform,
    private val preferences: Preferences,
    private val pumpSync: PumpSync,
    private val activePlugin: ActivePlugin,
    private val rh: TextResolver,
    private val rxBus: RxBus,
    private val dateUtil: DateUtil,
    @ApplicationScope private val appScope: CoroutineScope
) : PumpAlerts {

    private class Entry(
        var alert: PumpAlert,
        /** The card shown for it, or null while it has none. */
        var card: NotificationHandle? = null,
        /** Epoch ms until when it does not ring; 0 when not silenced. */
        var silencedUntil: Long = 0L,
        var silenceEnd: Job? = null
    )

    private data class Silence(val source: String, val key: String, val raisedAt: Long, val until: Long)

    /** Guards [entries], [silences] and every card post or dismiss for them. Not reentrant. */
    private val lock = AapsLock()
    private val entries = LinkedHashMap<Pair<String, String>, Entry>()
    private var silences: MutableList<Silence>? = null
    private var observing = false

    private val _active = MutableStateFlow<List<PumpAlert>>(emptyList())
    override val active: StateFlow<List<PumpAlert>> = _active.asStateFlow()

    override val sounding: PumpAlert?
        get() = lock.withLock {
            val now = dateUtil.now()
            entries.values
                .filter { it.card != null && it.silencedUntil <= now && it.alert.severity == PumpAlertSeverity.CRITICAL }
                .maxByOrNull { it.alert.raisedAt }
                ?.alert
        }

    override fun raise(alert: PumpAlert) {
        startObserving()
        lock.withLock {
            val id = alert.source to alert.key
            val existing = entries[id]
            // Published before the card, so the alarm screen the card may open finds the alert in [active].
            if (existing != null && existing.alert.raisedAt == alert.raisedAt) {
                // The same occurrence again: new text or action perhaps, but it does not ring again.
                existing.alert = alert
                publish()
                if (existing.card != null) showCard(existing, ring = false)
            } else {
                existing?.let { removeCard(it) }
                val entry = Entry(alert, silencedUntil = storedSilence(alert))
                entries[id] = entry
                publish()
                showCard(entry, ring = true)
                if (alert.severity != PumpAlertSeverity.INFO) announce(alert)
            }
        }
    }

    override fun resolve(source: String, key: String) = lock.withLock {
        entries.remove(source to key)?.let { end(it) }
        publish()
    }

    override fun resolveAll(source: String) = lock.withLock {
        entries.keys.filter { it.first == source }.forEach { id -> entries.remove(id)?.let { end(it) } }
        publish()
    }

    /**
     * What "the user dealt with the sound" means depends on who owns the alert.
     *
     * A [PumpAlertLifetime.CONDITION] is the pump's, so silence only quiets it and it comes back in
     * [PumpAlerts.SILENCE_MINUTES]. A [PumpAlertLifetime.EVENT] is the user's: answering it is the end of it, and a
     * bolus error that came back half an hour later would be telling them about something they have already read.
     */
    override fun silence(source: String, key: String) {
        val lifetime = lock.withLock { entries[source to key]?.alert?.lifetime } ?: return
        // Outside the lock: neither call may be made while holding it, it is not reentrant.
        if (lifetime == PumpAlertLifetime.EVENT) resolve(source, key)
        else snooze(source, key, PumpAlerts.SILENCE_MINUTES)
    }

    override fun snooze(source: String, key: String, minutes: Int) = lock.withLock {
        entries[source to key]?.let { silenceEntry(it, minutes) }
        Unit
    }

    /**
     * Runs the alert's own step.
     *
     * A [PumpAlertLifetime.CONDITION] is silenced first, because the user is dealing with it and the pump will take
     * its own time to confirm; the driver resolves it when that happens. A [PumpAlertLifetime.EVENT] is not silenced,
     * because its action *is* the answer: "+5 min" on the "time to eat" timer moves the timer and ends the alarm
     * itself, and when it fails the alarm has to go on ringing - the user still has to eat. So an event is resolved
     * when its action succeeds, and left exactly as it was when it does not.
     */
    override fun act(source: String, key: String) {
        val alert = lock.withLock {
            val entry = entries[source to key] ?: return
            if (entry.alert.lifetime == PumpAlertLifetime.CONDITION) silenceEntry(entry, PumpAlerts.SILENCE_MINUTES)
            entry.alert
        }
        val action = alert.action ?: return
        appScope.launch {
            val done = try {
                action.run()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                aapsLogger.error(LTag.PUMP, "Pump alert action failed: ${alert.source}/${alert.key}", e)
                false
            }
            aapsLogger.debug(LTag.PUMP, "Pump alert action ${alert.source}/${alert.key} done=$done")
            when {
                !done                                             ->
                    rxBus.send(EventShowSnackbar(rh.gs(CoreUiStrings.pump_alert_action_failed), EventShowSnackbar.Type.Error))

                alert.lifetime == PumpAlertLifetime.EVENT -> resolve(source, key)
            }
        }
    }

    /**
     * Ring again every alert whose silence has ended. Called by the timer of each silence, and by a platform that
     * wakes the app at that time (Android: an exact alarm), because a timer alone runs late while the phone sleeps.
     */
    fun checkSilences() = lock.withLock {
        val now = dateUtil.now()
        entries.values.filter { it.silencedUntil in 1..now }.forEach { endSilence(it) }
    }

    // ---- the cards ----------------------------------------------------------------------------

    /** Caller holds [lock]. Posts (or re-posts) the card of [entry]; [ring] = this is the moment it starts to ring. */
    private fun showCard(entry: Entry, ring: Boolean) {
        entry.card?.let { old ->
            entry.card = null
            notificationManager.dismiss(old)
        }
        val alert = entry.alert
        val silenced = entry.silencedUntil > dateUtil.now()
        val level = when (alert.severity) {
            PumpAlertSeverity.CRITICAL -> NotificationLevel.URGENT
            PumpAlertSeverity.WARNING  -> NotificationLevel.IMPORTANT
            PumpAlertSeverity.INFO     -> NotificationLevel.NORMAL
        }
        entry.card = notificationManager.post(
            id = NotificationId.PUMP_ALERT,
            text = cardText(alert),
            level = level,
            date = alert.raisedAt,
            validTo = 0L,
            sound = if (alert.severity == PumpAlertSeverity.CRITICAL && !silenced) alert.sound ?: AlarmSound.ALARM else null,
            actions = alert.action?.let { listOf(NotificationAction(it.label) { act(alert.source, alert.key) }) } ?: emptyList(),
            validityCheck = null,
            // What the system notification and the watch announce it as. A pump alert passes nothing and is named by
            // its level, as every card was before; the app's own alarms carry the title their caller gave.
            alarmTitle = alert.notificationTitle,
            canMute = alert.canMute,
            tellWatch = alert.tellWatch
        )
        when {
            silenced                                                -> scheduleSilenceEnd(entry)
            !ring                                                   -> Unit
            alert.severity == PumpAlertSeverity.CRITICAL -> platform.showAlarmScreen(alert)
            alert.severity == PumpAlertSeverity.WARNING  -> platform.chime(alert)
            else                                                    -> Unit
        }
    }

    /** Caller holds [lock]. */
    private fun removeCard(entry: Entry) {
        entry.silenceEnd?.cancel()
        entry.silenceEnd = null
        entry.card?.let { card ->
            entry.card = null
            notificationManager.dismiss(card)
        }
    }

    /** Caller holds [lock]. The alert is over: its card, its timer, what the platform holds and its stored silence go. */
    private fun end(entry: Entry) {
        removeCard(entry)
        platform.withdrawn(entry.alert.source, entry.alert.key)
        if (entry.silencedUntil != 0L) {
            entry.silencedUntil = 0L
            forgetSilence(entry.alert)
        }
    }

    /** Caller holds [lock]. */
    private fun silenceEntry(entry: Entry, minutes: Int) {
        val until = dateUtil.now() + minutes * 60_000L
        entry.silencedUntil = until
        rememberSilence(entry.alert, until)
        platform.snoozed(entry.alert, until)
        showCard(entry, ring = false)
    }

    /** Caller holds [lock]. */
    private fun endSilence(entry: Entry) {
        entry.silenceEnd?.cancel()
        entry.silenceEnd = null
        entry.silencedUntil = 0L
        forgetSilence(entry.alert)
        platform.withdrawn(entry.alert.source, entry.alert.key)
        aapsLogger.debug(LTag.PUMP, "Pump alert silence ended, it rings again: ${entry.alert.source}/${entry.alert.key}")
        showCard(entry, ring = true)
    }

    /** Caller holds [lock]. */
    private fun scheduleSilenceEnd(entry: Entry) {
        entry.silenceEnd?.cancel()
        val wait = entry.silencedUntil - dateUtil.now()
        entry.silenceEnd = appScope.launch {
            delay(wait)
            checkSilences()
        }
    }

    /**
     * What the card says.
     *
     * Title and text together, because a pump alert's card is titled by its level ("Urgent alarm") and would
     * otherwise lose which alert it is. An alarm that brought its own title does not need that: the title is already
     * on the card and on the watch, so repeating it in the body would say the same thing twice.
     */
    private fun cardText(alert: PumpAlert): String {
        val text = alert.text ?: return rh.gs(alert.title)
        return if (alert.notificationTitle != null) rh.gs(text)
        else rh.gs(CoreUiStrings.pump_alert_text, rh.gs(alert.title), rh.gs(text))
    }

    /** Caller holds [lock]. */
    private fun publish() {
        _active.value = entries.values.map { it.alert }
    }

    /**
     * A card of a pump alert can go away without [resolve]: Dismiss on a card with no action, OK on the alarm screen
     * of another alarm (it clears every ringing card), a swipe. The alert is still there, so it counts as a silence
     * and the card comes back without sound. Only an INFO alert with no action is gone for good: the user has read it.
     */
    private fun startObserving() {
        val start = lock.withLock { if (observing) false else true.also { observing = true } }
        if (!start) return
        appScope.launch {
            notificationManager.notifications.collect { lock.withLock { restoreRemovedCards() } }
        }
    }

    /** Caller holds [lock]. Reads the list as it is now, not as the flow sent it, so a card posted meanwhile counts. */
    private fun restoreRemovedCards() {
        val shown = notificationManager.notifications.value.map { it.instanceKey }.toSet()
        // A copy, because an event's card going away removes it from `entries` inside the loop.
        val removed = entries.values.filter { entry -> entry.card?.let { it.instanceKey !in shown } == true }
        var ended = false
        removed.forEach { entry ->
            entry.card = null
            val alert = entry.alert
            when {
                // The user answered it, however they did it, and an event is over once it is answered.
                alert.lifetime == PumpAlertLifetime.EVENT                      -> {
                    aapsLogger.debug(LTag.PUMP, "Alert card removed, and it was the answer: ${alert.source}/${alert.key}")
                    entries.remove(alert.source to alert.key)?.let { end(it) }
                    ended = true
                }
                // Read, and nothing to do about it.
                alert.severity == PumpAlertSeverity.INFO && alert.action == null -> Unit

                else                                                             -> {
                    aapsLogger.debug(LTag.PUMP, "Pump alert card removed while active, shown again silenced: ${alert.source}/${alert.key}")
                    if (entry.silencedUntil > dateUtil.now()) showCard(entry, ring = false)
                    else silenceEntry(entry, PumpAlerts.SILENCE_MINUTES)
                }
            }
        }
        if (ended) publish()
    }

    private fun announce(alert: PumpAlert) {
        val note = cardText(alert)
        appScope.launch {
            try {
                val pump = activePlugin.activePump
                pumpSync.insertTherapyEventIfNewWithTimestamp(
                    timestamp = alert.raisedAt,
                    type = TE.Type.ANNOUNCEMENT,
                    note = note,
                    pumpType = pump.model(),
                    pumpSerial = pump.serialNumber()
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                aapsLogger.error(LTag.PUMP, "Pump alert announcement failed", e)
            }
        }
    }

    // ---- the stored silences --------------------------------------------------------------------

    /** Caller holds [lock]. */
    private fun storedSilences(): MutableList<Silence> = silences ?: loadSilences().also { silences = it }

    /** Caller holds [lock]. Until when this occurrence is silenced, or 0. */
    private fun storedSilence(alert: PumpAlert): Long {
        val now = dateUtil.now()
        return storedSilences().firstOrNull { it.source == alert.source && it.key == alert.key && it.raisedAt == alert.raisedAt && it.until > now }?.until ?: 0L
    }

    /** Caller holds [lock]. */
    private fun rememberSilence(alert: PumpAlert, until: Long) {
        val list = storedSilences()
        list.removeAll { it.source == alert.source && it.key == alert.key }
        list.add(Silence(alert.source, alert.key, alert.raisedAt, until))
        saveSilences(list)
    }

    /** Caller holds [lock]. */
    private fun forgetSilence(alert: PumpAlert) {
        val list = storedSilences()
        if (list.removeAll { it.source == alert.source && it.key == alert.key }) saveSilences(list)
    }

    private fun loadSilences(): MutableList<Silence> {
        val now = dateUtil.now()
        return try {
            Json.parseToJsonElement(preferences.get(StringNonKey.PumpAlertSilences)).jsonArray.map { element ->
                val o = element.jsonObject
                Silence(o.text("source"), o.text("key"), o.number("raisedAt"), o.number("until"))
            }.filter { it.until > now }.toMutableList()
        } catch (e: Exception) {
            aapsLogger.error(LTag.PUMP, "Stored pump alert silences unreadable, dropped", e)
            mutableListOf()
        }
    }

    private fun saveSilences(list: List<Silence>) {
        val now = dateUtil.now()
        val json = buildJsonArray {
            list.filter { it.until > now }.forEach { s ->
                add(buildJsonObject {
                    put("source", s.source)
                    put("key", s.key)
                    put("raisedAt", s.raisedAt)
                    put("until", s.until)
                })
            }
        }
        preferences.put(StringNonKey.PumpAlertSilences, json.toString())
    }

    private fun JsonObject.text(name: String): String = getValue(name).jsonPrimitive.content
    private fun JsonObject.number(name: String): Long = getValue(name).jsonPrimitive.long
}
