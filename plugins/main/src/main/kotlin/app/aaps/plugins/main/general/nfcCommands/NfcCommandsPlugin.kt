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
            val divided = command.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
            if (divided.isEmpty()) {
                return NfcExecutionResult(false, rh.gs(R.string.wrong_format))
            }
            return when (divided[0].uppercase(Locale.ROOT)) {
                "LOOP" -> requireRemoteCommands { processLoop(divided) }
                "AAPSCLIENT" -> requireRemoteCommands { AapsClientRestartAction(this).execute(divided) }
                "PUMP" -> requireRemoteCommands { processPump(divided) }
                "PROFILE" -> requireRemoteCommands { ProfileSwitchAction(this).execute(divided) }
                "BASAL" -> requireRemoteCommands { processBasal(divided) }
                "EXTENDED" -> requireRemoteCommands { processExtended(divided) }
                "BOLUS" -> requireRemoteCommands { BolusAction(this).execute(divided) }
                "CARBS" -> requireRemoteCommands { CarbsAction(this).execute(divided) }
                "TARGET" -> requireRemoteCommands { processTarget(divided) }
                "RESTART" -> requireRemoteCommands { RestartAction(this).execute(divided) }
                else -> NfcExecutionResult(false, rh.gs(R.string.nfccommands_unknown_command))
            }
        }

        private suspend fun requireRemoteCommands(block: suspend () -> NfcExecutionResult): NfcExecutionResult {
            val remoteAllowed = preferences.get(BooleanKey.NfcAllowRemoteCommands)
            if (!remoteAllowed) {
                return NfcExecutionResult(false, rh.gs(R.string.nfccommands_remote_command_not_allowed))
            }
            return block()
        }

        private suspend fun processLoop(divided: List<String>): NfcExecutionResult {
            if (divided.size < 2) return NfcExecutionResult(false, rh.gs(R.string.wrong_format))
            return when (divided[1].uppercase(Locale.ROOT)) {
                "DISABLE", "STOP" -> LoopStopAction(this).execute(divided)
                "RESUME" -> LoopResumeAction(this).execute(divided)
                "SUSPEND" -> LoopSuspendAction(this).execute(divided)
                "LGS" -> LoopLgsAction(this).execute(divided)
                "CLOSED" -> LoopClosedAction(this).execute(divided)
                else -> NfcExecutionResult(false, rh.gs(R.string.wrong_format))
            }
        }

        private suspend fun processPump(divided: List<String>): NfcExecutionResult {
            if (divided.size < 2) return NfcExecutionResult(false, rh.gs(R.string.wrong_format))
            return when (divided[1].uppercase(Locale.ROOT)) {
                "CONNECT" -> PumpConnectAction(this).execute(divided)
                "DISCONNECT" -> PumpDisconnectAction(this).execute(divided)
                else -> NfcExecutionResult(false, rh.gs(R.string.wrong_format))
            }
        }

        private suspend fun processBasal(divided: List<String>): NfcExecutionResult {
            if (divided.size < 2) return NfcExecutionResult(false, rh.gs(R.string.wrong_format))
            return when {
                divided[1].uppercase(Locale.ROOT) in listOf("STOP", "CANCEL") -> BasalCancelAction(this).execute(divided)
                divided[1].endsWith("%") -> TempBasalPercentAction(this).execute(divided)
                else -> TempBasalAbsoluteAction(this).execute(divided)
            }
        }

        private suspend fun processExtended(divided: List<String>): NfcExecutionResult {
            if (divided.size < 2) return NfcExecutionResult(false, rh.gs(R.string.wrong_format))
            return when {
                divided[1].uppercase(Locale.ROOT) in listOf("STOP", "CANCEL") -> ExtendedCancelAction(this).execute(divided)
                else -> ExtendedSetAction(this).execute(divided)
            }
        }

        private suspend fun processTarget(divided: List<String>): NfcExecutionResult {
            if (divided.size < 2) return NfcExecutionResult(false, rh.gs(R.string.wrong_format))
            return when {
                divided[1].uppercase(Locale.ROOT) in listOf("STOP", "CANCEL") -> TempTargetCancelAction(this).execute(divided)
                else -> TempTargetSetAction(this).execute(divided)
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
