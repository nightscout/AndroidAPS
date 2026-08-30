package app.aaps.plugins.sync.nfcCommands

import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.bolus.WizardBolusExecutor
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.iob.GlucoseStatusProvider
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileRepository
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.pump.BolusProgressData
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.scenes.SceneAutomationApi
import app.aaps.core.interfaces.scenes.SceneIconResolver
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.plugins.sync.nfcCommands.actions.BasalCancelAction
import app.aaps.plugins.sync.nfcCommands.actions.BolusAction
import app.aaps.plugins.sync.nfcCommands.actions.BolusWizardAction
import app.aaps.plugins.sync.nfcCommands.actions.CarbsAction
import app.aaps.plugins.sync.nfcCommands.actions.ExtendedCancelAction
import app.aaps.plugins.sync.nfcCommands.actions.ExtendedSetAction
import app.aaps.plugins.sync.nfcCommands.actions.LoopClosedAction
import app.aaps.plugins.sync.nfcCommands.actions.LoopLgsAction
import app.aaps.plugins.sync.nfcCommands.actions.LoopResumeAction
import app.aaps.plugins.sync.nfcCommands.actions.LoopStopAction
import app.aaps.plugins.sync.nfcCommands.actions.LoopSuspendAction
import app.aaps.plugins.sync.nfcCommands.actions.NfcAction
import app.aaps.plugins.sync.nfcCommands.actions.ProfileSwitchAction
import app.aaps.plugins.sync.nfcCommands.actions.PumpConnectAction
import app.aaps.plugins.sync.nfcCommands.actions.PumpDisconnectAction
import app.aaps.plugins.sync.nfcCommands.actions.RunSceneAction
import app.aaps.plugins.sync.nfcCommands.actions.TempBasalAbsoluteAction
import app.aaps.plugins.sync.nfcCommands.actions.TempBasalPercentAction
import app.aaps.plugins.sync.nfcCommands.actions.TempTargetActivityAction
import app.aaps.plugins.sync.nfcCommands.actions.TempTargetCancelAction
import app.aaps.plugins.sync.nfcCommands.actions.TempTargetHypoAction
import app.aaps.plugins.sync.nfcCommands.actions.TempTargetManualAction
import app.aaps.plugins.sync.nfcCommands.actions.TempTargetMealAction
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.SingleIn
import javax.inject.Inject

/**
 * Builds an [NfcAction] for a [NfcCommandCode].
 *
 * A tag stores its commands as JSON naming a command code, so actions cannot be built by the DI graph
 * - the set of them is only known when a tag is read. They used to be handed the whole
 * [NfcCommandsPlugin] and reach through it for whatever they needed, which coupled every action to the
 * plugin and hid its real dependencies behind 261 `plugin.x` accesses.
 *
 * This holds the dependencies instead and passes each action exactly what it asks for. Same shape as
 * `plugins/automation/.../actions/ActionFactory.kt`.
 *
 * Written with Dagger because the rest of `:plugins:sync` still is. It converts to Metro with the
 * module - see section 1a of `_docs/NFC_KMP_MIGRATION.md`.
 */
@SingleIn(AppScope::class)
class NfcActionFactory @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val activePlugin: ActivePlugin,
    private val bolusProgressData: BolusProgressData,
    private val commandQueue: CommandQueue,
    private val constraintChecker: ConstraintsChecker,
    private val dateUtil: DateUtil,
    private val decimalFormatter: DecimalFormatter,
    private val glucoseStatusProvider: GlucoseStatusProvider,
    private val loop: Loop,
    private val persistenceLayer: PersistenceLayer,
    private val preferences: Preferences,
    private val profileFunction: ProfileFunction,
    private val profileRepository: ProfileRepository,
    private val profileUtil: ProfileUtil,
    private val rh: TextResolver,
    private val runtimeState: NfcRuntimeState,
    private val sceneAutomationApi: SceneAutomationApi,
    private val sceneIconResolver: SceneIconResolver,
    private val uel: UserEntryLogger,
    private val wizardBolusExecutor: WizardBolusExecutor
) {

    /** The action for [code], with its parameters unset. */
    fun create(code: NfcCommandCode): NfcAction = when (code) {
        NfcCommandCode.LOOP_STOP       -> LoopStopAction(aapsLogger, rh, uel, loop, profileFunction)
        NfcCommandCode.LOOP_RESUME     -> LoopResumeAction(aapsLogger, rh, uel, loop, profileFunction)
        NfcCommandCode.LOOP_SUSPEND    -> LoopSuspendAction(aapsLogger, rh, uel, loop, profileFunction)
        NfcCommandCode.LOOP_CLOSED     -> LoopClosedAction(aapsLogger, rh, uel, loop, profileFunction)
        NfcCommandCode.LOOP_LGS        -> LoopLgsAction(aapsLogger, rh, uel, loop, profileFunction)
        NfcCommandCode.PUMP_CONNECT    -> PumpConnectAction(aapsLogger, rh, uel, loop, profileFunction)
        NfcCommandCode.PUMP_DISCONNECT -> PumpDisconnectAction(aapsLogger, rh, uel, loop, profileFunction)
        NfcCommandCode.BASAL_STOP      -> BasalCancelAction(aapsLogger, rh, uel, commandQueue)
        NfcCommandCode.BASAL_ABS       -> TempBasalAbsoluteAction(aapsLogger, rh, uel, activePlugin, commandQueue, constraintChecker, profileFunction)
        NfcCommandCode.BASAL_PCT       -> TempBasalPercentAction(aapsLogger, rh, uel, activePlugin, commandQueue, constraintChecker, profileFunction)
        NfcCommandCode.BOLUS           -> BolusAction(aapsLogger, rh, uel, bolusProgressData, commandQueue, constraintChecker, dateUtil, loop, persistenceLayer, preferences, profileFunction, profileUtil, runtimeState)
        NfcCommandCode.CARBS           -> CarbsAction(aapsLogger, rh, uel, commandQueue, constraintChecker, dateUtil)
        NfcCommandCode.BOLUS_WIZARD    -> BolusWizardAction(aapsLogger, rh, uel, commandQueue, dateUtil, glucoseStatusProvider, loop, persistenceLayer, preferences, profileUtil, runtimeState, wizardBolusExecutor)
        NfcCommandCode.EXTENDED_STOP   -> ExtendedCancelAction(aapsLogger, rh, uel, commandQueue)
        NfcCommandCode.EXTENDED_SET    -> ExtendedSetAction(aapsLogger, rh, uel, commandQueue, constraintChecker, decimalFormatter)
        NfcCommandCode.PROFILE_SWITCH  -> ProfileSwitchAction(aapsLogger, rh, uel, dateUtil, profileFunction, profileRepository)
        NfcCommandCode.RUN_SCENE       -> RunSceneAction(aapsLogger, rh, uel, sceneAutomationApi, sceneIconResolver)
        NfcCommandCode.TARGET_MEAL     -> TempTargetMealAction(aapsLogger, rh, uel, dateUtil, decimalFormatter, persistenceLayer, preferences, profileUtil)
        NfcCommandCode.TARGET_ACTIVITY -> TempTargetActivityAction(aapsLogger, rh, uel, dateUtil, decimalFormatter, persistenceLayer, preferences, profileUtil)
        NfcCommandCode.TARGET_HYPO     -> TempTargetHypoAction(aapsLogger, rh, uel, dateUtil, decimalFormatter, persistenceLayer, preferences, profileUtil)
        NfcCommandCode.TARGET_MANUAL   -> TempTargetManualAction(aapsLogger, rh, uel, dateUtil, decimalFormatter, persistenceLayer, profileFunction, profileUtil)
        NfcCommandCode.TARGET_STOP     -> TempTargetCancelAction(aapsLogger, rh, uel, dateUtil, persistenceLayer)
    }
}
