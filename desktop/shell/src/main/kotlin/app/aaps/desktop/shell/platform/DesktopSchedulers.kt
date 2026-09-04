package app.aaps.desktop.shell.platform

import app.aaps.core.interfaces.alerts.ReminderScheduler
import app.aaps.core.interfaces.di.ApplicationScope
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.NotificationId
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.implementation.scenes.SceneExpiryRunner
import app.aaps.implementation.scenes.SceneExpiryScheduler
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Automation's alarm action, as a coroutine that waits and then posts.
 *
 * Real work rather than a placeholder: waiting and then showing a message is something a desktop
 * does perfectly well, and the shared code above does not care how the waiting happens.
 *
 * **The wait lives in this process.** Android schedules through `AlarmManager`, so a reminder
 * survives the app being killed and can wake the device. Here, closing AAPS forgets every pending
 * reminder. That matters for one set hours ahead, which is why it is written down rather than left
 * to look equivalent.
 */
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
class DesktopReminderScheduler @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val notificationManager: NotificationManager,
    @ApplicationScope private val scope: CoroutineScope
) : ReminderScheduler {

    override fun scheduleReminder(seconds: Int, text: String) {
        aapsLogger.debug(LTag.CORE, "Reminder in ${seconds}s: $text")
        scope.launch {
            delay(seconds * 1000L)
            notificationManager.post(NotificationId.TOAST_ALARM, text)
        }
    }
}

/**
 * Ends a timed scene when its time is up, by running the shared expiry rule.
 *
 * **This has to run code, not show a message.** [SceneExpiryRunner] reverts the two things that do
 * not end on their own - the SMB toggle, a preference with no duration, and the profile switch,
 * whose `EffectiveProfileSwitch` outlives the timed record it came from - then marks the scene
 * expired and starts any chained follow-up. Skipping it would leave both applied indefinitely, on
 * therapy settings, with nothing on screen to say so. The Apple side cannot schedule exact
 * background work and therefore refuses timed scenes outright; a desktop JVM has an ordinary timer,
 * so here the contract is genuinely honoured.
 *
 * **While the app is running.** The timer is in this process, so a scene whose end falls after the
 * window is closed is not reverted at that moment - it is reverted at the next start, when the
 * scene code re-schedules an already-overdue expiry and the delay returns immediately. Late rather
 * than never, and worth knowing before relying on a scene that outlives a session.
 *
 * **The runner is looked up when it is needed, not when the graph is built.** There is a cycle
 * otherwise, and a real one: `SceneExpiryRunner` needs `SceneExecutor`, which needs this scheduler
 * back. Android never meets it because WorkManager constructs the runner itself, so it is not a
 * graph node there - the same reason `LazyCalculationExecutor` exists. Deferring is safe rather than
 * merely convenient: nothing is resolved while the graph is assembled, and the first lookup happens
 * when a scene actually expires, by which time every object in the loop exists.
 *
 * One expiry is pending at a time; scheduling another replaces it, which matches there being one
 * active scene.
 */
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
class DesktopSceneExpiryScheduler @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val expiryRunner: () -> SceneExpiryRunner,
    @ApplicationScope private val scope: CoroutineScope
) : SceneExpiryScheduler {

    private var pending: Job? = null

    override fun schedule(sceneName: String, delayMs: Long) {
        cancel()
        aapsLogger.debug(LTag.CORE, "Scene '$sceneName' expires in ${delayMs}ms")
        pending = scope.launch {
            delay(delayMs)
            val outcome = expiryRunner().run(sceneName)
            aapsLogger.debug(LTag.CORE, "Scene '$sceneName' expiry finished: $outcome")
        }
    }

    override fun cancel() {
        pending?.cancel()
        pending = null
    }
}
