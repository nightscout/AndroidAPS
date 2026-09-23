package app.aaps.core.interfaces.plugin

import app.aaps.core.data.plugin.PluginType
import app.aaps.core.interfaces.InterfacesStrings
import app.aaps.core.interfaces.concurrent.AapsLock
import app.aaps.core.interfaces.concurrent.withLock
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.AlarmSound
import app.aaps.core.interfaces.notifications.NotificationHandle
import app.aaps.core.interfaces.notifications.NotificationId
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.plugin.PluginBase.Companion.TRANSITION_WAIT
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.keys.interfaces.PreferenceItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.concurrent.Volatile
import kotlin.time.Duration.Companion.seconds

/**
 * Created by mike on 09.06.2016.
 */
abstract class PluginBase(
    val pluginDescription: PluginDescription,
    val aapsLogger: AAPSLogger,
    open val rh: TextResolver,
    protected val notificationManager: NotificationManager
) {

    /**
     * Work the plugin starts. Process-lifetime, and never cancelled. That is a settled decision, not a
     * gap waiting to be closed.
     *
     * Making it per-enable was proposed and rejected once the 14 `pluginScope.launch` calls were written
     * down one by one - see `PluginLifetimeWorkScanTest.pluginScopeLaunches`, which fails the build on a
     * new one. The reason it was rejected: cancelling this scope does NOT withdraw a queued command.
     * `CommandQueueImplementation.readStatus` is `add` then `notifyAboutNewCommand` then
     * `deferred.await()`, so cancelling the caller at the await leaves the command in the queue and still
     * running - it only throws away the answer. Twelve of the fourteen sites are status reads whose result
     * can be abandoned safely; `PumpPluginBase.onStart` already cancels its own job explicitly, which is
     * the only place cancelling really prevents anything; and `OmnipodDashPumpPlugin.handleCommandConfirmation`
     * must NOT be cancelled at all, because it delivers a basal correction the pod asked for and the result
     * is the only record that it worked.
     *
     * If a stopping driver must stop being driven, the command has to leave the QUEUE. That is a queue
     * change, and this scope is the wrong lever for it.
     *
     * [SupervisorJob], though, because a plain `Job` made one failing child kill the scope for good and
     * every later launch on it a silent no-op.
     *
     * And a handler, because without one a throw here reaches the thread's default handler, which on
     * Android ends the process. The supervisor job only saved the scope; the app still died. Roughly fifteen
     * `pluginScope.launch` calls across the pump drivers queue pump commands, so this is reachable from a
     * failing pump.
     */
    protected val pluginScope = CoroutineScope(
        Dispatchers.Default + SupervisorJob() +
            CoroutineExceptionHandler { _, e -> onLaunchedWorkFailed(e) }
    )

    /**
     * Runs [onStart] / [onStop], and nothing else.
     *
     * Separate from [pluginScope] so a plugin cancelling its own work can never cancel the transition
     * that is doing the cancelling. Immortal, and safe to be: [runPhase] catches everything a phase can
     * throw, and the handler is the last resort for anything thrown outside that try.
     */
    private val lifecycleScope = CoroutineScope(
        Dispatchers.Default + SupervisorJob() +
            CoroutineExceptionHandler { _, e -> aapsLogger.error(LTag.CORE, "Plugin lifecycle failed: $name", e) }
    )

    /**
     * The last [onStart] threw, so this plugin is enabled but only half built.
     *
     * It is NOT disabled: AAPS needs a pump driver active, and disabling the failed one makes
     * `ActivePlugin.activePumpInternal` throw "No pump selected". Instead the failure is visible - loudly
     * to the user through an URGENT notification, and to the code through here. `PumpWithConcentrationImpl`
     * reports a pump in this state as not initialized, because `isInitialized()` on the drivers reads
     * device state (last connection, pod running) that survives a stop, so on a restart it would otherwise
     * answer true for a driver whose service never came back up.
     */
    @Volatile
    var lastStartFailed: Boolean = false
        private set

    /**
     * The card [runPhase] posted for this plugin's own failure, so it can take back that one and no other.
     *
     * [NotificationId.PLUGIN_START_FAILED] is shared by every plugin, and `dismiss(id)` removes every card
     * carrying it. Dismissing by id here would mean one plugin starting cleanly clears the alarm of another
     * that is still broken, while its [lastStartFailed] stays set - a pump that quietly refuses to dose with
     * nothing on screen to say why.
     */
    @Volatile
    private var startFailureCard: NotificationHandle? = null

    /** Same idea for [pluginScope], so one plugin failing over and over leaves one card, not a pile. */
    @Volatile
    private var workFailureCard: NotificationHandle? = null

    /**
     * Work this plugin launched on [pluginScope] ended with an error.
     *
     * Nothing can be undone from here - the coroutine is gone and only the plugin knows what it was doing -
     * so the job is to make sure it is not lost. Not routed through [lastStartFailed]: the plugin did start,
     * and a pump that started and then lost a polling loop is a different state from one that never came up.
     */
    private fun onLaunchedWorkFailed(e: Throwable) {
        // The throwable goes to the log only. It is developer text, untranslated, and can be a page long.
        aapsLogger.error(LTag.CORE, "Work launched by $name failed", e)
        workFailureCard?.let { notificationManager.dismiss(it) }
        workFailureCard = notificationManager.post(
            NotificationId.PLUGIN_WORK_FAILED,
            rh.gs(InterfacesStrings.plugin_work_failed, name),
            validMinutes = 0,
            sound = AlarmSound.ALARM
        )
    }

    /**
     * The previous transition. Start and stop of one plugin must not run at the same time.
     *
     * Guarded by [transitionLock] rather than only [Volatile]: [schedule] reads this and then writes it,
     * and volatile alone makes each half visible without making the pair atomic - two callers arriving
     * together would both read the same predecessor and queue behind it, running concurrently. That is
     * the one thing the queueing exists to prevent, and it is reachable: `ConfigBuilderImpl` disables
     * several plugins and enables one in the same pass.
     */
    private var lastTransition: Job? = null

    /**
     * Guards the read-then-write of [lastTransition]. Its own object, never reassigned - locking on
     * something that gets replaced lets a second thread lock the new one and walk straight in.
     */
    private val transitionLock = AapsLock()

    enum class State {
        NOT_INITIALIZED, ENABLED, DISABLED
    }

    private var state = State.NOT_INITIALIZED

    open val name: String
        get() = pluginDescription.pluginName?.let { rh.gs(it) } ?: "UNKNOWN"

    /**
     * Stable identity for syncing the active-plugin selection (see the `ActivePlugin*` keys). Defaults to
     * the class simple name — matching the legacy `RunningConfiguration` encoding, so dual-write stays
     * consistent. Override to decouple from the class name (survive rename / R8) when a durable id is needed.
     */
    // this::class.simpleName rather than javaClass.simpleName, so this file is not tied to the JVM. It
    // gives the same string: a plugin is always a named class. simpleName is only null for an anonymous
    // one, which would be a bug worth failing on rather than syncing an empty id.
    open val pluginId: String get() = this::class.simpleName!!

    //only if translation exists
    // use long name as fallback
    val nameShort: String
        get() {
            val shortNameRef = pluginDescription.shortName ?: return name
            val translatedName = rh.gs(shortNameRef)
            return if (translatedName.trim { it <= ' ' }.isNotEmpty()) translatedName else name
            // use long name as fallback
        }

    val description: String?
        get() = pluginDescription.description?.let { rh.gs(it) }

    fun getType(): PluginType = pluginDescription.mainType

    open fun isEnabled() = isEnabled(pluginDescription.mainType)

    /**
     * What this build forces, or `null` when the user's stored choice decides. See [Enforcement].
     *
     * A CONSTRAINTS plugin is enabled by virtue of being registered: which constraint plugins exist is a
     * property of the build, and one that is present must always be consulted. That is why the rule lives
     * here rather than being declared on each of them - a new constraint plugin cannot forget it.
     *
     * Disabled wins a disagreement, so a wrong declaration fails closed.
     */
    fun enforcedState(): EnforcedState? {
        if (pluginDescription.mainType == PluginType.CONSTRAINTS) return EnforcedState.Enabled
        val applying = pluginDescription.enforcements.filter { it.applies() }
        return when {
            applying.any { it.state == EnforcedState.Disabled } -> EnforcedState.Disabled
            applying.any { it.state == EnforcedState.Enabled }  -> EnforcedState.Enabled
            else                                               -> null
        }
    }

    fun isEnabled(type: PluginType): Boolean {
        if (pluginDescription.mainType == PluginType.CONSTRAINTS && type == PluginType.CONSTRAINTS) return true
        if (type == pluginDescription.mainType) return when (enforcedState()) {
            EnforcedState.Enabled  -> true
            EnforcedState.Disabled -> false
            null                   -> state == State.ENABLED
        }
        if (type == PluginType.CONSTRAINTS && pluginDescription.mainType == PluginType.PUMP && isEnabled(PluginType.PUMP)) return true
        return type == PluginType.CONSTRAINTS && pluginDescription.mainType == PluginType.APS && isEnabled(PluginType.APS)
    }

    fun hasComposeContent(): Boolean {
        return pluginDescription.composeContentProvider != null
    }

    /**
     * Whether this plugin exposes a preferences screen via [getPreferenceScreenContent].
     * Cached after the first call — the existence bit is stable for a plugin instance,
     * while [getPreferenceScreenContent] itself is still invoked on demand when the screen is rendered.
     * Override to `true` eagerly when [getPreferenceScreenContent] does a runtime lookup that might not be
     * resolved at first call (see SensitivityWeightedAveragePlugin).
     */
    open fun hasPreferences(): Boolean = hasPreferencesLazy
    private val hasPreferencesLazy: Boolean by lazy { getPreferenceScreenContent() != null }

    /**
     * Returns the compose content provider for this plugin's main UI.
     *
     * @return ComposablePluginContent instance or null. Typed as Any? to avoid Compose dependency in core:interfaces.
     *         Caller should cast to ComposablePluginContent from core:ui module.
     */
    fun getComposeContent(): Any? {
        return pluginDescription.composeContentProvider?.invoke(this)
    }

    fun isDefault() = pluginDescription.defaultPlugin

    /**
     * So far plugin can have it's main type + ConstraintInterface
     * ConstraintInterface is enabled if main plugin is enabled
     *
     * The enabled flag is set here and now, but [onStart] / [onStop] only get *scheduled*: they run
     * later on [pluginScope]. Returns that job, or null when nothing changed, so a caller that must
     * not carry on until the plugin has really started or stopped can wait for it - see
     * [setPluginEnabledAwaiting]. Callers that only flip the flag can keep ignoring the result.
     */
    open fun setPluginEnabled(type: PluginType, newState: Boolean): Job? {
        if (type == pluginDescription.mainType) {
            if (newState) { // enabling plugin
                if (state != State.ENABLED) {
                    onStateChange(type, state, State.ENABLED)
                    state = State.ENABLED
                    aapsLogger.debug(LTag.CORE, "Starting: $name")
                    return schedule(starting = true)
                }
            } else { // disabling plugin
                if (state == State.ENABLED) {
                    onStateChange(type, state, State.DISABLED)
                    state = State.DISABLED
                    aapsLogger.debug(LTag.CORE, "Stopping: $name")
                    return schedule(starting = false)
                }
            }
        }
        return null
    }

    /**
     * Queue one transition behind the previous one, so a stop can never overtake the start it is meant to
     * undo. `ConfigBuilderImpl.processOnEnabledCategoryChanged` disables the others and enables one in the
     * same pass, and nothing else orders them.
     *
     * The wait is bounded. A driver teardown that hangs - a BLE read with no suspension point - would
     * otherwise wedge this plugin's lifecycle for the rest of the process. After [TRANSITION_WAIT] the new
     * transition goes ahead anyway and says so in the log: overlapping is bad, never starting again is worse.
     */
    private fun schedule(starting: Boolean): Job = transitionLock.withLock {
        val previous = lastTransition
        val job = lifecycleScope.launch {
            if (previous != null && previous.isActive) {
                val settled = withTimeoutOrNull(TRANSITION_WAIT) { previous.join() }
                if (settled == null) aapsLogger.warn(LTag.CORE, "Previous transition of $name did not finish in $TRANSITION_WAIT, carrying on")
            }
            runPhase(starting)
        }
        lastTransition = job
        job
    }

    /**
     * Run [onStart] or [onStop] and survive it throwing.
     *
     * Before this a throw reached no handler at all and killed the process. Catching it alone would be
     * worse than the crash - the plugin would stay marked ENABLED, half built and silent - so the failure
     * is recorded in [lastStartFailed] and put in front of the user at the alarm tier.
     */
    private suspend fun runPhase(starting: Boolean) {
        try {
            if (starting) {
                onStart()
                // A clean start clears both the flag and this plugin's own card from the previous failure.
                if (lastStartFailed) {
                    lastStartFailed = false
                    startFailureCard?.let { notificationManager.dismiss(it) }
                    startFailureCard = null
                }
            } else {
                onStop()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            // The throwable goes to the log, never into the notification: it is developer text, it is not
            // translated, and it can be null or a page long.
            aapsLogger.error(LTag.CORE, "${if (starting) "onStart" else "onStop"} failed: $name", e)
            if (starting) {
                lastStartFailed = true
                // Replace this plugin's own previous card, so repeated failed starts do not stack up.
                startFailureCard?.let { notificationManager.dismiss(it) }
                startFailureCard = notificationManager.post(
                    NotificationId.PLUGIN_START_FAILED,
                    rh.gs(InterfacesStrings.plugin_start_failed, name),
                    validMinutes = 0,
                    sound = AlarmSound.ALARM
                )
            }
        }
    }

    /**
     * [setPluginEnabled], but returns only once [onStart] / [onStop] has actually finished.
     *
     * **Used by tests, not by production code, and that is correct rather than a gap.** It was written
     * for the settings import, on the reasoning that stopping and starting pump drivers is pointless if
     * the teardown is still queued when the caller lets commands flow again. The import does need that
     * guarantee - it just gets it one level up instead: `ConfigBuilderImpl.applyConfiguration` collects
     * the jobs from every `setPluginEnabled` it calls and waits for the whole set at once, bounded by
     * `PLUGIN_SETTLE_WAIT`. Waiting plugin-by-plugin here would serialise what that deliberately runs
     * in parallel.
     *
     * Kept because roughly twenty tests use it to await a transition deterministically; the alternative
     * is `setPluginEnabled(type, state)?.join()` written out at each of them.
     */
    suspend fun setPluginEnabledAwaiting(type: PluginType, newState: Boolean) {
        setPluginEnabled(type, newState)?.join()
    }

    /**
     * Version of setPluginEnabled used for testing only.
     * OnStart/OnStop is called directly.
     *
     * Deliberately NOT routed through [runPhase]: a test that makes `onStart` throw must see the throw,
     * not a swallowed one and a notification. ~30 unit tests and the pump emulator tests use this.
     */
    fun setPluginEnabledBlocking(type: PluginType, newState: Boolean) {
        if (type == pluginDescription.mainType) {
            if (newState) { // enabling plugin
                if (state != State.ENABLED) {
                    onStateChange(type, state, State.ENABLED)
                    state = State.ENABLED
                    aapsLogger.debug(LTag.CORE, "Starting: $name")
                    runBlocking { onStart() }
                }
            } else { // disabling plugin
                if (state == State.ENABLED) {
                    onStateChange(type, state, State.DISABLED)
                    state = State.DISABLED
                    runBlocking { onStop() }
                    aapsLogger.debug(LTag.CORE, "Stopping: $name")
                }
            }
        }
    }

    fun showInList(type: PluginType): Boolean {
        if (pluginDescription.mainType == type) return pluginDescription.showInList.invoke()
        return false
    }

    open suspend fun onStart() {}
    open suspend fun onStop() {}
    protected open fun onStateChange(type: PluginType?, oldState: State?, newState: State?) {}

    /**
     * Add compose preference screen content
     *
     * Plugin can override this to provide compose-based preference UI using PreferenceSubScreenDef.
     * This provides a declarative, type-safe way to define preference screens.
     *
     * @return PreferenceItem (typically PreferenceSubScreenDef) or null if not implemented
     */
    open fun getPreferenceScreenContent(): PreferenceItem? = null

    /**
     * Runtime permissions this plugin requires.
     * Override in subclasses to declare permissions.
     */
    open fun requiredPermissions(): List<PermissionGroup> = emptyList()

    companion object {

        /** How long a transition waits for the previous one. See [schedule]. */
        private val TRANSITION_WAIT = 30.seconds
    }
}