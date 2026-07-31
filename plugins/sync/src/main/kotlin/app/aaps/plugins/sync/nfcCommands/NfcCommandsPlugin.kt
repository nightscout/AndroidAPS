package app.aaps.plugins.sync.nfcCommands

import android.content.Context
import android.content.Intent
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.VibratorManager
import android.widget.Toast
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
import app.aaps.core.interfaces.plugin.PluginBaseWithPreferences
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileRepository
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.scenes.SceneAutomationApi
import app.aaps.core.interfaces.scenes.SceneIconResolver
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.compose.icons.IcPluginNfc
import app.aaps.core.ui.compose.preference.PreferenceActionItem
import app.aaps.core.ui.compose.preference.PreferenceSubScreenDef
import app.aaps.plugins.sync.R
import app.aaps.plugins.sync.nfcCommands.actions.*
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import javax.inject.Singleton

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
@Singleton
class NfcCommandsPlugin @Inject constructor(
    private val context: Context,
    aapsLogger: AAPSLogger,
    rh: ResourceHelper,
    preferences: Preferences,
    val nfcTagStore: NfcTagStore,
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
        .pluginName(R.string.nfccommands)
        .shortName(R.string.nfccommands_shortname)
        .description(R.string.description_nfc_communicator),
    ownPreferences = emptyList(),
    aapsLogger,
    rh,
    preferences,
) {
    /** Cooldown tracker for remote boluses to prevent double-scanning issues. */
    var lastRemoteBolusTime: Long = 0
        private set

    /** Temporary state storage for actions that need to pass data between phases (e.g. Wizard calculation to execution). */
    private val actionStates = mutableMapOf<String, Any>()

    fun setActionState(key: String, state: Any) { actionStates[key] = state }
    fun getActionState(key: String): Any? = actionStates[key]
    fun clearActionStates() { actionStates.clear() }

    fun setLastRemoteBolusTime(time: Long) { lastRemoteBolusTime = time }

    override fun getPreferenceScreenContent() = PreferenceSubScreenDef(
        key = "nfccommunicator_settings",
        titleResId = R.string.nfccommands,
        items = listOf(
            BooleanKey.NfcAllowRemoteCommands,
            BooleanKey.NfcForegroundPriority,
            PreferenceActionItem(
                key = "nfccommunicator_clear_log",
                titleResId = R.string.nfccommands_clear_log,
                summaryResId = R.string.nfccommands_clear_log_summary,
                onAction = {
                    nfcTagStore.clearLog()
                    showToast(rh.gs(R.string.nfccommands_log_cleared))
                },
            ),
        ),
        icon = pluginDescription.icon,
    )

    fun updateLastScanned(tagUid: String) { nfcTagStore.updateLastScanned(tagUid) }

    /**
     * Prepares a tag for execution by checking plugin status and registration.
     */
    fun prepareExecution(tagUid: String): NfcPrepareResult {
        clearActionStates()
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
    suspend fun executeCascade(commands: List<String>): NfcExecutionResult {
        val results = mutableListOf<NfcExecutionResult>()
        for (command in commands) {
            val result = executeCommand(command)
            results += result
            if (!result.success) break
        }
        val allSuccess = results.all { it.success }
        val message = results.joinToString("\n") { it.message }
        return NfcExecutionResult(success = allSuccess, message = message)
    }

    /**
     * Executes commands and provides physical (vibration) and visual (toast/log) feedback.
     */
    suspend fun executeWithFeedback(commands: List<String>, tagName: String, action: String = "READ"): NfcExecutionResult {
        val result = executeCascade(commands)
        clearActionStates()
        nfcTagStore.appendLogEntry(
            NfcLogEntry(
                timestamp = System.currentTimeMillis(),
                tagName = tagName,
                action = action,
                success = result.success,
                message = result.message,
            ),
        )
        vibrate(result.success)
        showToast(result.message)
        return result
    }

    private fun vibrate(success: Boolean) {
        runCatching {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager ?: return
            val effect = if (success) {
                VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE)
            } else {
                VibrationEffect.createWaveform(longArrayOf(0, 150, 100, 150), -1)
            }
            vm.defaultVibrator.vibrate(effect)
        }
    }

    private fun showToast(message: String) {
        runCatching {
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            }
        }
    }

    /** Returns the pump's temporary basal duration step in minutes. */
    fun pumpBasalDurationStep(): Int =
        activePlugin.activePump.model().tbrSettings()?.durationStep ?: 60

    /**
     * Parses and executes a single serialized command string.
     */
    suspend fun executeCommand(command: String): NfcExecutionResult {
        aapsLogger.debug(LTag.NFC, "Executing NFC command: $command")

        runCatching { JSONObject(command) }.onSuccess { json ->
            val codeString = json.optString(NfcJsonKeys.CODE)
            val code = runCatching { NfcCommandCode.valueOf(codeString) }.getOrNull()
            val params = json.optJSONObject(NfcJsonKeys.PARAMS) ?: JSONObject()
            if (code != null) {
                return routeAction(code, params)
            }
        }

        return NfcExecutionResult(false, rh.gs(R.string.nfccommands_unknown_command))
    }

    fun getAction(code: NfcCommandCode): NfcAction = code.createAction(this)

    private suspend fun routeAction(code: NfcCommandCode, params: JSONObject): NfcExecutionResult {
        return requireRemoteCommands {
            val action = getAction(code)
            action.params = params
            action.execute()
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
     * Rounds a duration value UP to the next valid pump step multiple.
     * Ensures compatibility when a tag written for one pump is used on another.
     */
    fun roundUpToStep(value: Int, step: Int): Int =
        if (value % step == 0) value else ((value / step) + 1) * step

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
