package app.aaps.implementation.alerts

import app.aaps.core.data.model.TE
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.interfaces.alerts.Alert
import app.aaps.core.interfaces.alerts.AlertChoice
import app.aaps.core.interfaces.alerts.AlertKind
import app.aaps.core.interfaces.alerts.AlertKindCatalogue
import app.aaps.core.interfaces.alerts.AlertLifetime
import app.aaps.core.interfaces.alerts.AlertOrigin
import app.aaps.core.interfaces.alerts.AlertPlatform
import app.aaps.core.interfaces.alerts.AlertSeverity
import app.aaps.core.interfaces.alerts.AlertSound
import app.aaps.core.interfaces.alerts.BgAlert
import app.aaps.core.interfaces.alerts.SharedSilence
import app.aaps.core.interfaces.alerts.SharedSilenceLink
import app.aaps.core.interfaces.alerts.Alerts
import app.aaps.core.interfaces.concurrent.AapsLock
import app.aaps.core.interfaces.concurrent.withLock
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.configuration.awaitInitialized
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.di.ApplicationScope
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.AlarmSound
import app.aaps.core.interfaces.notifications.AlarmSoundPlayer
import app.aaps.core.interfaces.notifications.NotificationAction
import app.aaps.core.interfaces.notifications.NotificationHandle
import app.aaps.core.interfaces.notifications.NotificationId
import app.aaps.core.interfaces.notifications.NotificationLevel
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventShowSnackbar
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.StringNonKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.objects.extensions.asAnnouncement
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
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import kotlin.coroutines.cancellation.CancellationException

/**
 * The shared implementation of [Alerts]. Each alert is one card in the notification list (`NotificationId.ALERT`),
 * and that list is the only thing that plays alarm sound: a CRITICAL alert that rings is an URGENT card with a sound,
 * a silenced one is the same card without it. The alarm screen and the one-time sound of a WARNING come from
 * [AlertPlatform]. See `_docs/PUMP_DESIGN.md`, section 2.
 *
 * Not contributed from shared code: each platform binds it once it has a [AlertPlatform], so a platform without
 * one has no pump alerts rather than alerts that do nothing.
 */
@SingleIn(AppScope::class)
@Inject
class AlertsImpl(
    private val aapsLogger: AAPSLogger,
    private val notificationManager: NotificationManager,
    private val platform: AlertPlatform,
    private val preferences: Preferences,
    private val pumpSync: PumpSync,
    private val persistenceLayer: PersistenceLayer,
    private val config: Config,
    private val activePlugin: ActivePlugin,
    private val rh: TextResolver,
    private val rxBus: RxBus,
    private val dateUtil: DateUtil,
    private val soundPlayer: AlarmSoundPlayer,
    // A provider: the Nightscout client behind it reaches much of the app, and is needed only when a silence is shared
    private val sharedSilenceLink: () -> SharedSilenceLink,
    @ApplicationScope private val appScope: CoroutineScope,
    // A provider: a driver's catalogue reaches its plugin, which raises through here. Read only to restore a saved booking.
    private val catalogues: () -> Set<AlertKindCatalogue>
) : Alerts {

    private class Entry(
        var alert: Alert,
        /** The card shown for it, or null while it has none. */
        var card: NotificationHandle? = null,
        /** Epoch ms until when it does not ring; 0 when not silenced. */
        var silencedUntil: Long = 0L,
        var silenceEnd: Job? = null
    )

    private data class Silence(val source: String, val key: String, val raisedAt: Long, val until: Long)

    /** An alert booked to be raised at [at]. Its timer raises it while the process runs; the platform does otherwise. */
    private class Booking(val alert: Alert, val at: Long, var timer: Job? = null)

    /** Guards [entries], [silences] and every card post or dismiss for them. Not reentrant. */
    private val lock = AapsLock()
    private val entries = LinkedHashMap<Pair<String, String>, Entry>()
    private var silences: MutableList<Silence>? = null
    private var choiceMap: MutableMap<String, AlertChoice>? = null
    private var pauseMap: MutableMap<String, Long>? = null
    private var soundMap: MutableMap<String, AlertSound>? = null

    /** Silences given on another phone for an alert this phone has not raised yet; used by [raise]. Guarded by [lock]. */
    private val pendingShared = mutableListOf<SharedSilence>()

    /** A silence tapped on a notification for an alert not raised (again) yet. See [takePendingTap]. */
    private class PendingTap(val source: String, val key: String, val minutes: Int?, val at: Long)

    private val pendingTaps = mutableListOf<PendingTap>()
    private var pauseEnd: Job? = null
    private var bookings: MutableMap<Pair<String, String>, Booking>? = null
    private var observing = false

    private val _active = MutableStateFlow<List<Alert>>(emptyList())
    override val active: StateFlow<List<Alert>> = _active.asStateFlow()

    private val _choices = MutableStateFlow<Map<String, AlertChoice>>(emptyMap())
    private val choicesView = _choices.asStateFlow()
    override val choices: StateFlow<Map<String, AlertChoice>>
        get() {
            lock.withLock { storedChoices() }
            return choicesView
        }

    private val _pauses = MutableStateFlow<Map<String, Long>>(emptyMap())
    private val pausesView = _pauses.asStateFlow()
    override val pauses: StateFlow<Map<String, Long>>
        get() {
            lock.withLock { storedPauses() }
            return pausesView
        }

    override fun pause(kinds: Collection<AlertKind>, until: Long): Unit = lock.withLock {
        if (until <= dateUtil.now()) return@withLock
        val ids = kinds.filterNot { it.isProtected }.map { it.id }.toSet()
        if (ids.isEmpty()) return@withLock
        val map = storedPauses()
        ids.forEach { map[it] = until }
        savePauses(map)
        aapsLogger.info(LTag.NOTIFICATION, "Alert kinds $ids paused on this phone until ${dateUtil.dateAndTimeString(until)}")
        // Quiet at once; showCard books the pause end, so it rings then also when the app does not run
        entries.values.filter { it.alert.kind.id in ids }.forEach { showCard(it, ring = false) }
        schedulePauseEnd()
    }

    override fun resume(kinds: Collection<AlertKind>): Unit = lock.withLock {
        val map = storedPauses()
        val ids = kinds.map { it.id }.filter { map.remove(it) != null }.toSet()
        if (ids.isEmpty()) return@withLock
        savePauses(map)
        aapsLogger.info(LTag.NOTIFICATION, "Alert kinds $ids no longer paused on this phone")
        ringAfterPause(ids)
        schedulePauseEnd()
    }

    private val _sounds = MutableStateFlow<Map<String, AlertSound>>(emptyMap())
    private val soundsView = _sounds.asStateFlow()
    override val sounds: StateFlow<Map<String, AlertSound>>
        get() {
            lock.withLock { storedSounds() }
            return soundsView
        }

    override fun setSound(kind: AlertKind, sound: AlertSound?): Unit = lock.withLock {
        val map = storedSounds()
        if (map[kind.id] == sound) return@withLock
        if (sound == null) map.remove(kind.id) else map[kind.id] = sound
        preferences.put(StringNonKey.AlertSounds, buildJsonObject {
            map.forEach { (id, s) ->
                when (s) {
                    is AlertSound.Bundled  -> put(id, s.sound.name)
                    is AlertSound.Ringtone -> put(id, buildJsonObject {
                        put("uri", s.uri)
                        put("title", s.title)
                    })
                }
            }
        }.toString())
        _sounds.value = map.toMap()
        aapsLogger.info(LTag.NOTIFICATION, "Alert kind ${kind.id} sound on this phone: ${sound ?: "its own"}")
    }

    override fun test(kind: AlertKind, severity: AlertSeverity): Boolean {
        val testAlert = Alert(kind = kind, key = TEST_KEY, severity = severity, title = kind.title, raisedAt = dateUtil.now())
        // A test must never stop a real alarm: the player plays one sound at a time
        if (sounding != null) {
            aapsLogger.info(LTag.NOTIFICATION, "Alert test of ${kind.id} not played: an alarm is sounding")
            return false
        }
        val (sound, ringtone) = lock.withLock { soundFor(testAlert) to ringtoneFor(testAlert) }
        aapsLogger.info(LTag.NOTIFICATION, "Alert test of ${kind.id}: $severity, $sound${ringtone?.let { ", ringtone $it" } ?: ""}")
        if (severity == AlertSeverity.CRITICAL) {
            soundPlayer.play(sound, OWNER_TEST, breakThroughDnd = false, ringtoneUri = ringtone)
            // Owner-scoped: a real alarm that started meanwhile took the player over and is not stopped here
            appScope.launch {
                delay(Alerts.TEST_SECONDS * 1000L)
                soundPlayer.stop(OWNER_TEST)
            }
        } else {
            platform.chime(testAlert, sound, ringtone)
            appScope.launch {
                delay(Alerts.TEST_SECONDS * 1000L)
                platform.withdrawn(kind.source, TEST_KEY)
            }
        }
        return true
    }

    /**
     * Caller holds [lock]. The bundled sound an alert plays on this phone: the user's for its kind, else its own. With a
     * ringtone chosen, the alert's own: the fallback when the ringtone cannot be played.
     */
    private fun soundFor(alert: Alert): AlarmSound =
        (storedSounds()[alert.kind.id] as? AlertSound.Bundled)?.sound ?: alert.sound ?: AlarmSound.ALARM

    /** Caller holds [lock]. The phone ringtone the user chose for the alert's kind (Android), or null. */
    private fun ringtoneFor(alert: Alert): String? = (storedSounds()[alert.kind.id] as? AlertSound.Ringtone)?.uri

    /** Caller holds [lock]. The stored sound choices, read on first use. */
    private fun storedSounds(): MutableMap<String, AlertSound> =
        soundMap ?: loadSounds().also {
            soundMap = it
            _sounds.value = it.toMap()
        }

    private fun loadSounds(): MutableMap<String, AlertSound> = try {
        Json.parseToJsonElement(preferences.get(StringNonKey.AlertSounds)).jsonObject
            .mapNotNull { (id, value) ->
                // One entry that cannot be read is left out, not the whole map
                runCatching {
                    if (value is JsonObject) AlertSound.Ringtone(value.getValue("uri").jsonPrimitive.content, value.getValue("title").jsonPrimitive.content)
                    else AlertSound.Bundled(AlarmSound.valueOf(value.jsonPrimitive.content))
                }.getOrNull()?.let { id to it }
            }
            .toMap().toMutableMap()
    } catch (e: Exception) {
        aapsLogger.error(LTag.NOTIFICATION, "Stored alert sounds unreadable, every kind plays its own", e)
        mutableMapOf()
    }

    override fun silenceEverywhere(source: String, key: String, minutes: Int) {
        // Here first, at once: the other phones may be out of reach
        silence(source, key, minutes)
        val shared = lock.withLock {
            val entry = entries[source to key] ?: return
            if (entry.silencedUntil <= dateUtil.now()) return
            SharedSilence(source = source, key = key, raisedAt = entry.alert.raisedAt, until = entry.silencedUntil, sentAt = dateUtil.now())
        }
        aapsLogger.info(LTag.NOTIFICATION, "Alert $source/$key silenced everywhere until ${dateUtil.timeString(shared.until)}")
        // The master keeps it for its clients (its hot status doc); a client sends it to the master, which does that
        if (config.AAPSCLIENT) sharedSilenceLink().send(shared)
        else preferences.put(
            StringNonKey.AlertSharedSilences,
            SharedSilence.toJson(SharedSilence.merge(SharedSilence.parse(preferences.get(StringNonKey.AlertSharedSilences)), shared, dateUtil.now()))
        )
    }

    // Only the BG alarms: every phone raises the same ones from the same rules and readings
    override fun canSilenceEverywhere(alert: Alert): Boolean = alert.source == BgAlert.SOURCE && sharedSilenceLink().available()

    override fun applySharedSilence(silence: SharedSilence) {
        val now = dateUtil.now()
        if (!silence.accepts(now)) {
            if (silence.until > now) aapsLogger.warn(LTag.NOTIFICATION, "Shared silence refused, the sender's clock is far off or it is too long: $silence")
            return
        }
        lock.withLock {
            val entry = entries[silence.source to silence.key]
            if (entry != null && silence.isFor(entry.alert.source, entry.alert.key, entry.alert.raisedAt)) {
                // The same silence again (its echo from the master), or a shorter one: nothing to do
                if (entry.silencedUntil >= silence.until) return
                aapsLogger.info(LTag.NOTIFICATION, "Alert ${silence.source}/${silence.key} silenced from another phone until ${dateUtil.timeString(silence.until)}")
                silenceEntryUntil(entry, silence.until)
            } else {
                // Not raised here yet: the readings may come a moment later to this phone
                pendingShared.removeAll { it.source == silence.source && it.key == silence.key }
                pendingShared += silence
            }
        }
    }

    /** Caller holds [lock]. The end of a pending shared silence of [alert]'s occurrence, used up, or 0. */
    private fun takePendingShared(alert: Alert): Long {
        val now = dateUtil.now()
        pendingShared.removeAll { !it.accepts(now) }
        val match = pendingShared.firstOrNull { it.isFor(alert.source, alert.key, alert.raisedAt) } ?: return 0L
        pendingShared.remove(match)
        val until = if (alert.kind.isProtected) minOf(match.until, now + Alerts.PROTECTED_SILENCE_MAX_MINUTES * 60_000L) else match.until
        rememberSilence(alert, until)
        platform.snoozed(alert, until)
        aapsLogger.info(LTag.NOTIFICATION, "Alert ${alert.source}/${alert.key} raised silenced: silenced from another phone until ${dateUtil.timeString(until)}")
        return until
    }

    /**
     * Caller holds [lock]. The end of a silence tapped for [alert] before it was raised again, used up, or 0.
     *
     * The notification names only the source and the key, not the occurrence, so a tap is matched by those and kept
     * only [PENDING_TAP_MINUTES]: long enough for the raiser to raise again after the start, too short to silence a new
     * occurrence the user did not see. The length counts from the tap.
     */
    private fun takePendingTap(alert: Alert): Long {
        val now = dateUtil.now()
        pendingTaps.removeAll { now - it.at > PENDING_TAP_MINUTES * 60_000L }
        val match = pendingTaps.firstOrNull { it.source == alert.source && it.key == alert.key } ?: return 0L
        pendingTaps.remove(match)
        // Silence answers an event, it does not quiet it: kept, it would hide a new one
        if (alert.lifetime == AlertLifetime.EVENT) return 0L
        val minutes = match.minutes ?: alert.silenceMinutes
        val length = if (alert.kind.isProtected) minOf(minutes, Alerts.PROTECTED_SILENCE_MAX_MINUTES) else minutes
        val until = match.at + length * 60_000L
        if (until <= now) return 0L
        rememberSilence(alert, until)
        platform.snoozed(alert, until)
        aapsLogger.info(LTag.NOTIFICATION, "Alert ${alert.source}/${alert.key} raised silenced: silenced from its notification until ${dateUtil.timeString(until)}")
        return until
    }

    /** Caller holds [lock]. Whether [new] shows anything on its card that [old] did not. */
    private fun cardChanged(old: Alert, new: Alert): Boolean =
        old.severity != new.severity || cardText(old) != cardText(new) || systemText(old) != systemText(new) || rh.gs(old.title) != rh.gs(new.title) ||
            old.notificationTitle != new.notificationTitle || old.action?.label?.let { rh.gs(it) } != new.action?.label?.let { rh.gs(it) } ||
            old.canMute != new.canMute || old.tellWatch != new.tellWatch || old.breakThroughDnd != new.breakThroughDnd

    override fun silencedUntil(source: String, key: String): Long = lock.withLock {
        entries[source to key]?.silencedUntil?.takeIf { it > dateUtil.now() } ?: 0L
    }

    override val sounding: Alert?
        get() = lock.withLock {
            val now = dateUtil.now()
            entries.values
                .filter {
                    it.card != null && it.silencedUntil <= now && it.alert.severity == AlertSeverity.CRITICAL &&
                        choiceFor(it.alert) == AlertChoice.RING
                }
                .maxByOrNull { it.alert.raisedAt }
                ?.alert
        }

    override fun setChoice(kind: AlertKind, choice: AlertChoice) {
        if (kind.isProtected) {
            aapsLogger.warn(LTag.NOTIFICATION, "Alert kind ${kind.id} is protected, it always rings: $choice ignored")
            return
        }
        lock.withLock {
            val map = storedChoices()
            // Not the same choice again: below, a kind set to Ring rings what is active of it
            if ((map[kind.id] ?: AlertChoice.RING) == choice) return
            if (choice == AlertChoice.RING) map.remove(kind.id) else map[kind.id] = choice
            saveChoices(map)
            _choices.value = map.toMap()
            aapsLogger.info(LTag.NOTIFICATION, "Alert kind ${kind.id} is now $choice on this phone")
            // At once for what is active now. A kind set back to Ring rings as a new alert does, with the alarm screen,
            // because sound with nothing on the screen to answer it is worse than both. One the user has answered is
            // silenced and stays quiet until its silence ends.
            entries.values.filter { it.alert.kind.id == kind.id }.forEach { showCard(it, ring = choice == AlertChoice.RING) }
        }
    }

    override fun raise(alert: Alert) {
        startObserving()
        lock.withLock {
            val id = alert.source to alert.key
            val existing = entries[id]
            // Published before the card, so the alarm screen the card may open finds the alert in [active].
            if (existing != null && existing.alert.raisedAt == alert.raisedAt) {
                // The same occurrence again: new text or action perhaps, but it does not ring again.
                val changed = cardChanged(existing.alert, alert)
                existing.alert = alert
                publish()
                // Not posted again when nothing on the card changed: a raiser repeats itself (the BG alarms 5 times in
                // 30 ms at a start), and on iOS the removal of each old card lost the race with the new one, so the
                // old cards stayed in the tray after the alert ended (2026-11-25).
                if (existing.card != null && changed) {
                    showCard(existing, ring = false)
                    // A silenced alert rings again from a booking made with the text it had then (iOS showed a BG of
                    // the time of the silence, 2026-11-25): booked again, the booking replaces the old one.
                    if (existing.silencedUntil > dateUtil.now()) platform.snoozed(alert, existing.silencedUntil)
                }
            } else {
                existing?.let { removeCard(it) }
                val entry = Entry(alert, silencedUntil = maxOf(storedSilence(alert), takePendingShared(alert), takePendingTap(alert)))
                entries[id] = entry
                publish()
                showCard(entry, ring = true)
                if (alert.severity != AlertSeverity.INFO) announce(alert)
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
     * A [AlertLifetime.CONDITION] is the pump's, so silence only quiets it and it comes back in [minutes]. A
     * [AlertLifetime.EVENT] is the user's: answering it is the end of it, and a bolus error that came back half an
     * hour later would be telling them about something they have already read.
     */
    override fun silence(source: String, key: String, minutes: Int?) {
        val alert = lock.withLock {
            entries[source to key]?.alert ?: run {
                // A button of a notification left from before a restart, tapped before the raiser raised its alert again:
                // iOS starts the app for the tap. Kept for that raise, as a silence from another phone is.
                pendingTaps.removeAll { it.source == source && it.key == key }
                pendingTaps += PendingTap(source, key, minutes, dateUtil.now())
                null
            }
        } ?: return aapsLogger.info(LTag.NOTIFICATION, "Alert $source/$key is not active yet, its silence is kept for $PENDING_TAP_MINUTES min")
        // Outside the lock: neither call may be made while holding it, it is not reentrant.
        if (alert.lifetime == AlertLifetime.EVENT) resolve(source, key)
        else snooze(source, key, minutes ?: alert.silenceMinutes)
    }

    override fun snooze(source: String, key: String, minutes: Int) = lock.withLock {
        entries[source to key]?.let { silenceEntry(it, minutes) }
        Unit
    }

    /**
     * Runs the alert's own step.
     *
     * A [AlertLifetime.CONDITION] is silenced first, because the user is dealing with it and the pump will take
     * its own time to confirm; the driver resolves it when that happens. A [AlertLifetime.EVENT] is not silenced,
     * because its action *is* the answer: "+5 min" on the "time to eat" timer moves the timer and ends the alarm
     * itself, and when it fails the alarm has to go on ringing - the user still has to eat. So an event is resolved
     * when its action succeeds, and left exactly as it was when it does not.
     */
    override fun act(source: String, key: String) {
        val alert = lock.withLock {
            val entry = entries[source to key] ?: return
            if (entry.alert.lifetime == AlertLifetime.CONDITION) silenceEntry(entry, entry.alert.silenceMinutes)
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

                alert.lifetime == AlertLifetime.EVENT -> resolve(source, key)
            }
        }
    }

    /**
     * Ring again every alert whose silence has ended. Called by the timer of each silence, and by a platform that
     * wakes the app at that time (Android: an exact alarm), because a timer alone runs late while the phone sleeps.
     */
    fun checkSilences() = lock.withLock {
        val now = dateUtil.now()
        // The silences first: one that ends with a pause still on stays quiet, and the pause's end rings it once
        entries.values.filter { it.silencedUntil in 1..now }.forEach { endSilence(it) }
        endPauses(now)
    }

    /** Caller holds [lock]. The pauses that are over end: what is active of their kinds rings. */
    private fun endPauses(now: Long) {
        val map = storedPauses()
        val ended = map.filterValues { it <= now }.keys
        if (ended.isEmpty()) return
        ended.forEach { map.remove(it) }
        savePauses(map)
        aapsLogger.info(LTag.NOTIFICATION, "Pause of alert kinds $ended over on this phone")
        ringAfterPause(ended)
        schedulePauseEnd()
    }

    /** Caller holds [lock]. The pause of [ids] is over: their active alerts ring, unless the user silenced one. */
    private fun ringAfterPause(ids: Set<String>) {
        val now = dateUtil.now()
        entries.values.filter { it.alert.kind.id in ids }.forEach { entry ->
            if (entry.silencedUntil > now) showCard(entry, ring = false)
            else {
                platform.withdrawn(entry.alert.source, entry.alert.key)
                showCard(entry, ring = true)
            }
        }
    }

    /** Caller holds [lock]. A timer for the next pause end, while the process runs; the platform wakes it otherwise. */
    private fun schedulePauseEnd() {
        pauseEnd?.cancel()
        pauseEnd = null
        val next = storedPauses().values.minOrNull() ?: return
        pauseEnd = appScope.launch {
            delay(next - dateUtil.now())
            checkSilences()
        }
    }

    // ---- the bookings (`_docs/ALARMS.md`, step 1e) ----------------------------------------------

    override fun schedule(alert: Alert, at: Long): Boolean = lock.withLock {
        val id = alert.source to alert.key
        val map = storedBookings()
        // A kind the user made silent or turned off books nothing that rings by itself (iOS); the core still raises it
        val ring = choiceFor(alert) == AlertChoice.RING && alert.severity != AlertSeverity.INFO
        if (!platform.book(alert, at, ring)) {
            aapsLogger.warn(LTag.NOTIFICATION, "Alert ${alert.source}/${alert.key} not booked: the phone cannot book it")
            return false
        }
        map.remove(id)?.timer?.cancel()
        val booking = Booking(alert, at)
        map[id] = booking
        saveBookings(map)
        startTimer(booking)
        aapsLogger.debug(LTag.NOTIFICATION, "Alert ${alert.source}/${alert.key} booked for ${dateUtil.dateAndTimeString(at)}")
        true
    }

    override fun unschedule(source: String, key: String) = lock.withLock {
        val map = storedBookings()
        map.remove(source to key)?.let { booking ->
            booking.timer?.cancel()
            saveBookings(map)
            aapsLogger.debug(LTag.NOTIFICATION, "Alert booking $source/$key taken back")
        }
        // Also when nothing is known here: a booking of an earlier process may still be set on the phone
        platform.unbook(source, key)
    }

    /**
     * Raises every booking whose time has come. Called by the timer of each booking, by the platform that wakes the
     * app at that time (Android: an exact alarm), and when the app comes to the front (iOS, where the phone rang by
     * itself while the app was not running).
     *
     * Not before the app start is over: the process may have been started by this very alarm, and a raiser whose
     * alert has an action books it again in the start ("time to eat"), so the alert raised then has its action.
     */
    fun fireDue() {
        appScope.launch {
            while (!config.awaitInitialized()) aapsLogger.debug(LTag.NOTIFICATION, "Alert bookings wait for the app start")
            val due = lock.withLock {
                val now = dateUtil.now()
                val map = storedBookings()
                // A little early is the same moment: a phone alarm can come a moment before its time
                val ready = map.values.filter { it.at <= now + EARLY_MS }
                if (ready.isEmpty()) return@launch
                ready.forEach { map.remove(it.alert.source to it.alert.key)?.timer?.cancel() }
                saveBookings(map)
                ready
            }
            due.forEach { booking ->
                val alert = booking.alert
                platform.unbook(alert.source, alert.key)
                val late = dateUtil.now() - booking.at
                if (late > Alerts.LATE_LIMIT_MINUTES * 60_000L)
                    aapsLogger.info(LTag.NOTIFICATION, "Alert booking ${alert.source}/${alert.key} dropped: found ${late / 60_000} min after its time")
                else raise(alert.raisedAt(booking.at))
            }
        }
    }

    /** Caller holds [lock]. */
    private fun startTimer(booking: Booking) {
        // The wait counted now, when it is booked, not when the coroutine first runs
        val wait = booking.at - dateUtil.now()
        booking.timer = appScope.launch {
            delay(wait)
            fireDue()
        }
    }

    /** The same alert, raised at [at]: the time of its booking is the time of the occurrence. */
    private fun Alert.raisedAt(at: Long) = Alert(
        kind = kind, key = key, severity = severity, title = title, text = text, raisedAt = at, action = action,
        lifetime = lifetime, notificationTitle = notificationTitle, canMute = canMute, tellWatch = tellWatch, sound = sound,
        silenceMinutes = silenceMinutes, breakThroughDnd = breakThroughDnd
    )

    // ---- the cards ----------------------------------------------------------------------------

    /** Caller holds [lock]. Posts (or re-posts) the card of [entry]; [ring] = this is the moment it starts to ring. */
    private fun showCard(entry: Entry, ring: Boolean) {
        entry.card?.let { old ->
            entry.card = null
            notificationManager.dismiss(old)
        }
        val alert = entry.alert
        val choice = choiceFor(alert)
        if (choice == AlertChoice.OFF) {
            // Not shown on this phone; the announcement still records it, and its raiser still resolves it
            if (ring) aapsLogger.info(LTag.NOTIFICATION, "Alert ${alert.source}/${alert.key} not shown: ${alert.kind.id} is off on this phone")
            entry.silenceEnd?.cancel()
            entry.silenceEnd = null
            return
        }
        val quiet = choice == AlertChoice.SILENT
        val silenced = entry.silencedUntil > dateUtil.now()
        val level = when {
            // A silent kind is never the alarm tier: an URGENT card is only the card of an alert that rings
            quiet && alert.severity != AlertSeverity.INFO -> NotificationLevel.IMPORTANT
            alert.severity == AlertSeverity.CRITICAL      -> NotificationLevel.URGENT
            alert.severity == AlertSeverity.WARNING       -> NotificationLevel.IMPORTANT
            else                                          -> NotificationLevel.NORMAL
        }
        entry.card = notificationManager.post(
            id = NotificationId.ALERT,
            text = cardText(alert),
            level = level,
            date = alert.raisedAt,
            validTo = 0L,
            sound = if (alert.severity == AlertSeverity.CRITICAL && !silenced && !quiet) soundFor(alert) else null,
            ringtoneUri = if (alert.severity == AlertSeverity.CRITICAL && !silenced && !quiet) ringtoneFor(alert) else null,
            actions = alert.action?.let { listOf(NotificationAction(it.label) { act(alert.source, alert.key) }) } ?: emptyList(),
            validityCheck = null,
            // What the system notification and the watch announce it as: the title the caller gave, else the alert's
            // own ("Low", "Occlusion"). Named by its level instead, a WARNING reached the tray as "Info" (2026-11-25).
            alarmTitle = alert.notificationTitle ?: rh.gs(alert.title),
            canMute = alert.canMute,
            tellWatch = alert.tellWatch,
            breakThroughDnd = alert.breakThroughDnd,
            systemText = systemText(alert)
        )
        val pausedUntil = storedPauses()[alert.kind.id] ?: 0L
        when {
            // Paused: the end of the pause rings it, booked so it does also when the app does not run then
            quiet && pausedUntil > dateUtil.now()                   -> platform.snoozed(alert, maxOf(pausedUntil, entry.silencedUntil))
            quiet                                                   -> Unit
            silenced                                                -> scheduleSilenceEnd(entry)
            !ring                                                   -> Unit
            alert.severity == AlertSeverity.CRITICAL -> platform.showAlarmScreen(alert)
            alert.severity == AlertSeverity.WARNING  -> platform.chime(alert, soundFor(alert), ringtoneFor(alert))
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

    /** Caller holds [lock]. A protected kind is silenced only briefly, whatever the caller asked for. */
    private fun silenceEntry(entry: Entry, minutes: Int) {
        val length = if (entry.alert.kind.isProtected) minOf(minutes, Alerts.PROTECTED_SILENCE_MAX_MINUTES) else minutes
        if (length != minutes) aapsLogger.info(LTag.NOTIFICATION, "Alert ${entry.alert.kind.id} is protected: silenced $length min, not $minutes")
        silenceEntryUntil(entry, dateUtil.now() + length * 60_000L)
    }

    /** Caller holds [lock]. Silences [entry] until [until]; a protected kind for a short time at most. */
    private fun silenceEntryUntil(entry: Entry, until: Long) {
        val capped = if (entry.alert.kind.isProtected) minOf(until, dateUtil.now() + Alerts.PROTECTED_SILENCE_MAX_MINUTES * 60_000L) else until
        entry.silencedUntil = capped
        rememberSilence(entry.alert, capped)
        platform.snoozed(entry.alert, capped)
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
     * The text of the system notification and the watch, which show the alert's title above it: the alert's text
     * alone, empty when it has none, so the title is not shown twice. Null for an alarm whose caller gave its own
     * title: its card text is already the text alone.
     */
    private fun systemText(alert: Alert): String? =
        if (alert.notificationTitle != null) null else alert.text?.let { rh.gs(it) } ?: ""

    /**
     * What the card in the app says.
     *
     * Title and text together, because the card in the app has no title line and would otherwise lose which alert it
     * is; the system notification and the watch get the text alone ([systemText]). An alarm that brought its own title
     * does not need that: the title is already on the card and on the watch, so repeating it would say it twice.
     */
    private fun cardText(alert: Alert): String {
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
        // Made now, not when the first alarm screen asks: the master learns from it whether it has a client, and the
        // screen should not miss "Silence everywhere" while that is still being read
        appScope.launch { sharedSilenceLink() }
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
                alert.lifetime == AlertLifetime.EVENT                      -> {
                    aapsLogger.debug(LTag.PUMP, "Alert card removed, and it was the answer: ${alert.source}/${alert.key}")
                    entries.remove(alert.source to alert.key)?.let { end(it) }
                    ended = true
                }
                // Read, and nothing to do about it.
                alert.severity == AlertSeverity.INFO && alert.action == null -> Unit

                else                                                             -> {
                    aapsLogger.debug(LTag.PUMP, "Pump alert card removed while active, shown again silenced: ${alert.source}/${alert.key}")
                    if (entry.silencedUntil > dateUtil.now()) showCard(entry, ring = false)
                    else silenceEntry(entry, alert.silenceMinutes)
                }
            }
        }
        if (ended) publish()
    }

    /**
     * The alert as an announcement: the one record of it, written by the core for every raiser.
     *
     * Written when it is raised, with [Alert.raisedAt], so the record carries the true time whatever the user does
     * with the alert afterwards: answers it on the phone, on the watch, or never at all. A re-ring writes nothing, as
     * only the first raise of an occurrence comes here.
     *
     * Only on the master: an announcement a client wrote would reach Nightscout and come back to the master as a
     * second one. A pump's alert is filed under the active pump, with its type and serial; any other one is the app's
     * own, with no pump identity (`_docs/ALARMS.md`, section 6).
     *
     * Not for a kind that is not [app.aaps.core.interfaces.alerts.AlertKind.announced]: a BG alarm (every phone raises
     * its own from the same readings and rules, so a client would hear it twice, decided 2026-11-23), an alert from
     * Nightscout (its record is already there) and carbs required (the loop writes its own).
     *
     * Not when the user turned off "Create announcements from errors" (decided 2026-11-24): the setting is for every
     * alert, as pump unreachable followed it before it was an alert.
     */
    private fun announce(alert: Alert) {
        if (!config.APS) return
        if (!preferences.get(BooleanKey.NsClientCreateAnnouncementsFromErrors)) return
        if (!alert.kind.announced) return
        val note = cardText(alert)
        appScope.launch {
            try {
                when (alert.origin) {
                    AlertOrigin.PUMP -> {
                        val pump = activePlugin.activePump
                        pumpSync.insertTherapyEventIfNewWithTimestamp(
                            timestamp = alert.raisedAt,
                            type = TE.Type.ANNOUNCEMENT,
                            note = note,
                            pumpType = pump.model(),
                            pumpSerial = pump.serialNumber()
                        )
                    }

                    // A CGM's alert has no pump identity either
                    AlertOrigin.APP,
                    AlertOrigin.CGM  -> persistenceLayer.insertPumpTherapyEventIfNewByTimestamp(
                        therapyEvent = TE.asAnnouncement(note).copy(timestamp = alert.raisedAt),
                        action = Action.CAREPORTAL,
                        source = Sources.Aaps,
                        note = note,
                        listValues = listOf(ValueWithUnit.TEType(TE.Type.ANNOUNCEMENT))
                    )

                    AlertOrigin.NIGHTSCOUT -> Unit
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                aapsLogger.error(LTag.NOTIFICATION, "Alert announcement failed", e)
            }
        }
    }

    // ---- the stored silences --------------------------------------------------------------------

    /** Caller holds [lock]. */
    private fun storedSilences(): MutableList<Silence> = silences ?: loadSilences().also { silences = it }

    /** Caller holds [lock]. Until when this occurrence is silenced, or 0. */
    private fun storedSilence(alert: Alert): Long {
        val now = dateUtil.now()
        return storedSilences().firstOrNull { it.source == alert.source && it.key == alert.key && it.raisedAt == alert.raisedAt && it.until > now }?.until ?: 0L
    }

    // ---- the user's choices ---------------------------------------------------------------------

    /** Caller holds [lock]. The stored choices, read on first use: building the object graph reads nothing. */
    private fun storedChoices(): MutableMap<String, AlertChoice> =
        choiceMap ?: loadChoices().also {
            choiceMap = it
            _choices.value = it.toMap()
        }

    /** Caller holds [lock]. What this phone does with [alert]; a protected kind always rings. */
    /** Caller holds [lock]. A paused kind that rings is Silent while its pause lasts. */
    private fun choiceFor(alert: Alert): AlertChoice {
        if (alert.kind.isProtected) return AlertChoice.RING
        val chosen = storedChoices()[alert.kind.id] ?: AlertChoice.RING
        val paused = (storedPauses()[alert.kind.id] ?: 0L) > dateUtil.now()
        return if (chosen == AlertChoice.RING && paused) AlertChoice.SILENT else chosen
    }

    // ---- the pauses -----------------------------------------------------------------------------

    /** Caller holds [lock]. The stored pauses, read on first use; one that ended while the app did not run is gone. */
    private fun storedPauses(): MutableMap<String, Long> =
        pauseMap ?: loadPauses().also {
            pauseMap = it
            _pauses.value = it.toMap()
            if (it.isNotEmpty()) schedulePauseEnd()
        }

    private fun loadPauses(): MutableMap<String, Long> = try {
        val now = dateUtil.now()
        Json.parseToJsonElement(preferences.get(StringNonKey.AlertPauses)).jsonObject
            .mapValues { it.value.jsonPrimitive.long }
            .filterValues { it > now }
            .toMutableMap()
    } catch (e: Exception) {
        aapsLogger.error(LTag.NOTIFICATION, "Stored alert pauses unreadable, nothing paused", e)
        mutableMapOf()
    }

    /** Caller holds [lock]. */
    private fun savePauses(map: Map<String, Long>) {
        preferences.put(StringNonKey.AlertPauses, buildJsonObject { map.forEach { (id, until) -> put(id, until) } }.toString())
        _pauses.value = map.toMap()
    }

    private fun loadChoices(): MutableMap<String, AlertChoice> = try {
        Json.parseToJsonElement(preferences.get(StringNonKey.AlertChoices)).jsonObject
            .mapNotNull { (id, value) -> AlertChoice.entries.firstOrNull { it.name == value.jsonPrimitive.content }?.let { id to it } }
            .filter { it.second != AlertChoice.RING }
            .toMap(mutableMapOf())
    } catch (e: Exception) {
        aapsLogger.error(LTag.NOTIFICATION, "Stored alert choices unreadable, every kind rings", e)
        mutableMapOf()
    }

    private fun saveChoices(map: Map<String, AlertChoice>) {
        val json = buildJsonObject { map.forEach { (id, choice) -> put(id, choice.name) } }
        preferences.put(StringNonKey.AlertChoices, json.toString())
    }

    /** Caller holds [lock]. */
    private fun rememberSilence(alert: Alert, until: Long) {
        val list = storedSilences()
        list.removeAll { it.source == alert.source && it.key == alert.key }
        list.add(Silence(alert.source, alert.key, alert.raisedAt, until))
        saveSilences(list)
    }

    /** Caller holds [lock]. */
    private fun forgetSilence(alert: Alert) {
        val list = storedSilences()
        if (list.removeAll { it.source == alert.source && it.key == alert.key }) saveSilences(list)
    }

    private fun loadSilences(): MutableList<Silence> {
        val now = dateUtil.now()
        return try {
            Json.parseToJsonElement(preferences.get(StringNonKey.AlertSilences)).jsonArray.map { element ->
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
        preferences.put(StringNonKey.AlertSilences, json.toString())
    }

    // ---- the stored bookings ---------------------------------------------------------------------

    /**
     * Caller holds [lock]. The bookings, read on first use. A booking read back has no timer: the platform wakes the app
     * for it, and every way back in calls [fireDue].
     */
    private fun storedBookings(): MutableMap<Pair<String, String>, Booking> = bookings ?: loadBookings().also { bookings = it }

    /**
     * Saved with resolved text, because a [app.aaps.core.keys.interfaces.TextRef] cannot be stored, and without the
     * action, because a function cannot. The kind is found again by its id.
     */
    private fun saveBookings(map: Map<Pair<String, String>, Booking>) {
        val json = buildJsonArray {
            map.values.forEach { b ->
                val a = b.alert
                add(buildJsonObject {
                    put("kind", a.kind.id)
                    put("key", a.key)
                    put("at", b.at)
                    put("severity", a.severity.name)
                    put("title", rh.gs(a.title))
                    a.text?.let { put("text", rh.gs(it)) }
                    put("lifetime", a.lifetime.name)
                    a.notificationTitle?.let { put("notificationTitle", it) }
                    put("canMute", a.canMute)
                    put("tellWatch", a.tellWatch)
                    a.sound?.let { put("sound", it.name) }
                    put("silenceMinutes", a.silenceMinutes)
                    put("breakThroughDnd", a.breakThroughDnd)
                })
            }
        }
        preferences.put(StringNonKey.AlertBookings, json.toString())
    }

    private fun loadBookings(): MutableMap<Pair<String, String>, Booking> {
        val kinds = catalogues().flatMap { it.kinds }.associateBy { it.id }
        val map = mutableMapOf<Pair<String, String>, Booking>()
        try {
            Json.parseToJsonElement(preferences.get(StringNonKey.AlertBookings)).jsonArray.forEach { element ->
                val o = element.jsonObject
                val kind = kinds[o.text("kind")]
                if (kind == null) {
                    // The module that raised it is not in this build any more
                    aapsLogger.warn(LTag.NOTIFICATION, "Alert booking of unknown kind ${o.text("kind")} dropped")
                    return@forEach
                }
                val alert = Alert(
                    kind = kind,
                    key = o.text("key"),
                    severity = AlertSeverity.valueOf(o.text("severity")),
                    title = TextRef.Literal(o.text("title")),
                    text = o["text"]?.jsonPrimitive?.content?.let { TextRef.Literal(it) },
                    raisedAt = o.number("at"),
                    lifetime = AlertLifetime.valueOf(o.text("lifetime")),
                    notificationTitle = o["notificationTitle"]?.jsonPrimitive?.content,
                    canMute = o.getValue("canMute").jsonPrimitive.boolean,
                    tellWatch = o.getValue("tellWatch").jsonPrimitive.boolean,
                    sound = o["sound"]?.jsonPrimitive?.content?.let { name -> AlarmSound.entries.firstOrNull { it.name == name } },
                    silenceMinutes = o["silenceMinutes"]?.jsonPrimitive?.long?.toInt() ?: Alerts.SILENCE_MINUTES,
                    breakThroughDnd = o["breakThroughDnd"]?.jsonPrimitive?.boolean ?: false
                )
                map[alert.source to alert.key] = Booking(alert, o.number("at"))
            }
        } catch (e: Exception) {
            aapsLogger.error(LTag.NOTIFICATION, "Stored alert bookings unreadable, dropped", e)
            map.clear()
        }
        return map
    }

    private fun JsonObject.text(name: String): String = getValue(name).jsonPrimitive.content
    private fun JsonObject.number(name: String): Long = getValue(name).jsonPrimitive.long

    private companion object {

        /** An alarm of the phone may come a moment before its time; that is still its time. */
        const val EARLY_MS = 1_000L

        /** The owner of a test sound: its stop never stops a real alarm. */
        const val OWNER_TEST = "alert-test"

        /** The key of a test chime. */
        const val TEST_KEY = "test"

        /** How long a silence tapped for an alert not raised yet waits for its raise. */
        const val PENDING_TAP_MINUTES = 2
    }
}
