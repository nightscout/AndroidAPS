package app.aaps.plugins.aps.utils

import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.db.ProcessedTbrEbData
import app.aaps.core.interfaces.di.MetroMemberInjector
import app.aaps.core.interfaces.insulin.ConcentrationHelper
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.di.metro.AlgTestGraph
import app.aaps.di.metro.AlgTestLeaves
import dev.zacsweers.metro.MembersInjector
import dev.zacsweers.metro.createGraphFactory
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fills the fields of the reference algorithm helpers, which are built with `new` rather than by a graph.
 *
 * Hilt injects the objects below, this hands them to [AlgTestGraph], and the graph produces the member
 * injectors. Nothing here is constructed by that graph - see [AlgTestLeaves].
 */
@Singleton
class StaticInjector @Inject constructor(
    aapsLogger: AAPSLogger,
    constraintChecker: ConstraintsChecker,
    preferences: Preferences,
    activePlugin: ActivePlugin,
    processedTbrEbData: ProcessedTbrEbData,
    profileFunction: ProfileFunction,
    rh: ResourceHelper,
    decimalFormatter: DecimalFormatter,
    ch: ConcentrationHelper,
    dateUtil: DateUtil,
    profileUtil: ProfileUtil
) : MetroMemberInjector {

    private val graph = createGraphFactory<AlgTestGraph.Factory>().create(
        AlgTestLeaves(
            aapsLogger, constraintChecker, preferences, activePlugin, processedTbrEbData, profileFunction,
            rh, decimalFormatter, ch, dateUtil, profileUtil
        )
    )

    companion object {

        private var instance: StaticInjector? = null

        /**
         * For [app.aaps.plugins.aps.logger.LoggerCallback] only, which the JS engine instantiates - there
         * is no way to hand it anything.
         */
        @Deprecated("Use only for classes instantiated by 3rd party")
        fun getInstance(): StaticInjector =
            instance ?: throw IllegalStateException("StaticInjector not initialized")
    }

    init {
        instance = this
    }

    /**
     * Throws rather than returning false for a class with no entry.
     *
     * The callers here fill their fields from their own `init` and ignore the result, so returning false
     * would leave a `lateinit` unset and surface later as an unrelated failure - inside tests that check
     * algorithm output against recorded results, where a quietly wrong value is the worst outcome. A
     * missing entry is a mistake in [app.aaps.di.metro.AlgMemberInjectors], so say which class it was.
     */
    override fun injectMembers(target: Any): Boolean {
        val injector = graph.memberInjectors[target::class]
            ?: error("No member injector for ${target::class.java.name}. Add an entry to AlgMemberInjectors.")
        @Suppress("UNCHECKED_CAST")
        (injector as MembersInjector<Any>).injectMembers(target)
        return true
    }
}
