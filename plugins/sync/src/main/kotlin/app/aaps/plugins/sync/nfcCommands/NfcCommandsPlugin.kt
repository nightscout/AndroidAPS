package app.aaps.plugins.sync.nfcCommands

import android.content.Intent
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.bolus.WizardBolusExecutor
import app.aaps.core.interfaces.configuration.ConfigBuilder
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.iob.GlucoseStatusProvider
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.pump.BolusProgressData
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.di.NotNSClient
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.plugin.PluginBaseWithPreferences
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileRepository
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventShowSnackbar
import app.aaps.core.interfaces.scenes.SceneAutomationApi
import app.aaps.core.interfaces.scenes.SceneIconResolver
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.keys.interfaces.withClick
import app.aaps.core.ui.compose.icons.IcPluginNfc
import app.aaps.core.ui.compose.preference.PreferenceSubScreenDef
import app.aaps.plugins.sync.R
import app.aaps.plugins.sync.nfcCommands.actions.NfcAction
import app.aaps.plugins.sync.nfcCommands.actions.pumpBasalDurationStep
import app.aaps.plugins.sync.nfcCommands.compose.NfcCommandsComposeContent
import app.aaps.plugins.sync.nfcCommands.keys.NfcIntentKey
import java.nio.charset.StandardCharsets
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.IntKey
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import javax.inject.Inject

/**
 * Result of the pre-execution phase when an NFC tag is detected.
 */
sealed class NfcPrepareResult {
    data class Error(val message: String) : NfcPrepareResult()
    data class Ready(
        val tagUid: String,
        val tagName: String,
        val commands: List<String>,
    ) : NfcPrepareResult()
}

/**
 * Result of a single NFC action execution.
 */
data class NfcExecutionResult(
    val success: Boolean,
    val message: String,
)

/**
 * Main plugin class for NFC Command execution.
 * Handles the lifecycle of NFC tag scanning, command routing, and feedback.
 */
@ContributesIntoMap(AppScope::class, binding = binding<PluginBase>())
@NotNSClient
@IntKey(380)
@SingleIn(AppScope::class)
class NfcCommandsPlugin @Inject constructor(
    aapsLogger: AAPSLogger,
    // Narrows PluginBase.rh, which is a TextResolver and so only takes TextRef. This module still owns
    // AAPT resources, so it needs the resource id overloads. Same as SmsCommunicatorPlugin.
    override val rh: ResourceHelper,
    preferences: Preferences,
    val nfcTagStore: NfcTagStore,
    val actionFactory: NfcActionFactory,
    val runtimeState: NfcRuntimeState,
    val constraintChecker: ConstraintsChecker,
    val profileFunction: ProfileFunction,
    val profileUtil: ProfileUtil,
    val profileRepository: ProfileRepository,
    val activePlugin: ActivePlugin,
    val commandQueue: CommandQueue,
    val loop: Loop,
    val dateUtil: DateUtil,
    val persistenceLayer: PersistenceLayer,
    val decimalFormatter: DecimalFormatter,
    val configBuilder: ConfigBuilder,
    val rxBus: RxBus,
    val uel: UserEntryLogger,
    val wizardBolusExecutor: WizardBolusExecutor,
    val iobCobCalculator: IobCobCalculator,
    val bolusProgressData: BolusProgressData,
    val glucoseStatusProvider: GlucoseStatusProvider,
    val sceneAutomationApi: SceneAutomationApi,
    val sceneIconResolver: SceneIconResolver,
) : PluginBaseWithPreferences(
    PluginDescription()
        .mainType(PluginType.SYNC)
        .icon(IcPluginNfc)
        .composeContent { NfcCommandsComposeContent(it as NfcCommandsPlugin) }
        .pluginName(TextRef.AndroidRes(R.string.nfccommands))
        .shortName(TextRef.AndroidRes(R.string.nfccommands_shortname))
        .description(TextRef.AndroidRes(R.string.description_nfc_communicator)),
    ownPreferences = emptyList(),
    aapsLogger,
    rh,
    preferences,
) {
    override fun getPreferenceScreenContent() = PreferenceSubScreenDef(
        key = "nfccommunicator_settings",
        titleResId = R.string.nfccommands,
        items = listOf(
            BooleanKey.NfcAllowRemoteCommands,
            BooleanKey.NfcForegroundPriority,
            NfcIntentKey.ClearLog.withClick {
                nfcTagStore.clearLog()
                showMessage(rh.gs(R.string.nfccommands_log_cleared), EventShowSnackbar.Type.Success)
            },
        ),
        icon = pluginDescription.icon,
    )

    fun updateLastScanned(tagUid: String) { nfcTagStore.updateLastScanned(tagUid) }

    /**
     * Prepares a tag for execution by checking plugin status and registration.
     */
    fun prepareExecution(tagUid: String): NfcPrepareResult {
        runtimeState.clearWizardPreviews()
        if (!isEnabled()) return NfcPrepareResult.Error(rh.gs(R.string.nfccommands_plugin_disabled))
        
        val tag = nfcTagStore.findTagByUid(tagUid)
        if (tag == null) {
            aapsLogger.debug(LTag.NFC, "No registered tag found for UID: $tagUid")
            return NfcPrepareResult.Error(rh.gs(R.string.nfccommands_tag_not_registered))
        }
        return NfcPrepareResult.Ready(tagUid = tagUid, tagName = tag.name, commands = tag.commands)
    }

    /**
     * Executes a list of serialized command strings sequentially.
     */
    suspend fun executeCascade(commands: List<String>, tagName: String = ""): NfcExecutionResult {
        val results = mutableListOf<NfcExecutionResult>()
        for (command in commands) {
            val result = executeCommand(command, tagName)
            results += result
            if (!result.success) break
        }
        val allSuccess = results.all { it.success }
        val message = results.joinToString("\n") { it.message }
        return NfcExecutionResult(success = allSuccess, message = message)
    }

    /**
     * Executes commands, records the result in the NFC log, and asks for it to be shown on screen.
     *
     * The message goes out as an [EventShowSnackbar] rather than a Toast. The event needs no [android.content.Context]
     * and no reference to a host, it is styled by success or failure, and when no screen is up an
     * application-scoped collector turns it into a system notification instead of losing it - which a
     * Toast posted from here could not do.
     *
     * The vibration is not done here. It needs a Context, so the screens do it with
     * [vibrateForNfcResult] on the result this returns.
     */
    suspend fun executeWithFeedback(commands: List<String>, tagName: String, action: String = "READ"): NfcExecutionResult {
        val result = executeCascade(commands, tagName)
        runtimeState.clearWizardPreviews()
        nfcTagStore.appendLogEntry(
            NfcLogEntry(
                timestamp = System.currentTimeMillis(),
                tagName = tagName,
                action = action,
                success = result.success,
                message = result.message,
            ),
        )
        showMessage(
            result.message,
            if (result.success) EventShowSnackbar.Type.Success else EventShowSnackbar.Type.Error
        )
        return result
    }

    /** Asks whatever screen is up to show [message]. Safe to call from any thread. */
    internal fun showMessage(message: String, type: EventShowSnackbar.Type = EventShowSnackbar.Type.Info) {
        rxBus.send(EventShowSnackbar(message, type))
    }

    /** Returns the pump's temporary basal duration step in minutes. Used by the build screen. */
    fun pumpBasalDurationStep(): Int = pumpBasalDurationStep(activePlugin)

    /**
     * Parses and executes a single serialized command string.
     */
    suspend fun executeCommand(command: String, tagName: String = ""): NfcExecutionResult {
        aapsLogger.debug(LTag.NFC, "Executing NFC command: $command")
        val decoded = NfcCommand.decode(command)
            ?: return NfcExecutionResult(false, rh.gs(R.string.nfccommands_unknown_command))
        return routeAction(decoded.code, decoded.params, tagName)
    }

    fun getAction(code: NfcCommandCode): NfcAction = actionFactory.create(code)

    private suspend fun routeAction(code: NfcCommandCode, params: NfcParams, tagName: String): NfcExecutionResult {
        return requireRemoteCommands {
            val action = getAction(code)
            action.params = params
            action.executeIfComplete(tagName)
        }
    }

    private suspend fun requireRemoteCommands(block: suspend () -> NfcExecutionResult): NfcExecutionResult {
        val remoteAllowed = preferences.get(BooleanKey.NfcAllowRemoteCommands)
        if (!remoteAllowed) {
            return NfcExecutionResult(false, rh.gs(R.string.nfccommands_remote_command_not_allowed))
        }
        return block()
    }

    /**
     * Entry point for processing Android NFC Intents.
     */
    fun processIntent(intent: Intent?): NfcPrepareResult {
        if (!isEnabled()) return NfcPrepareResult.Error(rh.gs(R.string.nfccommands_plugin_disabled))
        if (intent == null) return NfcPrepareResult.Error("")

        @Suppress("DEPRECATION")
        val nfcTag = intent.getParcelableExtra<Tag>(NfcAdapter.EXTRA_TAG)
        if (nfcTag == null) {
            aapsLogger.debug(LTag.NFC, "Rejected intent without physical NFC tag")
            return NfcPrepareResult.Error("")
        }

        @Suppress("DEPRECATION")
        return when (intent.action) {
            NfcAdapter.ACTION_NDEF_DISCOVERED -> processNdefIntent(intent, nfcTag)
            NfcAdapter.ACTION_TECH_DISCOVERED, NfcAdapter.ACTION_TAG_DISCOVERED -> processTagIntent(nfcTag)
            else -> NfcPrepareResult.Error("")
        }
    }

    private fun processNdefIntent(intent: Intent, nfcTag: Tag): NfcPrepareResult {
        @Suppress("DEPRECATION")
        val rawMsgs = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)
        if (rawMsgs.isNullOrEmpty()) return NfcPrepareResult.Error("")
        
        val message = rawMsgs[0] as? NdefMessage ?: return NfcPrepareResult.Error("")
        val record = message.records?.firstOrNull() ?: return NfcPrepareResult.Error("")

        if (record.tnf != NdefRecord.TNF_MIME_MEDIA ||
            String(record.type, StandardCharsets.US_ASCII) != NfcTagStore.MIME_TYPE
        ) {
            aapsLogger.debug(LTag.NFC, "Rejected NFC record with unexpected TNF/type")
            return NfcPrepareResult.Error("")
        }

        val tagUid = NfcTagStore.tagUidHex(nfcTag.id) ?: return NfcPrepareResult.Error("")
        return prepareExecutionByUid(tagUid)
    }

    private fun processTagIntent(nfcTag: Tag): NfcPrepareResult {
        val tagUid = NfcTagStore.tagUidHex(nfcTag.id) ?: return NfcPrepareResult.Error("")
        aapsLogger.debug(LTag.NFC, "TAG_DISCOVERED fallback, UID: $tagUid")
        return prepareExecutionByUid(tagUid)
    }

    private fun prepareExecutionByUid(tagUid: String): NfcPrepareResult {
        if (nfcTagStore.isJustWritten(tagUid)) return NfcPrepareResult.Error("")

        val prep = prepareExecution(tagUid)
        if (prep is NfcPrepareResult.Error) {
            aapsLogger.debug(LTag.NFC, "Tag not registered: $tagUid")
        }
        return prep
    }
}
