package app.aaps.plugins.source

import androidx.annotation.VisibleForTesting
import app.aaps.core.data.model.CA
import app.aaps.core.data.model.GV
import app.aaps.core.data.model.IDs
import app.aaps.core.data.model.SourceSensor
import app.aaps.core.data.model.TrendArrow
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.data.time.T
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.configuration.ExternalOptions
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.plugin.EnforcedState
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.pump.VirtualPump
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.source.BgSource
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.compose.icons.IcPluginRandomBg
import app.aaps.core.ui.compose.preference.PreferenceSubScreenDef
import app.aaps.plugins.source.compose.BgSourceComposeContent
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant
import kotlin.time.TimeSource
import dev.zacsweers.metro.IntKey as MetroIntKey

/**
 * A made-up glucose source for testing and demonstration: a slow sine wave between `MIN` and `MAX`,
 * one reading every few minutes, with some noise and the odd carb entry when "randomize" is on.
 *
 * Shared code on every platform. It was Android-only before, only because of how it kept time: a
 * `Handler` thread, a `GregorianCalendar` and a wake lock. The timer is a coroutine now and the local
 * time comes from `kotlinx-datetime`, which gives the same minute and hour the calendar did, so the
 * readings are the same as before. The wake lock is the one part that differs by platform, and sits
 * behind [RandomBgWakeLock].
 */
@ContributesIntoMap(AppScope::class, binding = binding<PluginBase>())
@MetroIntKey(550)
@SingleIn(AppScope::class)
@Inject
class RandomBgPlugin(
    rh: TextResolver,
    aapsLogger: AAPSLogger,
    private val persistenceLayer: PersistenceLayer,
    private val virtualPump: VirtualPump,
    preferences: Preferences,
    config: Config,
    notificationManager: NotificationManager,
    private val dateUtil: DateUtil,
    private val appScope: CoroutineScope,
    private val wakeLock: RandomBgWakeLock
) : AbstractBgSourcePlugin(
    PluginDescription()
        .mainType(PluginType.BGSOURCE)
        .composeContent { plugin ->
            BgSourceComposeContent(
                title = rh.gs(SourceStrings.random_bg)
            )
        }
        .icon(IcPluginRandomBg)
        .pluginName(SourceStrings.random_bg)
        .preferencesVisibleInSimpleMode(false)
        // A debug BG source: only on the virtual pump in an engineering build, or with the
        // unfinished-features option file. Forced off otherwise. This is the one enforcement that reads
        // something other than build config - `virtualPump.isEnabled()` changes when the user picks another
        // pump - which is why the virtual pump must never gain an enforcement that reads this plugin back.
        //
        // It also allowed itself inside an instrumented test, through `isRunningTest()`. That was its only
        // caller, and no test relied on it: the tests that need random readings write them themselves.
        .enforce(EnforcedState.Disabled) {
            !(virtualPump.isEnabled() && config.isEngineeringMode() || config.isEnabled(ExternalOptions.UNFINISHED_MODE))
        }
        .description(SourceStrings.description_source_random_bg),
    aapsLogger = aapsLogger,
    rh = rh,
    preferences = preferences,
    config = config,
    notificationManager = notificationManager
), BgSource {

    /** The refresh loop while the plugin runs, null while it is stopped. */
    @VisibleForTesting
    internal var job: Job? = null
    private var held: RandomBgWakeLock.Held? = null
    private var interval = 5L // minutes

    companion object {

        const val MIN = 70 // mgdl
        const val MAX = 190 // mgdl
        const val PERIOD = 120.0 // minutes
    }

    private fun updateInterval() {
        interval = preferences.get(IntKey.BgSourceRandomInterval).toLong()
    }

    override suspend fun onStart() {
        super.onStart()
        stopLoop()
        updateInterval()
        // The first reading lands one second after the next whole interval, as it always has.
        val firstAt = roundedToInterval(dateUtil.now()) + T.mins(interval).msecs() + 1000
        job = appScope.launch {
            delay(firstAt - dateUtil.now())
            while (isActive) {
                updateInterval()
                // Timed from before the work, so a slow insert does not push every later reading back.
                val started = TimeSource.Monotonic.markNow()
                handleNewData()
                delay(T.mins(interval).msecs().milliseconds - started.elapsedNow())
            }
        }
        held = wakeLock.acquire()
    }

    override suspend fun onStop() {
        super.onStop()
        stopLoop()
    }

    private fun stopLoop() {
        job?.cancel()
        job = null
        held?.release()
        held = null
    }

    @VisibleForTesting
    internal suspend fun handleNewData() {
        if (!isEnabled()) return

        val randomize = preferences.get(BooleanKey.BgSourceRandomBgRandomize)
        val now = dateUtil.now()
        val local = Instant.fromEpochMilliseconds(now).toLocalDateTime(TimeZone.currentSystemDefault())
        val currentMinute = local.minute + (local.hour % 2) * 60
        val sineBgMgdl = MIN + ((MAX - MIN) + (MAX - MIN) * sin(currentMinute / PERIOD * 2 * PI)) / 2
        val bgMgdl =
            if (randomize) sineBgMgdl + (Random.nextDouble() - 0.5) * (MAX - MIN) * 0.08 * interval
            else sineBgMgdl

        val rounded = roundedToInterval(now)
        val timestamp =
            if (randomize) rounded - T.secs(40).msecs() + Random.nextInt(T.secs(40).msecs().toInt())
            else rounded
        val trend =
            if (randomize) TrendArrow.entries.random()
            else trendFromSineSlope(currentMinute)
        val glucoseValues = listOf(
            GV(
                timestamp = timestamp,
                value = bgMgdl,
                raw = 0.0,
                noise = null,
                trendArrow = trend,
                sourceSensor = SourceSensor.RANDOM
            )
        )
        persistenceLayer.insertCgmSourceData(Sources.Random, glucoseValues, emptyList(), null)

        //  Generate carbs around once in 4 hours (skipped in deterministic mode)
        if (randomize && Random.nextDouble() <= 0.02) {
            val ca = CA(
                timestamp = rounded + T.mins(1).msecs(),
                isValid = true,
                amount = Random.nextInt(50).toDouble(),
                duration = 0,
                notes = "Random carbs",
                ids = IDs()
            )
            persistenceLayer.insertOrUpdateCarbs(ca, Action.TREATMENT, Sources.CarbDialog, ca.notes)
        }
    }

    /**
     * [millis] with seconds and milliseconds cleared, and the local minute rounded down to the interval -
     * what the calendar arithmetic did before. Seconds are the same in every time zone, and the minute is
     * read in local time, so this gives the same result, half-hour time zones included.
     */
    private fun roundedToInterval(millis: Long): Long {
        val local = Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.currentSystemDefault())
        return millis - millis.mod(T.mins(1).msecs()) - (local.minute % interval) * T.mins(1).msecs()
    }

    // Derivative of MIN + (MAX - MIN) * (1 + sin(currentMinute / PERIOD * 2π)) / 2 with respect to minute.
    private fun trendFromSineSlope(currentMinute: Int): TrendArrow {
        val slopeMgdlPerMin = (MAX - MIN) * PI / PERIOD * cos(currentMinute / PERIOD * 2 * PI)
        return when {
            slopeMgdlPerMin > 3.0  -> TrendArrow.DOUBLE_UP
            slopeMgdlPerMin > 2.0  -> TrendArrow.SINGLE_UP
            slopeMgdlPerMin > 1.0  -> TrendArrow.FORTY_FIVE_UP
            slopeMgdlPerMin < -3.0 -> TrendArrow.DOUBLE_DOWN
            slopeMgdlPerMin < -2.0 -> TrendArrow.SINGLE_DOWN
            slopeMgdlPerMin < -1.0 -> TrendArrow.FORTY_FIVE_DOWN
            else                   -> TrendArrow.FLAT
        }
    }

    override fun getPreferenceScreenContent() = PreferenceSubScreenDef(
        key = "bg_source_upload_settings",
        title = SourceStrings.random_bg,
        items = listOf(
            BooleanKey.BgSourceUploadToNs,
            IntKey.BgSourceRandomInterval,
            BooleanKey.BgSourceRandomBgRandomize
        ),
        icon = pluginDescription.icon
    )
}
