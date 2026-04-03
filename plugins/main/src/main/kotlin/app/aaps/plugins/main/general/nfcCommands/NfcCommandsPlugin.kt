package app.aaps.plugins.main.general.nfcCommands

import android.content.Context
import android.widget.Toast
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceManager
import androidx.preference.PreferenceScreen
import app.aaps.core.data.configuration.Constants
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.RM
import app.aaps.core.data.model.TT
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.configuration.ConfigBuilder
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.insulin.Insulin
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.profile.LocalProfileManager
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.pump.DetailedBolusInfo
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.queue.Callback
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventNSClientRestart
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.interfaces.utils.SafeParse
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.UnitDoubleKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.constraints.ConstraintObject
import app.aaps.core.validators.preferences.AdaptiveSwitchPreference
import app.aaps.plugins.main.R
import kotlinx.coroutines.runBlocking
import org.apache.commons.lang3.Strings
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

sealed class NfcPrepareResult {
    data class Error(
        val message: String,
        val eraseTag: Boolean = false,
    ) : NfcPrepareResult()

    data class Ready(
        val tokenId: String,
        val commands: List<String>,
        val rewriteWith: NfcIssuedToken?, // null = no rewrite needed
        val oldTag: NfcCreatedTag?, // non-null iff rewriteWith != null
    ) : NfcPrepareResult() {
        init {
            require((rewriteWith == null) == (oldTag == null)) {
                "rewriteWith and oldTag must both be null or both be non-null"
            }
        }
    }
}

data class NfcExecutionResult(
    val success: Boolean,
    val message: String,
    val eraseTag: Boolean = false,
)

@Singleton
class NfcCommandsPlugin
    @Inject
    constructor(
        private val context: Context,
        aapsLogger: AAPSLogger,
        rh: ResourceHelper,
        private val preferences: Preferences,
        private val constraintChecker: ConstraintsChecker,
        private val profileFunction: ProfileFunction,
        private val profileUtil: ProfileUtil,
        private val localProfileManager: LocalProfileManager,
        private val insulin: Insulin,
        private val activePlugin: ActivePlugin,
        private val commandQueue: CommandQueue,
        private val loop: Loop,
        private val dateUtil: DateUtil,
        private val persistenceLayer: PersistenceLayer,
        private val decimalFormatter: DecimalFormatter,
        private val configBuilder: ConfigBuilder,
        private val rxBus: RxBus,
    ) : PluginBase(
            PluginDescription()
                .mainType(PluginType.GENERAL)
                .fragmentClass(NfcCommandsFragment::class.java.name)
                .pluginIcon(app.aaps.core.objects.R.drawable.ic_nfc)
                .pluginName(R.string.nfccommands)
                .shortName(R.string.nfccommands_shortname)
                .preferencesId(PluginDescription.PREFERENCE_SCREEN)
                .description(R.string.description_nfc_communicator),
            aapsLogger,
            rh,
        ) {
        @Volatile private var lastRemoteBolusTime: Long = 0

        override fun addPreferenceScreen(
            preferenceManager: PreferenceManager,
            parent: PreferenceScreen,
            context: Context,
            requiredKey: String?,
        ) {
            if (requiredKey != null) return
            val category = PreferenceCategory(context)
            parent.addPreference(category)
            category.apply {
                key = "nfccommunicator_settings"
                title = rh.gs(R.string.nfccommands)
                addPreference(
                    AdaptiveSwitchPreference(
                        ctx = context,
                        booleanKey = BooleanKey.NfcAllowRemoteCommands,
                        title = R.string.nfccommands_remote_commands_allowed,
                    ),
                )
                addPreference(
                    Preference(context).apply {
                        key = "nfccommunicator_clear_blacklist"
                        title = rh.gs(R.string.nfccommands_clear_blacklist)
                        summary = rh.gs(R.string.nfccommands_clear_blacklist_summary)
                        setOnPreferenceClickListener {
                            NfcTokenSupport.clearBlacklist(context)
                            Toast.makeText(context, rh.gs(R.string.nfccommands_blacklist_cleared), Toast.LENGTH_SHORT).show()
                            true
                        }
                    },
                )
            }
        }

        fun prepareExecution(token: String, tagUid: String? = null): NfcPrepareResult {
            if (!isEnabled()) {
                return NfcPrepareResult.Error(rh.gs(R.string.nfccommands_plugin_disabled))
            }
            val now = dateUtil.now()
            return when (val verified = NfcTokenSupport.verifyToken(context, token, now, tagUid)) {
                is NfcTokenVerificationResult.Failure ->
                    NfcPrepareResult.Error(verified.reason)
                is NfcTokenVerificationResult.Success -> {
                    if (NfcTokenSupport.isBlacklisted(context, verified.tokenId, now)) {
                        aapsLogger.debug(LTag.NFC, "Scanned token is blacklisted: ${verified.tokenId}")
                        return NfcPrepareResult.Error(
                            message = rh.gs(R.string.nfccommands_tag_erased_blacklisted),
                            eraseTag = true,
                        )
                    }
                    val foundTag =
                        NfcTokenSupport
                            .loadCreatedTags(context)
                            .find { it.id == verified.tokenId }
                    if (foundTag != null && foundTag.isExpiringSoon(now)) {
                        val newToken = NfcTokenSupport.issueToken(context, verified.commands, tagUid = tagUid)
                        NfcPrepareResult.Ready(
                            tokenId = verified.tokenId,
                            commands = verified.commands,
                            rewriteWith = newToken,
                            oldTag = foundTag,
                        )
                    } else {
                        NfcPrepareResult.Ready(
                            tokenId = verified.tokenId,
                            commands = verified.commands,
                            rewriteWith = null,
                            oldTag = null,
                        )
                    }
                }
            }
        }

        fun executeToken(token: String): NfcExecutionResult =
            when (val prep = prepareExecution(token)) {
                is NfcPrepareResult.Error ->
                    NfcExecutionResult(success = false, message = prep.message, eraseTag = prep.eraseTag)
                is NfcPrepareResult.Ready ->
                    executeCascade(prep.commands)
            }

        fun replaceTag(
            oldId: String,
            newTag: NfcCreatedTag,
        ) {
            NfcTokenSupport.replaceTag(context, oldId, newTag)
        }

        fun executeCascade(commands: List<String>): NfcExecutionResult {
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

        fun pumpBasalDurationStep(): Int =
            activePlugin.activePump
                .model()
                .tbrSettings()
                ?.durationStep ?: 60

        fun executeCommand(command: String): NfcExecutionResult {
            aapsLogger.debug(LTag.NFC, "Executing NFC command: $command")
            val divided = command.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
            if (divided.isEmpty()) {
                return NfcExecutionResult(false, rh.gs(R.string.wrong_format))
            }
            return when (divided[0].uppercase(Locale.ROOT)) {
                "LOOP" -> requireRemoteCommands { processLoop(divided) }
                "AAPSCLIENT" -> requireRemoteCommands { if (divided.size == 2) processAapsClient(divided) else invalidFormat() }
                "PUMP" -> requireRemoteCommands { processPump(divided) }
                "PROFILE" -> requireRemoteCommands { processProfile(divided) }
                "BASAL" -> requireRemoteCommands { processBasal(divided) }
                "EXTENDED" -> requireRemoteCommands { processExtended(divided) }
                "BOLUS" -> requireRemoteCommands { processBolus(divided) }
                "CARBS" -> requireRemoteCommands { processCarbs(divided) }
                "TARGET" -> requireRemoteCommands { processTarget(divided) }
                "RESTART" ->
                    requireRemoteCommands {
                        if (divided.size == 1) processRestart() else invalidFormat()
                    }
                else -> NfcExecutionResult(false, rh.gs(R.string.nfccommands_unknown_command))
            }
        }

        private fun requireRemoteCommands(block: () -> NfcExecutionResult): NfcExecutionResult {
            val remoteAllowed = preferences.get(BooleanKey.NfcAllowRemoteCommands)
            if (!remoteAllowed) {
                return NfcExecutionResult(false, rh.gs(R.string.nfccommands_remote_command_not_allowed))
            }
            return block()
        }

        private fun invalidFormat(): NfcExecutionResult = NfcExecutionResult(false, rh.gs(R.string.wrong_format))

        private fun processLoop(divided: List<String>): NfcExecutionResult {
            if (divided.size !in 2..3) return invalidFormat()
            val profile = runBlocking { profileFunction.getProfile() } ?: return NfcExecutionResult(false, rh.gs(app.aaps.core.ui.R.string.noprofile))
            return when (divided[1].uppercase(Locale.ROOT)) {
                "DISABLE", "STOP" -> {
                    if (!loop.allowedNextModes().contains(RM.Mode.DISABLED_LOOP)) {
                        NfcExecutionResult(false, rh.gs(app.aaps.core.ui.R.string.loopisdisabled))
                    } else {
                        val result =
                            loop.handleRunningModeChange(
                                newRM = RM.Mode.DISABLED_LOOP,
                                durationInMinutes = Int.MAX_VALUE,
                                action = Action.LOOP_DISABLED,
                                source = Sources.NfcCommands,
                                profile = profile,
                            )
                        val messageId =
                            if (result) {
                                R.string.nfccommands_loop_has_been_disabled
                            } else {
                                R.string.nfccommands_remote_command_not_possible
                            }
                        NfcExecutionResult(result, rh.gs(messageId))
                    }
                }
                "RESUME" -> {
                    if (!loop.allowedNextModes().contains(RM.Mode.RESUME) && loop.runningMode != RM.Mode.DISABLED_LOOP) {
                        NfcExecutionResult(false, rh.gs(R.string.nfccommands_remote_command_not_possible))
                    } else {
                        val result =
                            loop.handleRunningModeChange(
                                newRM = RM.Mode.RESUME,
                                action = Action.RESUME,
                                source = Sources.NfcCommands,
                                profile = profile,
                            )
                        val messageId = if (result) R.string.nfccommands_loop_resumed else R.string.nfccommands_remote_command_not_possible
                        NfcExecutionResult(result, rh.gs(messageId))
                    }
                }
                "SUSPEND" -> {
                    val duration = SafeParse.stringToInt(divided.getOrNull(2))
                    val normalizedDuration = duration.coerceIn(0, 180)
                    if (normalizedDuration == 0) {
                        NfcExecutionResult(false, rh.gs(R.string.nfccommands_wrong_duration))
                    } else if (!loop.allowedNextModes().contains(RM.Mode.SUSPENDED_BY_USER)) {
                        NfcExecutionResult(false, rh.gs(R.string.nfccommands_remote_command_not_possible))
                    } else {
                        val result =
                            loop.handleRunningModeChange(
                                newRM = RM.Mode.SUSPENDED_BY_USER,
                                durationInMinutes = normalizedDuration,
                                action = Action.SUSPEND,
                                source = Sources.NfcCommands,
                                profile = profile,
                            )
                        val messageId = if (result) R.string.nfccommands_loop_suspended else R.string.nfccommands_remote_command_not_possible
                        NfcExecutionResult(result, rh.gs(messageId))
                    }
                }
                "LGS" -> {
                    if (!loop.allowedNextModes().contains(RM.Mode.CLOSED_LOOP_LGS)) {
                        NfcExecutionResult(false, rh.gs(R.string.nfccommands_remote_command_not_possible))
                    } else {
                        val result =
                            loop.handleRunningModeChange(
                                newRM = RM.Mode.CLOSED_LOOP_LGS,
                                action = Action.LGS_LOOP_MODE,
                                source = Sources.NfcCommands,
                                profile = profile,
                            )
                        val message =
                            if (result) {
                                rh.gs(R.string.nfccommands_current_loop_mode, rh.gs(app.aaps.core.ui.R.string.lowglucosesuspend))
                            } else {
                                rh.gs(R.string.nfccommands_remote_command_not_possible)
                            }
                        NfcExecutionResult(result, message)
                    }
                }
                "CLOSED" -> {
                    if (!loop.allowedNextModes().contains(RM.Mode.CLOSED_LOOP)) {
                        NfcExecutionResult(false, rh.gs(R.string.nfccommands_remote_command_not_possible))
                    } else {
                        val result =
                            loop.handleRunningModeChange(
                                newRM = RM.Mode.CLOSED_LOOP,
                                action = Action.CLOSED_LOOP_MODE,
                                source = Sources.NfcCommands,
                                profile = profile,
                            )
                        val message =
                            if (result) {
                                rh.gs(R.string.nfccommands_current_loop_mode, rh.gs(app.aaps.core.ui.R.string.closedloop))
                            } else {
                                rh.gs(R.string.nfccommands_remote_command_not_possible)
                            }
                        NfcExecutionResult(result, message)
                    }
                }
                else -> invalidFormat()
            }
        }

        private fun processAapsClient(divided: List<String>): NfcExecutionResult =
            if (divided[1].equals("RESTART", ignoreCase = true)) {
                rxBus.send(EventNSClientRestart())
                NfcExecutionResult(true, rh.gs(R.string.nfccommands_aapsclient_restart_sent))
            } else {
                invalidFormat()
            }

        private fun processPump(divided: List<String>): NfcExecutionResult {
            return when {
                divided.size == 2 && divided[1].equals("CONNECT", ignoreCase = true) -> {
                    val profile =
                        runBlocking { profileFunction.getProfile() } ?: return NfcExecutionResult(false, rh.gs(app.aaps.core.ui.R.string.noprofile))
                    if (!loop.allowedNextModes().contains(RM.Mode.RESUME)) {
                        NfcExecutionResult(true, rh.gs(app.aaps.core.interfaces.R.string.connected))
                    } else {
                        val result =
                            loop.handleRunningModeChange(
                                newRM = RM.Mode.RESUME,
                                action = Action.RECONNECT,
                                source = Sources.NfcCommands,
                                profile = profile,
                            )
                        val messageId = if (result) R.string.nfccommands_reconnect else R.string.nfccommands_remote_command_not_possible
                        NfcExecutionResult(result, rh.gs(messageId))
                    }
                }
                divided.size == 3 && divided[1].equals("DISCONNECT", ignoreCase = true) -> {
                    val duration = SafeParse.stringToInt(divided[2]).coerceIn(0, 180)
                    val profile =
                        runBlocking { profileFunction.getProfile() } ?: return NfcExecutionResult(false, rh.gs(app.aaps.core.ui.R.string.noprofile))
                    if (duration == 0) {
                        NfcExecutionResult(false, rh.gs(R.string.nfccommands_wrong_duration))
                    } else {
                        val result =
                            loop.handleRunningModeChange(
                                durationInMinutes = duration,
                                profile = profile,
                                newRM = RM.Mode.DISCONNECTED_PUMP,
                                action = Action.DISCONNECT,
                                source = Sources.NfcCommands,
                            )
                        val messageId = if (result) R.string.nfccommands_pump_disconnected else R.string.nfccommands_remote_command_not_possible
                        NfcExecutionResult(result, rh.gs(messageId))
                    }
                }
                else -> invalidFormat()
            }
        }

        private fun processProfile(divided: List<String>): NfcExecutionResult {
            if (divided.size !in 2..3) return invalidFormat()
            val indexToken = divided[1]
            if (indexToken.any { !it.isDigit() }) return invalidFormat()
            val index = SafeParse.stringToInt(indexToken)
            val percentage = divided.getOrNull(2)?.let { SafeParse.stringToInt(it) } ?: 100
            val profileStore = localProfileManager.profile ?: return NfcExecutionResult(false, rh.gs(app.aaps.core.ui.R.string.notconfigured))
            val list = profileStore.getProfileList()
            if (index <= 0 || percentage !in 10..500 || index > list.size) return invalidFormat()
            val name = list[index - 1] as String
            val created =
                runBlocking {
                    profileFunction.createProfileSwitch(
                        profileStore = profileStore,
                        profileName = name,
                        durationInMinutes = 0,
                        percentage = percentage,
                        timeShiftInHours = 0,
                        timestamp = dateUtil.now(),
                        action = Action.PROFILE_SWITCH,
                        source = Sources.NfcCommands,
                        note = rh.gs(R.string.nfccommands_profile_switch_created),
                        listValues = listOf(ValueWithUnit.SimpleString(rh.gsNotLocalised(R.string.nfccommands_profile_switch_created))),
                        iCfg = insulin.iCfg,
                    )
                }
            return if (created != null) {
                NfcExecutionResult(true, rh.gs(R.string.nfccommands_profile_switch_created))
            } else {
                NfcExecutionResult(false, rh.gs(app.aaps.core.ui.R.string.invalid_profile))
            }
        }

        private fun processBasal(divided: List<String>): NfcExecutionResult {
            if (divided.size !in 2..3) return invalidFormat()
            return when {
                divided[1].equals("STOP", ignoreCase = true) || divided[1].equals("CANCEL", ignoreCase = true) -> {
                    commandQueue.cancelTempBasal(
                        enforceNew = true,
                        callback =
                            object : Callback() {
                                override fun run() {
                                    if (!result.success) aapsLogger.error(LTag.NFC, "cancelTempBasal failed: ${result.comment}")
                                }
                            },
                    )
                    NfcExecutionResult(true, rh.gs(R.string.nfccommands_tempbasal_canceled))
                }
                divided[1].endsWith("%") -> {
                    val profile =
                        runBlocking { profileFunction.getProfile() } ?: return NfcExecutionResult(false, rh.gs(app.aaps.core.ui.R.string.noprofile))
                    var tempBasalPct = SafeParse.stringToInt(Strings.CS.removeEnd(divided[1], "%"))
                    val durationStep =
                        activePlugin.activePump
                            .model()
                            .tbrSettings()
                            ?.durationStep ?: 60
                    val rawDuration = divided.getOrNull(2)?.let { SafeParse.stringToInt(it) } ?: durationStep
                    if (tempBasalPct == 0 && divided[1] != "0%") return invalidFormat()
                    if (rawDuration <= 0) {
                        return NfcExecutionResult(false, rh.gs(R.string.nfccommands_wrong_tbr_duration, durationStep))
                    }
                    val duration = roundUpToStep(rawDuration, durationStep)
                    tempBasalPct =
                        constraintChecker.applyBasalPercentConstraints(ConstraintObject(tempBasalPct, aapsLogger), profile).value()
                    commandQueue.tempBasalPercent(
                        tempBasalPct,
                        duration,
                        true,
                        profile,
                        PumpSync.TemporaryBasalType.NORMAL,
                        object : Callback() {
                            override fun run() {
                                if (!result.success) aapsLogger.error(LTag.NFC, "tempBasalPercent failed: ${result.comment}")
                            }
                        },
                    )
                    NfcExecutionResult(true, rh.gs(R.string.nfccommands_command_executed, "BASAL ${divided.drop(1).joinToString(" ")}"))
                }
                else -> {
                    val profile =
                        runBlocking { profileFunction.getProfile() } ?: return NfcExecutionResult(false, rh.gs(app.aaps.core.ui.R.string.noprofile))
                    var tempBasal = SafeParse.stringToDouble(divided[1])
                    val durationStep =
                        activePlugin.activePump
                            .model()
                            .tbrSettings()
                            ?.durationStep ?: 60
                    val rawDuration = divided.getOrNull(2)?.let { SafeParse.stringToInt(it) } ?: durationStep
                    if (tempBasal == 0.0 && divided[1] != "0") return invalidFormat()
                    if (rawDuration <= 0) {
                        return NfcExecutionResult(false, rh.gs(R.string.nfccommands_wrong_tbr_duration, durationStep))
                    }
                    val duration = roundUpToStep(rawDuration, durationStep)
                    tempBasal = constraintChecker.applyBasalConstraints(ConstraintObject(tempBasal, aapsLogger), profile).value()
                    commandQueue.tempBasalAbsolute(
                        tempBasal,
                        duration,
                        true,
                        profile,
                        PumpSync.TemporaryBasalType.NORMAL,
                        object : Callback() {
                            override fun run() {
                                if (!result.success) aapsLogger.error(LTag.NFC, "tempBasalAbsolute failed: ${result.comment}")
                            }
                        },
                    )
                    NfcExecutionResult(true, rh.gs(R.string.nfccommands_command_executed, "BASAL ${divided.drop(1).joinToString(" ")}"))
                }
            }
        }

        private fun processExtended(divided: List<String>): NfcExecutionResult {
            if (divided.size !in 2..3) return invalidFormat()
            return when {
                divided[1].equals("STOP", ignoreCase = true) || divided[1].equals("CANCEL", ignoreCase = true) -> {
                    commandQueue.cancelExtended(
                        object : Callback() {
                            override fun run() {
                                if (!result.success) aapsLogger.error(LTag.NFC, "cancelExtended failed: ${result.comment}")
                            }
                        },
                    )
                    NfcExecutionResult(true, rh.gs(R.string.nfccommands_extended_canceled))
                }
                divided.size != 3 -> invalidFormat()
                else -> {
                    var extended = SafeParse.stringToDouble(divided[1])
                    val duration = SafeParse.stringToInt(divided[2])
                    extended = constraintChecker.applyExtendedBolusConstraints(ConstraintObject(extended, aapsLogger)).value()
                    if (extended <= 0.0 || duration <= 0) return invalidFormat()
                    commandQueue.extendedBolus(
                        extended,
                        duration,
                        object : Callback() {
                            override fun run() {
                                if (!result.success) aapsLogger.error(LTag.NFC, "extendedBolus failed: ${result.comment}")
                            }
                        },
                    )
                    NfcExecutionResult(true, rh.gs(R.string.nfccommands_extended_set, extended, duration))
                }
            }
        }

        private fun processBolus(divided: List<String>): NfcExecutionResult {
            if (divided.size !in 2..3) return invalidFormat()
            if (commandQueue.bolusInQueue()) {
                return NfcExecutionResult(false, rh.gs(R.string.nfccommands_another_bolus_in_queue))
            }
            if (dateUtil.now() - lastRemoteBolusTime < Constants.remoteBolusMinDistance) {
                return NfcExecutionResult(false, rh.gs(R.string.nfccommands_remote_bolus_not_allowed))
            }
            if (loop.runningMode.isSuspended()) {
                return NfcExecutionResult(false, rh.gs(app.aaps.core.ui.R.string.pumpsuspended))
            }
            var bolus = SafeParse.stringToDouble(divided[1])
            val isMeal = divided.size > 2 && divided[2].equals("MEAL", ignoreCase = true)
            bolus = constraintChecker.applyBolusConstraints(ConstraintObject(bolus, aapsLogger)).value()
            if (divided.size == 3 && !isMeal) return invalidFormat()
            if (bolus <= 0.0) return invalidFormat()
            val detailedBolusInfo = DetailedBolusInfo().apply { insulin = bolus }
            commandQueue.bolus(
                detailedBolusInfo,
                object : Callback() {
                    override fun run() {
                        if (!result.success) {
                            aapsLogger.error(LTag.NFC, "bolus failed: ${result.comment}")
                            return
                        }
                        if (result.success) {
                            lastRemoteBolusTime = dateUtil.now()
                            if (isMeal) {
                                runBlocking { profileFunction.getProfile() }?.let { currentProfile ->
                                    val eatingSoonTTDuration = preferences.get(IntKey.OverviewEatingSoonDuration)
                                    val eatingSoonTT = preferences.get(UnitDoubleKey.OverviewEatingSoonTarget)
                                    runBlocking {
                                        persistenceLayer.insertAndCancelCurrentTemporaryTarget(
                                            temporaryTarget =
                                                TT(
                                                    timestamp = dateUtil.now(),
                                                    duration = TimeUnit.MINUTES.toMillis(eatingSoonTTDuration.toLong()),
                                                    reason = TT.Reason.EATING_SOON,
                                                    lowTarget = profileUtil.convertToMgdl(eatingSoonTT, profileUtil.units),
                                                    highTarget = profileUtil.convertToMgdl(eatingSoonTT, profileUtil.units),
                                                ),
                                            action = Action.TT,
                                            source = Sources.NfcCommands,
                                            note = null,
                                            listValues =
                                                listOf(
                                                    ValueWithUnit.TETTReason(TT.Reason.EATING_SOON),
                                                    ValueWithUnit.Mgdl(profileUtil.convertToMgdl(eatingSoonTT, profileUtil.units)),
                                                    ValueWithUnit.Minute(
                                                        TimeUnit.MILLISECONDS
                                                            .toMinutes(
                                                                TimeUnit.MINUTES.toMillis(eatingSoonTTDuration.toLong()),
                                                            ).toInt(),
                                                    ),
                                                ),
                                        )
                                    }
                                    val tt = if (currentProfile.units == GlucoseUnit.MMOL) {
                                        decimalFormatter.to1Decimal(eatingSoonTT)
                                    } else {
                                        decimalFormatter.to0Decimal(eatingSoonTT)
                                    }
                                    aapsLogger.debug(LTag.NFC, "Meal bolus temp target applied: $tt for $eatingSoonTTDuration min")
                                }
                            }
                        }
                    }
                },
            )
            return NfcExecutionResult(true, rh.gs(R.string.nfccommands_command_executed, "BOLUS ${divided.drop(1).joinToString(" ")}"))
        }

        private fun processCarbs(divided: List<String>): NfcExecutionResult {
            if (divided.size != 2) return invalidFormat()
            var grams = SafeParse.stringToInt(divided[1])
            grams = constraintChecker.applyCarbsConstraints(ConstraintObject(grams, aapsLogger)).value()
            if (grams == 0) return invalidFormat()
            val detailedBolusInfo =
                DetailedBolusInfo().apply {
                    carbs = grams.toDouble()
                    timestamp = dateUtil.now()
                }
            commandQueue.bolus(
                detailedBolusInfo,
                object : Callback() {
                    override fun run() {
                        if (!result.success) aapsLogger.error(LTag.NFC, "carbs bolus failed: ${result.comment}")
                    }
                },
            )
            return NfcExecutionResult(true, rh.gs(R.string.nfccommands_carbs_set, grams))
        }

        private fun processTarget(divided: List<String>): NfcExecutionResult {
            if (divided.size != 2) return invalidFormat()
            val isMeal = divided[1].equals("MEAL", ignoreCase = true)
            val isActivity = divided[1].equals("ACTIVITY", ignoreCase = true)
            val isHypo = divided[1].equals("HYPO", ignoreCase = true)
            val isStop = divided[1].equals("STOP", ignoreCase = true) || divided[1].equals("CANCEL", ignoreCase = true)
            return when {
                isMeal || isActivity || isHypo -> {
                    val units = profileUtil.units
                    var reason = TT.Reason.EATING_SOON
                    var ttDuration = 0
                    var tt = 0.0
                    when {
                        isMeal -> {
                            ttDuration = preferences.get(IntKey.OverviewEatingSoonDuration)
                            tt = preferences.get(UnitDoubleKey.OverviewEatingSoonTarget)
                            reason = TT.Reason.EATING_SOON
                        }
                        isActivity -> {
                            ttDuration = preferences.get(IntKey.OverviewActivityDuration)
                            tt = preferences.get(UnitDoubleKey.OverviewActivityTarget)
                            reason = TT.Reason.ACTIVITY
                        }
                        isHypo -> {
                            ttDuration = preferences.get(IntKey.OverviewHypoDuration)
                            tt = preferences.get(UnitDoubleKey.OverviewHypoTarget)
                            reason = TT.Reason.HYPOGLYCEMIA
                        }
                    }
                    runBlocking {
                        persistenceLayer.insertAndCancelCurrentTemporaryTarget(
                            temporaryTarget =
                                TT(
                                    timestamp = dateUtil.now(),
                                    duration = TimeUnit.MINUTES.toMillis(ttDuration.toLong()),
                                    reason = reason,
                                    lowTarget = profileUtil.convertToMgdl(tt, profileUtil.units),
                                    highTarget = profileUtil.convertToMgdl(tt, profileUtil.units),
                                ),
                            action = Action.TT,
                            source = Sources.NfcCommands,
                            note = null,
                            listValues =
                                listOf(
                                    ValueWithUnit.fromGlucoseUnit(tt, units),
                                    ValueWithUnit.Minute(ttDuration),
                                ),
                        )
                    }
                    val ttString = if (units == GlucoseUnit.MMOL) decimalFormatter.to1Decimal(tt) else decimalFormatter.to0Decimal(tt)
                    NfcExecutionResult(true, rh.gs(R.string.nfccommands_tt_set, ttString, ttDuration))
                }
                isStop -> {
                    runBlocking {
                        persistenceLayer.cancelCurrentTemporaryTargetIfAny(
                            timestamp = dateUtil.now(),
                            action = Action.CANCEL_TT,
                            source = Sources.NfcCommands,
                            note = rh.gs(R.string.nfccommands_tt_canceled),
                            listValues = listOf(ValueWithUnit.SimpleString(rh.gsNotLocalised(R.string.nfccommands_tt_canceled))),
                        )
                    }
                    NfcExecutionResult(true, rh.gs(R.string.nfccommands_tt_canceled))
                }
                else -> invalidFormat()
            }
        }

        private fun processRestart(): NfcExecutionResult {
            configBuilder.exitApp("NFC", Sources.NfcCommands, true)
            return NfcExecutionResult(true, rh.gs(R.string.nfccommands_restarting))
        }

        // NFC tags are written ahead of time and may be scanned on any supported pump.
        // Rather than rejecting a duration that is not an exact multiple of the pump's
        // step size, round it UP to the next valid multiple so the command always runs.
        private fun roundUpToStep(
            value: Int,
            step: Int,
        ): Int = if (value % step == 0) value else ((value / step) + 1) * step
    }
