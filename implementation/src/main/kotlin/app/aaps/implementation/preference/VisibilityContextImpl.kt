package app.aaps.implementation.preference

import app.aaps.core.data.model.GV
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.db.observeChanges
import app.aaps.core.interfaces.di.ApplicationScope
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.rx.collectResilient
import app.aaps.core.interfaces.sync.NsClient
import app.aaps.core.keys.interfaces.VisibilityContext
import app.aaps.core.keys.interfaces.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of [VisibilityContext] that provides runtime context
 * for evaluating preference visibility conditions.
 *
 * This class bridges the gap between the preference key definitions (which declare
 * visibility conditions) and the runtime state of the app (pump type, BG source, etc.).
 */
@Singleton
class VisibilityContextImpl @Inject constructor(
    private val activePlugin: ActivePlugin,
    private val persistenceLayer: PersistenceLayer,
    private val constraintsChecker: ConstraintsChecker,
    private val config: Config,
    private val aapsLogger: AAPSLogger,
    private val nsClient: NsClient,
    @ApplicationScope private val appScope: CoroutineScope,
    override val preferences: Preferences
) : VisibilityContext {

    override val isClient: Boolean
        get() = config.AAPSCLIENT

    // Live snapshot of the stable Client-Control pairing signal (always true on a master). Read per
    // isVisible() evaluation so CLIENT_PAIRED-gated elements appear/disappear the moment pairing flips.
    override val masterOrPairedClient: Boolean
        get() = nsClient.masterOrPairedClientFlow.value

    // Cached off-main: advancedFilteringSupported is read from Compose preference-screen visibility
    // predicates on the main thread, so it must not hit the DB synchronously. Seeded in init and
    // refreshed whenever glucose values change (the value derives from the latest GV's sensor).
    // Until the first async load completes it reports false (advanced filtering not supported).
    @Volatile private var cachedAdvancedFiltering: Boolean = false

    init {
        appScope.launch { cachedAdvancedFiltering = persistenceLayer.isAdvancedFilteringSupported() }
        observeAdvancedFiltering()
    }

    // Debounced so a Nightscout backfill burst (hundreds of GV inserts) collapses into a single
    // recompute — the value only depends on the latest glucose value's sensor and changes rarely.
    @OptIn(FlowPreview::class)
    private fun observeAdvancedFiltering() {
        persistenceLayer.observeChanges<GV>()
            .debounce(1000L)
            .collectResilient(appScope, aapsLogger, LTag.CORE) {
                cachedAdvancedFiltering = persistenceLayer.isAdvancedFilteringSupported()
            }
    }

    override val isPatchPump: Boolean
        get() = activePlugin.activePump.pumpDescription.isPatchPump

    override val isBatteryReplaceable: Boolean
        get() = activePlugin.activePump.pumpDescription.isBatteryReplaceable

    override val isBatteryChangeLoggingEnabled: Boolean
        get() = activePlugin.activePump.isBatteryChangeLoggingEnabled()

    override val advancedFilteringSupported: Boolean
        get() = cachedAdvancedFiltering

    override val isPumpInitialized: Boolean
        get() = activePlugin.activePump.isInitialized()

    override val isConcentrationEnabled: Boolean
        get() = constraintsChecker.isConcentrationEnabled().value()
}
