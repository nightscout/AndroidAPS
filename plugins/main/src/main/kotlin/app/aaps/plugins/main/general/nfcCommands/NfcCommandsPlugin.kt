package app.aaps.plugins.main.general.nfcCommands

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
import app.aaps.core.interfaces.configuration.ConfigBuilder
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.insulin.Insulin
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.plugin.PluginBaseWithPreferences
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileRepository
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.compose.icons.IcPluginNfc
import app.aaps.core.ui.compose.preference.PreferenceActionItem
import app.aaps.core.ui.compose.preference.PreferenceSubScreenDef
import app.aaps.plugins.main.R
import app.aaps.plugins.main.general.nfcCommands.actions.*
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import javax.inject.Singleton

sealed class NfcPrepareResult {
    data class Error(
        val message: String,
    ) : NfcPrepareResult()

    data class Ready(
        val tagUid: String,
        val tagName: String,
        val commands: List<String>,
    ) : NfcPrepareResult()
}

data class NfcExecutionResult(
    val success: Boolean,
    val message: String,
)

@Singleton
class NfcCommandsPlugin
    @Inject
    constructor(
        private val context: Context,
        aapsLogger: AAPSLogger,
        rh: ResourceHelper,
        preferences: Preferences,
        val nfcTagStore: NfcTagStore,
        val constraintChecker: ConstraintsChecker,
        val profileFunction: ProfileFunction,
        val profileUtil: ProfileUtil,
        val profileRepository: ProfileRepository,
        val insulin: Insulin,
        val activePlugin: ActivePlugin,
        val commandQueue: CommandQueue,
        val loop: Loop,
        val dateUtil: DateUtil,
        val persistenceLayer: PersistenceLayer,
        val decimalFormatter: DecimalFormatter,
        val configBuilder: ConfigBuilder,
        val rxBus: RxBus,
        val uel: UserEntryLogger,
    ) : PluginBaseWithPreferences(
            PluginDescription()
                .mainType(PluginType.GENERAL)
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
        var lastRemoteBolusTime: Long = 0
            private set

        private val actionStates = mutableMapOf<String, Any>()

        fun setActionState(key: String, state: Any) {
            actionStates[key] = state
        }

        fun getActionState(key: String): Any? = actionStates[key]

        fun clearActionStates() {
            actionStates.clear()
        }

        fun setLastRemoteBolusTime(time: Long) {
            lastRemoteBolusTime = time
        }

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

        fun updateLastScanned(tagUid: String) {
            nfcTagStore.updateLastScanned(tagUid)
        }

        fun prepareExecution(tagUid: String): NfcPrepareResult {
            clearActionStates()
            if (!isEnabled()) {
                return NfcPrepareResult.Error(rh.gs(R.string.nfccommands_plugin_disabled))
            }
            val tag = nfcTagStore.findTagByUid(tagUid)
            if (tag == null) {
                aapsLogger.debug(LTag.NFC, "No registered tag found for UID: $tagUid")
                return NfcPrepareResult.Error(rh.gs(R.string.nfccommands_tag_not_registered))
            }
            return NfcPrepareResult.Ready(
                tagUid = tagUid,
                tagName = tag.name,
                commands = tag.commands,
            )
        }

        suspend fun executeCascade(commands: List<String>, tagName: String? = null): NfcExecutionResult {
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

        suspend fun executeWithFeedback(commands: List<String>, tagName: String, action: String = "READ"): NfcExecutionResult {
            val result = executeCascade(commands, tagName)
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
                val effect =
                    if (success) {
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

        fun pumpBasalDurationStep(): Int =
            activePlugin.activePump
                .model()
                .tbrSettings()
                ?.durationStep ?: 60

        suspend fun executeCommand(command: String, tagName: String? = null): NfcExecutionResult {
            aapsLogger.debug(LTag.NFC, "Executing NFC command: $command")
            
            runCatching { JSONObject(command) }.onSuccess { json ->
                val codeString = json.optString("code")
                val code = runCatching { NfcCommandCode.valueOf(codeString) }.getOrNull()
                val params = json.optJSONObject("params") ?: JSONObject()
                if (code != null) {
                    return routeAction(code, params, tagName)
                }
            }

            return NfcExecutionResult(false, rh.gs(R.string.nfccommands_unknown_command))
        }

        private suspend fun routeAction(code: NfcCommandCode, params: JSONObject, tagName: String? = null): NfcExecutionResult {
            return requireRemoteCommands {
                when (code) {
                    NfcCommandCode.LOOP_STOP -> LoopStopAction(this).execute(params, tagName)
                    NfcCommandCode.LOOP_RESUME -> LoopResumeAction(this).execute(params, tagName)
                    NfcCommandCode.LOOP_SUSPEND -> LoopSuspendAction(this).execute(params, tagName)
                    NfcCommandCode.LOOP_LGS -> LoopLgsAction(this).execute(params, tagName)
                    NfcCommandCode.LOOP_CLOSED -> LoopClosedAction(this).execute(params, tagName)
                    NfcCommandCode.AAPSCLIENT_RESTART -> AapsClientRestartAction(this).execute(params, tagName)
                    NfcCommandCode.PUMP_CONNECT -> PumpConnectAction(this).execute(params, tagName)
                    NfcCommandCode.PUMP_DISCONNECT -> PumpDisconnectAction(this).execute(params, tagName)
                    NfcCommandCode.BASAL_STOP -> BasalCancelAction(this).execute(params, tagName)
                    NfcCommandCode.BASAL_ABS -> TempBasalAbsoluteAction(this).execute(params, tagName)
                    NfcCommandCode.BASAL_PCT -> TempBasalPercentAction(this).execute(params, tagName)
                    NfcCommandCode.BOLUS -> BolusAction(this).execute(params, tagName)
                    NfcCommandCode.EXTENDED_STOP -> ExtendedCancelAction(this).execute(params, tagName)
                    NfcCommandCode.EXTENDED_SET -> ExtendedSetAction(this).execute(params, tagName)
                    NfcCommandCode.PROFILE_SWITCH -> ProfileSwitchAction(this).execute(params, tagName)
                    NfcCommandCode.TARGET_MEAL -> TempTargetSetAction(this).execute(params.put("type", "MEAL"), tagName)
                    NfcCommandCode.TARGET_ACTIVITY -> TempTargetSetAction(this).execute(params.put("type", "ACTIVITY"), tagName)
                    NfcCommandCode.TARGET_HYPO -> TempTargetSetAction(this).execute(params.put("type", "HYPO"), tagName)
                    NfcCommandCode.TARGET_STOP -> TempTargetCancelAction(this).execute(params, tagName)
                    NfcCommandCode.CARBS -> CarbsAction(this).execute(params, tagName)
                    NfcCommandCode.RESTART -> RestartAction(this).execute(params, tagName)
                }
            }
        }

        private suspend fun requireRemoteCommands(block: suspend () -> NfcExecutionResult): NfcExecutionResult {
            val remoteAllowed = preferences.get(BooleanKey.NfcAllowRemoteCommands)
            if (!remoteAllowed) {
                return NfcExecutionResult(false, rh.gs(R.string.nfccommands_remote_command_not_allowed))
            }
            return block()
        }

        // NFC tags are written ahead of time and may be scanned on any supported pump.
        // Rather than rejecting a duration that is not an exact multiple of the pump's
        // step size, round it UP to the next valid multiple so the command always runs.
        fun roundUpToStep(
            value: Int,
            step: Int,
        ): Int = if (value % step == 0) value else ((value / step) + 1) * step

        fun processIntent(intent: Intent?): NfcPrepareResult {
            if (!isEnabled()) {
                return NfcPrepareResult.Error(rh.gs(R.string.nfccommands_plugin_disabled))
            }

            if (intent == null) return NfcPrepareResult.Error("")

            @Suppress("DEPRECATION")
            val nfcTag = intent.getParcelableExtra<Tag>(NfcAdapter.EXTRA_TAG)
            if (nfcTag == null) {
                aapsLogger.debug(LTag.NFC, "Rejected intent without physical NFC tag")
                return NfcPrepareResult.Error("")
            }

            return when (intent.action) {
                NfcAdapter.ACTION_NDEF_DISCOVERED -> processNdefIntent(intent, nfcTag)
                NfcAdapter.ACTION_TECH_DISCOVERED, NfcAdapter.ACTION_TAG_DISCOVERED -> processTagIntent(nfcTag)
                else -> NfcPrepareResult.Error("")
            }
        }

        private fun processNdefIntent(intent: Intent, nfcTag: Tag): NfcPrepareResult {
            @Suppress("DEPRECATION")
            val rawMsgs = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)
            if (rawMsgs.isNullOrEmpty()) {
                return NfcPrepareResult.Error("")
            }
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
                // Silently ignore if not found in My Tags screen
                aapsLogger.debug(LTag.NFC, "Tag not registered: $tagUid")
            }
            return prep
        }
    }
