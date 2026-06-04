package app.aaps.plugins.main.general.nfcCommands

import android.content.Context
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
import java.util.Locale
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

        suspend fun executeWithFeedback(commands: List<String>, tagName: String, action: String = "READ"): NfcExecutionResult {
            val result = executeCascade(commands)
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

        suspend fun executeCommand(command: String): NfcExecutionResult {
            aapsLogger.debug(LTag.NFC, "Executing NFC command: $command")
            
            // Try structured JSON format first
            runCatching { JSONObject(command) }.onSuccess { json ->
                val codeString = json.optString("code")
                val code = runCatching { NfcCommandCode.valueOf(codeString) }.getOrNull()
                val params = json.optJSONObject("params") ?: JSONObject()
                if (code != null) {
                    return routeAction(code, params)
                }
            }

            // Fallback to legacy string format
            return executeLegacyCommand(command)
        }

        private suspend fun routeAction(code: NfcCommandCode, params: JSONObject): NfcExecutionResult {
            return requireRemoteCommands {
                when (code) {
                    NfcCommandCode.LOOP_STOP -> LoopStopAction(this).execute(params)
                    NfcCommandCode.LOOP_RESUME -> LoopResumeAction(this).execute(params)
                    NfcCommandCode.LOOP_SUSPEND -> LoopSuspendAction(this).execute(params)
                    NfcCommandCode.LOOP_LGS -> LoopLgsAction(this).execute(params)
                    NfcCommandCode.LOOP_CLOSED -> LoopClosedAction(this).execute(params)
                    NfcCommandCode.AAPSCLIENT_RESTART -> AapsClientRestartAction(this).execute(params)
                    NfcCommandCode.PUMP_CONNECT -> PumpConnectAction(this).execute(params)
                    NfcCommandCode.PUMP_DISCONNECT -> PumpDisconnectAction(this).execute(params)
                    NfcCommandCode.BASAL_STOP -> BasalCancelAction(this).execute(params)
                    NfcCommandCode.BASAL_ABS -> TempBasalAbsoluteAction(this).execute(params)
                    NfcCommandCode.BASAL_PCT -> TempBasalPercentAction(this).execute(params)
                    NfcCommandCode.BOLUS -> BolusAction(this).execute(params)
                    NfcCommandCode.EXTENDED_STOP -> ExtendedCancelAction(this).execute(params)
                    NfcCommandCode.EXTENDED_SET -> ExtendedSetAction(this).execute(params)
                    NfcCommandCode.PROFILE_SWITCH -> ProfileSwitchAction(this).execute(params)
                    NfcCommandCode.TARGET_MEAL -> TempTargetSetAction(this).execute(params.put("type", "MEAL"))
                    NfcCommandCode.TARGET_ACTIVITY -> TempTargetSetAction(this).execute(params.put("type", "ACTIVITY"))
                    NfcCommandCode.TARGET_HYPO -> TempTargetSetAction(this).execute(params.put("type", "HYPO"))
                    NfcCommandCode.TARGET_STOP -> TempTargetCancelAction(this).execute(params)
                    NfcCommandCode.CARBS -> CarbsAction(this).execute(params)
                    NfcCommandCode.RESTART -> RestartAction(this).execute(params)
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

        private suspend fun executeLegacyCommand(command: String): NfcExecutionResult {
            val divided = command.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
            if (divided.isEmpty()) {
                return NfcExecutionResult(false, rh.gs(R.string.wrong_format))
            }
            val codeString = divided[0].uppercase(Locale.ROOT)
            
            // Re-route legacy strings to modern actions by constructing temporary JSON params
            return when (codeString) {
                "LOOP" -> processLegacyLoop(divided)
                "AAPSCLIENT" -> routeAction(NfcCommandCode.AAPSCLIENT_RESTART, JSONObject())
                "PUMP" -> processLegacyPump(divided)
                "PROFILE" -> processLegacyProfile(divided)
                "BASAL" -> processLegacyBasal(divided)
                "EXTENDED" -> processLegacyExtended(divided)
                "BOLUS" -> processLegacyBolus(divided)
                "CARBS" -> processLegacyCarbs(divided)
                "TARGET" -> processLegacyTarget(divided)
                "RESTART" -> routeAction(NfcCommandCode.RESTART, JSONObject())
                else -> NfcExecutionResult(false, rh.gs(R.string.nfccommands_unknown_command))
            }
        }

        private suspend fun processLegacyLoop(divided: List<String>): NfcExecutionResult {
            if (divided.size < 2) return NfcExecutionResult(false, rh.gs(R.string.wrong_format))
            return when (divided[1].uppercase(Locale.ROOT)) {
                "DISABLE", "STOP" -> routeAction(NfcCommandCode.LOOP_STOP, JSONObject())
                "RESUME" -> routeAction(NfcCommandCode.LOOP_RESUME, JSONObject())
                "SUSPEND" -> routeAction(NfcCommandCode.LOOP_SUSPEND, JSONObject().put("duration", divided.getOrNull(2)?.toIntOrNull() ?: 60))
                "LGS" -> routeAction(NfcCommandCode.LOOP_LGS, JSONObject())
                "CLOSED" -> routeAction(NfcCommandCode.LOOP_CLOSED, JSONObject())
                else -> NfcExecutionResult(false, rh.gs(R.string.wrong_format))
            }
        }

        private suspend fun processLegacyPump(divided: List<String>): NfcExecutionResult {
            if (divided.size < 2) return NfcExecutionResult(false, rh.gs(R.string.wrong_format))
            return when (divided[1].uppercase(Locale.ROOT)) {
                "CONNECT" -> routeAction(NfcCommandCode.PUMP_CONNECT, JSONObject())
                "DISCONNECT" -> routeAction(NfcCommandCode.PUMP_DISCONNECT, JSONObject().put("duration", divided.getOrNull(2)?.toIntOrNull() ?: 30))
                else -> NfcExecutionResult(false, rh.gs(R.string.wrong_format))
            }
        }

        private suspend fun processLegacyProfile(divided: List<String>): NfcExecutionResult {
            if (divided.size < 2) return NfcExecutionResult(false, rh.gs(R.string.wrong_format))
            val index = divided[1].toIntOrNull() ?: return NfcExecutionResult(false, rh.gs(R.string.wrong_format))
            val percentage = divided.getOrNull(2)?.toIntOrNull() ?: 100
            val profileStore = profileRepository.profile.value ?: return NfcExecutionResult(false, rh.gs(app.aaps.core.ui.R.string.notconfigured))
            val list = profileStore.getProfileList()
            if (index <= 0 || index > list.size) return NfcExecutionResult(false, rh.gs(R.string.wrong_format))
            return routeAction(NfcCommandCode.PROFILE_SWITCH, JSONObject().put("profileName", list[index - 1]).put("percentage", percentage))
        }

        private suspend fun processLegacyBasal(divided: List<String>): NfcExecutionResult {
            if (divided.size < 2) return NfcExecutionResult(false, rh.gs(R.string.wrong_format))
            val sub = divided[1].uppercase(Locale.ROOT)
            return when {
                sub in listOf("STOP", "CANCEL") -> routeAction(NfcCommandCode.BASAL_STOP, JSONObject())
                sub.endsWith("%") -> routeAction(NfcCommandCode.BASAL_PCT, JSONObject().put("percent", sub.removeSuffix("%").toIntOrNull() ?: 100).put("duration", divided.getOrNull(2)?.toIntOrNull() ?: 60))
                else -> routeAction(NfcCommandCode.BASAL_ABS, JSONObject().put("rate", sub.toDoubleOrNull() ?: 0.0).put("duration", divided.getOrNull(2)?.toIntOrNull() ?: 60))
            }
        }

        private suspend fun processLegacyExtended(divided: List<String>): NfcExecutionResult {
            if (divided.size < 2) return NfcExecutionResult(false, rh.gs(R.string.wrong_format))
            val sub = divided[1].uppercase(Locale.ROOT)
            return when {
                sub in listOf("STOP", "CANCEL") -> routeAction(NfcCommandCode.EXTENDED_STOP, JSONObject())
                else -> routeAction(NfcCommandCode.EXTENDED_SET, JSONObject().put("amount", sub.toDoubleOrNull() ?: 0.0).put("duration", divided.getOrNull(2)?.toIntOrNull() ?: 30))
            }
        }

        private suspend fun processLegacyBolus(divided: List<String>): NfcExecutionResult {
            if (divided.size < 2) return NfcExecutionResult(false, rh.gs(R.string.wrong_format))
            val amount = divided[1].toDoubleOrNull() ?: 0.0
            val isMeal = divided.getOrNull(2)?.uppercase(Locale.ROOT) == "MEAL"
            return routeAction(NfcCommandCode.BOLUS, JSONObject().put("amount", amount).put("isMeal", isMeal))
        }

        private suspend fun processLegacyCarbs(divided: List<String>): NfcExecutionResult {
            if (divided.size < 2) return NfcExecutionResult(false, rh.gs(R.string.wrong_format))
            val amount = divided[1].toIntOrNull() ?: 0
            return routeAction(NfcCommandCode.CARBS, JSONObject().put("amount", amount))
        }

        private suspend fun processLegacyTarget(divided: List<String>): NfcExecutionResult {
            if (divided.size < 2) return NfcExecutionResult(false, rh.gs(R.string.wrong_format))
            return when (divided[1].uppercase(Locale.ROOT)) {
                "MEAL" -> routeAction(NfcCommandCode.TARGET_MEAL, JSONObject())
                "ACTIVITY" -> routeAction(NfcCommandCode.TARGET_ACTIVITY, JSONObject())
                "HYPO" -> routeAction(NfcCommandCode.TARGET_HYPO, JSONObject())
                "STOP", "CANCEL" -> routeAction(NfcCommandCode.TARGET_STOP, JSONObject())
                else -> NfcExecutionResult(false, rh.gs(R.string.wrong_format))
            }
        }

        // NFC tags are written ahead of time and may be scanned on any supported pump.
        // Rather than rejecting a duration that is not an exact multiple of the pump's
        // step size, round it UP to the next valid multiple so the command always runs.
        fun roundUpToStep(
            value: Int,
            step: Int,
        ): Int = if (value % step == 0) value else ((value / step) + 1) * step
    }
