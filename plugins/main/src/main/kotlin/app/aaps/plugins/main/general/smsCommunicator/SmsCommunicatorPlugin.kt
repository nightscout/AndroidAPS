package app.aaps.plugins.main.general.smsCommunicator

import android.content.Context
import android.content.Intent
import android.telephony.SmsManager
import android.telephony.SmsMessage
import android.text.TextUtils
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import androidx.preference.PreferenceScreen
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.aaps.core.data.aps.ApsMode
import app.aaps.core.data.configuration.Constants
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.OE
import app.aaps.core.data.model.TT
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.data.time.T
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.automation.Automation
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.configuration.ConfigBuilder
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.iob.GlucoseStatusProvider
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.notifications.Notification
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.pump.DetailedBolusInfo
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.queue.Callback
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventNSClientRestart
import app.aaps.core.interfaces.rx.events.EventNewNotification
import app.aaps.core.interfaces.rx.events.EventPreferenceChange
import app.aaps.core.interfaces.rx.events.EventRefreshOverview
import app.aaps.core.interfaces.smsCommunicator.Sms
import app.aaps.core.interfaces.smsCommunicator.SmsCommunicator
import app.aaps.core.interfaces.sync.XDripBroadcast
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.interfaces.utils.SafeParse
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.IntentKey
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.UnitDoubleKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.constraints.ConstraintObject
import app.aaps.core.objects.extensions.generateCOBString
import app.aaps.core.objects.extensions.round
import app.aaps.core.objects.workflow.LoggingWorker
import app.aaps.core.utils.receivers.DataWorkerStorage
import app.aaps.core.validators.DefaultEditTextValidator
import app.aaps.core.validators.EditTextValidator
import app.aaps.core.validators.preferences.AdaptiveIntPreference
import app.aaps.core.validators.preferences.AdaptiveIntentPreference
import app.aaps.core.validators.preferences.AdaptiveStringPreference
import app.aaps.core.validators.preferences.AdaptiveSwitchPreference
import app.aaps.plugins.main.R
import app.aaps.plugins.main.general.smsCommunicator.activities.SmsCommunicatorOtpActivity
import app.aaps.plugins.main.general.smsCommunicator.events.EventSmsCommunicatorUpdateGui
import app.aaps.plugins.main.general.smsCommunicator.otp.OneTimePassword
import dagger.android.HasAndroidInjector
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import kotlinx.coroutines.Dispatchers
import org.apache.commons.lang3.StringUtils
import org.joda.time.DateTime
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min

@Singleton
class SmsCommunicatorPlugin @Inject constructor(
    private val injector: HasAndroidInjector,
    aapsLogger: AAPSLogger,
    rh: ResourceHelper,
    private val smsManager: SmsManager?,
    private val aapsSchedulers: AapsSchedulers,
    private val preferences: Preferences,
    private val constraintChecker: ConstraintsChecker,
    private val rxBus: RxBus,
    private val profileFunction: ProfileFunction,
    private val profileUtil: ProfileUtil,
    private val fabricPrivacy: FabricPrivacy,
    private val activePlugin: ActivePlugin,
    private val commandQueue: CommandQueue,
    private val loop: Loop,
    private val iobCobCalculator: IobCobCalculator,
    private val xDripBroadcast: XDripBroadcast,
    private var otp: OneTimePassword,
    private val config: Config,
    private val dateUtil: DateUtil,
    private val uel: UserEntryLogger,
    private val glucoseStatusProvider: GlucoseStatusProvider,
    private val persistenceLayer: PersistenceLayer,
    private val decimalFormatter: DecimalFormatter,
    private val configBuilder: ConfigBuilder
) : PluginBase(
    PluginDescription()
        .mainType(PluginType.GENERAL)
        .fragmentClass(SmsCommunicatorFragment::class.java.name)
        .pluginIcon(app.aaps.core.objects.R.drawable.ic_sms)
        .pluginName(R.string.smscommunicator)
        .shortName(R.string.smscommunicator_shortname)
        .preferencesId(PluginDescription.PREFERENCE_SCREEN)
        .description(R.string.description_sms_communicator),
    aapsLogger, rh
), SmsCommunicator {

    @Inject lateinit var automation: Automation

    private val disposable = CompositeDisposable()
    var allowedNumbers: MutableList<String> = ArrayList()
    @Volatile var messageToConfirm: AuthRequest? = null
    @Volatile var lastRemoteBolusTime: Long = 0
    override var messages = ArrayList<Sms>()

    private val CARBS_PATTERN = Pattern.compile("CARBS\\s+(\\d+)(?:\\s*(?:(\\d{1,2}:\\d{2}(?:AM|PM)?)|(\\+\\d{1,2}))(\\s*ALARM)?)?$")
    private val BOLUSCARBS_PATTERN = Pattern.compile("BOLUSCARBS\\s+(\\d+(?:[.,]\\d+)?)\\s+(\\d+)(?:\\s*(?:(\\d{1,2}:\\d{2}(?:AM|PM)?)|(\\+\\d{1,2}))(\\s*ALARM)?)?$")

    private val commands = mapOf(
        "BG" to "BG",
        "LOOP" to "LOOP STOP/DISABLE/START/ENABLE/RESUME/STATUS/CLOSED/LGS\nLOOP SUSPEND 20",
        "AAPSCLIENT" to "AAPSCLIENT RESTART",
        "PUMP" to "PUMP\nPUMP CONNECT\nPUMP DISCONNECT 30\n",
        "BASAL" to "BASAL STOP/CANCEL\nBASAL 0.3\nBASAL 0.3 20\nBASAL 30%\nBASAL 30% 20\n",
        "BOLUS" to "BOLUS 1.2\nBOLUS 1.2 MEAL\n",
        "BOLUSCARBS" to "BOLUSCARBS 1.2 12\nBOLUSCARBS 1.2 12 21:37\nBOLUSCARBS 1.2 12 +15 ALARM\n",
        "EXTENDED" to "EXTENDED STOP/CANCEL\nEXTENDED 2 120",
        "CAL" to "CAL 5.6",
        "PROFILE" to "PROFILE STATUS/LIST\nPROFILE 1\nPROFILE 2 30",
        "TARGET" to "TARGET MEAL/ACTIVITY/HYPO/STOP",
        "SMS" to "SMS DISABLE/STOP",
        "CARBS" to "CARBS 12\nCARBS 12 23:05\nCARBS 12 11:05PM",
        "HELP" to "HELP\nHELP command",
        "RESTART" to "RESTART\nRestart AAPS"
    )

    override fun onStart() {
        processSettings(null)
        super.onStart()
        disposable += rxBus
            .toObservable(EventPreferenceChange::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ event: EventPreferenceChange? -> processSettings(event) }, fabricPrivacy::logException)
    }

    override fun onStop() {
        disposable.clear()
        super.onStop()
    }

    override fun preprocessPreferences(preferenceFragment: PreferenceFragmentCompat) {
        super.preprocessPreferences(preferenceFragment)
        val distance = preferenceFragment.findPreference(IntKey.SmsRemoteBolusDistance.key) as AdaptiveIntPreference?
            ?: return
        val allowedNumbers = preferenceFragment.findPreference(StringKey.SmsAllowedNumbers.key) as AdaptiveStringPreference?
            ?: return
        if (!areMoreNumbers(allowedNumbers.text)) {
            distance.title = (rh.gs(R.string.smscommunicator_remote_bolus_min_distance)
                + ".\n"
                + rh.gs(R.string.smscommunicator_remote_bolus_min_distance_caveat))
            distance.isEnabled = false
        } else {
            distance.title = rh.gs(R.string.smscommunicator_remote_bolus_min_distance)
            distance.isEnabled = true
        }
        allowedNumbers.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _: Preference?, newValue: Any ->
            if (!areMoreNumbers(newValue as String)) {
                distance.text = (Constants.remoteBolusMinDistance / (60 * 1000L)).toString()
                distance.title = (rh.gs(R.string.smscommunicator_remote_bolus_min_distance)
                    + ".\n"
                    + rh.gs(R.string.smscommunicator_remote_bolus_min_distance_caveat))
                distance.isEnabled = false
            } else {
                distance.title = rh.gs(R.string.smscommunicator_remote_bolus_min_distance)
                distance.isEnabled = true
            }
            true
        }
    }

    override fun updatePreferenceSummary(pref: Preference) {
        super.updatePreferenceSummary(pref)
        if (pref is EditTextPreference) {
            if (pref.key.contains(StringKey.SmsAllowedNumbers.key) && (TextUtils.isEmpty(pref.text?.trim { it <= ' ' }))) {
                pref.setSummary(rh.gs(R.string.smscommunicator_allowednumbers_summary))
            }
        }
    }

    // cannot be inner class because of needed injection
    class SmsCommunicatorWorker(
        context: Context,
        params: WorkerParameters
    ) : LoggingWorker(context, params, Dispatchers.IO) {

        @Inject lateinit var smsCommunicatorPlugin: SmsCommunicatorPlugin
        @Inject lateinit var dataWorkerStorage: DataWorkerStorage

        override suspend fun doWorkAndLog(): Result {
            val bundle = dataWorkerStorage.pickupBundle(inputData.getLong(DataWorkerStorage.STORE_KEY, -1))
                ?: return Result.failure(workDataOf("Error" to "missing input data"))
            val format = bundle.getString("format")
                ?: return Result.failure(workDataOf("Error" to "missing format in input data"))
            @Suppress("DEPRECATION") val pdus = bundle["pdus"] as Array<*>
            for (pdu in pdus) {
                val message = SmsMessage.createFromPdu(pdu as ByteArray, format)
                smsCommunicatorPlugin.processSms(Sms(message))
            }
            return Result.success()
        }
    }

    private fun processSettings(ev: EventPreferenceChange?) {
        if (ev == null || ev.isChanged(StringKey.SmsAllowedNumbers.key)) {
            val settings = preferences.get(StringKey.SmsAllowedNumbers)
            allowedNumbers.clear()
            val substrings = settings.split(";").toTypedArray()
            for (number in substrings) {
                val cleaned = number.replace("\\s+".toRegex(), "")
                allowedNumbers.add(cleaned)
                aapsLogger.debug(LTag.SMS, "Found allowed number: $cleaned")
            }
        }
    }

    fun isCommand(command: String, number: String): Boolean {
        var found = false
        commands.forEach { (k, _) ->
            if (k == command) found = true
        }
        return found || messageToConfirm?.requester?.phoneNumber == number
    }

    fun isAllowedNumber(number: String): Boolean {
        for (num in allowedNumbers) {
            if (num == number) return true
        }
        return false
    }

    fun processSms(receivedSms: Sms) {
        if (!isEnabled()) {
            aapsLogger.debug(LTag.SMS, "Ignoring SMS. Plugin disabled.")
            return
        }
        if (!isAllowedNumber(receivedSms.phoneNumber)) {
            aapsLogger.debug(LTag.SMS, "Ignoring SMS from: " + receivedSms.phoneNumber + ". Sender not allowed")
            receivedSms.ignored = true
            messages.add(receivedSms)
            rxBus.send(EventSmsCommunicatorUpdateGui())
            return
        }
        messages.add(receivedSms)
        aapsLogger.debug(LTag.SMS, receivedSms.toString())
        val divided = receivedSms.text.trim().split(Regex("\\s+")).toTypedArray()
        val remoteCommandsAllowed = preferences.get(BooleanKey.SmsAllowRemoteCommands)

        if (divided.isNotEmpty() && isCommand(divided[0].uppercase(Locale.getDefault()), receivedSms.phoneNumber)) {
            when (divided[0].uppercase(Locale.getDefault())) {
                "BG"         ->
                    if (divided.size == 1) processBG(receivedSms)
                    else sendSMS(Sms(receivedSms.phoneNumber, rh.gs(R.string.wrong_format)))

                "LOOP"       ->
                    if (!remoteCommandsAllowed) sendSMS(Sms(receivedSms.phoneNumber, rh.gs(R.string.smscommunicator_remote_command_not_allowed)))
                    else if (divided.size == 2 || divided.size == 3) processLOOP(divided, receivedSms)
                    else sendSMS(Sms(receivedSms.phoneNumber, rh.gs(R.string.wrong_format)))

                "AAPSCLIENT" ->
                    if (divided.size == 2) processNSCLIENT(divided, receivedSms)
                    else sendSMS(Sms(receivedSms.phoneNumber, rh.gs(R.string.wrong_format)))

                "PUMP"       ->
                    if (!remoteCommandsAllowed && divided.size > 1) sendSMS(Sms(receivedSms.phoneNumber, rh.gs(R.string.smscommunicator_remote_command_not_allowed)))
                    else if (divided.size <= 3) processPUMP(divided, receivedSms)
                    else sendSMS(Sms(receivedSms.phoneNumber, rh.gs(R.string.wrong_format)))

                "PROFILE"    ->
                    if (!remoteCommandsAllowed) sendSMS(Sms(receivedSms.phoneNumber, rh.gs(R.string.smscommunicator_remote_command_not_allowed)))
                    else if (divided.size == 2 || divided.size == 3) processPROFILE(divided, receivedSms)
                    else sendSMS(Sms(receivedSms.phoneNumber, rh.gs(R.string.wrong_format)))

                "BASAL"      ->
                    if (!remoteCommandsAllowed) sendSMS(Sms(receivedSms.phoneNumber, rh.gs(R.string.smscommunicator_remote_command_not_allowed)))
                    else if (divided.size == 2 || divided.size == 3) processBASAL(divided, receivedSms)
                    else sendSMS(Sms(receivedSms.phoneNumber, rh.gs(R.string.wrong_format)))

                "EXTENDED"   ->
                    if (!remoteCommandsAllowed) sendSMS(Sms(receivedSms.phoneNumber, rh.gs(R.string.smscommunicator_remote_command_not_allowed)))
                    else if (divided.size == 2 || divided.size == 3) processEXTENDED(divided, receivedSms)
                    else sendSMS(Sms(receivedSms.phoneNumber, rh.gs(R.string.wrong_format)))

                "BOLUS"      ->
                    if (!validateBolusCommand(receivedSms)) return
                    else if (divided.size == 2 || divided.size == 3) processBOLUS(divided, receivedSms)
                    else sendSMS(Sms(receivedSms.phoneNumber, rh.gs(R.string.wrong_format)))

                "BOLUSCARBS" ->
                    if (!validateBolusCommand(receivedSms)) return
                    else if (divided.size >= 3 && divided.size <= 5) processBOLUSCARBS(divided, receivedSms)
                    else sendSMS(Sms(receivedSms.phoneNumber, rh.gs(R.string.wrong_format)))

                "CARBS"      ->
                    if (!remoteCommandsAllowed) sendSMS(Sms(receivedSms.phoneNumber, rh.gs(R.string.smscommunicator_remote_command_not_allowed)))
                    else if (divided.size >= 2 && divided.size <= 4) processCARBS(divided, receivedSms)
                    else sendSMS(Sms(receivedSms.phoneNumber, rh.gs(R.string.wrong_format)))

                "CAL"        ->
                    if (!remoteCommandsAllowed) sendSMS(Sms(receivedSms.phoneNumber, rh.gs(R.string.smscommunicator_remote_command_not_allowed)))
                    else if (divided.size == 2) processCAL(divided, receivedSms)
                    else sendSMS(Sms(receivedSms.phoneNumber, rh.gs(R.string.wrong_format)))

                "TARGET"     ->
                    if (!remoteCommandsAllowed) sendSMS(Sms(receivedSms.phoneNumber, rh.gs(R.string.smscommunicator_remote_command_not_allowed)))
                    else if (divided.size == 2) processTARGET(divided, receivedSms)
                    else sendSMS(Sms(receivedSms.phoneNumber, rh.gs(R.string.wrong_format)))

                "SMS"        ->
                    if (!remoteCommandsAllowed) sendSMS(Sms(receivedSms.phoneNumber, rh.gs(R.string.smscommunicator_remote_command_not_allowed)))
                    else if (divided.size == 2) processSMS(divided, receivedSms)
                    else sendSMS(Sms(receivedSms.phoneNumber, rh.gs(R.string.wrong_format)))

                "HELP"       ->
                    if (divided.size == 1 || divided.size == 2) processHELP(divided, receivedSms)
                    else sendSMS(Sms(receivedSms.phoneNumber, rh.gs(R.string.wrong_format)))

                "RESTART"    ->
                    if (!remoteCommandsAllowed) sendSMS(Sms(receivedSms.phoneNumber, rh.gs(R.string.smscommunicator_remote_command_not_allowed)))
                    else if (divided.size == 1) processRestart()
                    else sendSMS(Sms(receivedSms.phoneNumber, rh.gs(R.string.wrong_format)))

                else         ->
                    if (messageToConfirm?.requester?.phoneNumber == receivedSms.phoneNumber) {
                        val execute = messageToConfirm
                        messageToConfirm = null
                        execute?.action(divided[0])
                    } else {
                        messageToConfirm = null
                        sendSMS(Sms(receivedSms.phoneNumber, rh.gs(R.string.smscommunicator_unknown_command)))
                    }
            }
        }
        rxBus.send(EventSmsCommunicatorUpdateGui())
    }

    private fun validateBolusCommand(receivedSms: Sms): Boolean {
        val pump = activePlugin.activePump
        val minDistance =
            if (areMoreNumbers(preferences.get(StringKey.SmsAllowedNumbers)))
                T.mins(preferences.get(IntKey.SmsRemoteBolusDistance).toLong()).msecs()
            else Constants.remoteBolusMinDistance

        if (!preferences.get(BooleanKey.SmsAllowRemoteCommands)) {
            sendSMS(Sms(receivedSms.phoneNumber, rh.gs(R.string.smscommunicator_remote_command_not_allowed)))
            return false
        }
        if (commandQueue.bolusInQueue()) {
            sendSMS(Sms(receivedSms.phoneNumber, rh.gs(R.string.smscommunicator_another_bolus_in_queue)))
            return false
        }
        if (dateUtil.now() - lastRemoteBolusTime < minDistance) {
            sendSMS(Sms(receivedSms.phoneNumber, rh.gs(R.string.smscommunicator_remote_bolus_not_allowed)))
            return false
        }
        if (pump.isSuspended()) {
            sendSMS(Sms(receivedSms.phoneNumber, rh.gs(app.aaps.core.ui.R.string.pumpsuspended)))
            return false
        }
        return true
    }

    private fun processBG(receivedSms: Sms) {
        val actualBG = iobCobCalculator.ads.actualBg()
        val lastBG = iobCobCalculator.ads.lastBg()
        var reply = ""
        val units = profileUtil.units
        if (actualBG != null) {
            reply = rh.gs(R.string.sms_actual_bg) + " " + profileUtil.fromMgdlToStringInUnits(actualBG.recalculated) + ", "
        } else if (lastBG != null) {
            val agoMilliseconds = dateUtil.now() - lastBG.timestamp
            val agoMin = (agoMilliseconds / 60.0 / 1000.0).toInt()
            reply = rh.gs(R.string.sms_last_bg) + " " + profileUtil.valueInCurrentUnitsDetect(lastBG.recalculated) + " " + rh.gs(R.string.sms_min_ago, agoMin) + ", "
        }
        val glucoseStatus = glucoseStatusProvider.glucoseStatusData
        if (glucoseStatus != null) reply += rh.gs(R.string.sms_delta) + " " + profileUtil.fromMgdlToUnits(glucoseStatus.delta) + " " + units + ", "
        val bolusIob = iobCobCalculator.calculateIobFromBolus().round()
        val basalIob = iobCobCalculator.calculateIobFromTempBasalsIncludingConvertedExtended().round()
        val cobInfo = iobCobCalculator.getCobInfo("SMS COB")
        reply += (rh.gs(R.string.sms_iob) + " " + decimalFormatter.to2Decimal(bolusIob.iob + basalIob.basaliob) + "U ("
            + rh.gs(R.string.sms_bolus) + " " + decimalFormatter.to2Decimal(bolusIob.iob) + "U "
            + rh.gs(R.string.sms_basal) + " " + decimalFormatter.to2Decimal(basalIob.basaliob) + "U), "
            + rh.gs(app.aaps.core.ui.R.string.cob) + ": " + cobInfo.generateCOBString(decimalFormatter))
        sendSMS(Sms(receivedSms.phoneNumber, reply))
        receivedSms.processed = true
    }

    private fun processLOOP(divided: Array<String>, receivedSms: Sms) {
        when (divided[1].uppercase(Locale.getDefault())) {
            "DISABLE", "STOP" -> {
                if (loop.isEnabled()) {
                    val passCode = generatePassCode()
                    val reply = rh.gs(R.string.smscommunicator_loop_disable_reply_with_code, passCode)
                    receivedSms.processed = true
                    messageToConfirm = AuthRequest(injector, receivedSms, reply, passCode, object : SmsAction(pumpCommand = false) {
                        override fun run() {
                            uel.log(Action.LOOP_DISABLED, Sources.SMS)
                            (loop as PluginBase).setPluginEnabled(PluginType.LOOP, false)
                            commandQueue.cancelTempBasal(true, object : Callback() {
                                override fun run() {
                                    rxBus.send(EventRefreshOverview("SMS_LOOP_STOP"))
                                    val replyText = rh.gs(R.string.smscommunicator_loop_has_been_disabled) + " " +
                                        rh.gs(if (result.success) R.string.smscommunicator_tempbasal_canceled else R.string.smscommunicator_tempbasal_cancel_failed)
                                    sendSMS(Sms(receivedSms.phoneNumber, replyText))
                                }
                            })
                        }
                    })
                } else
                    sendSMS(Sms(receivedSms.phoneNumber, rh.gs(app.aaps.core.ui.R.string.loopisdisabled)))
                receivedSms.processed = true
            }

            "ENABLE", "START" -> {
                if (!loop.isEnabled()) {
                    val passCode = generatePassCode()
                    val reply = rh.gs(R.string.smscommunicator_loop_enable_reply_with_code, passCode)
                    receivedSms.processed = true
                    messageToConfirm = AuthRequest(injector, receivedSms, reply, passCode, object : SmsAction(pumpCommand = false) {
                        override fun run() {
                            uel.log(Action.LOOP_ENABLED, Sources.SMS)
                            (loop as PluginBase).setPluginEnabled(PluginType.LOOP, true)
                            sendSMS(Sms(receivedSms.phoneNumber, rh.gs(R.string.smscommunicator_loop_has_been_enabled)))
                            rxBus.send(EventRefreshOverview("SMS_LOOP_START"))
                        }
                    })
                } else
                    sendSMS(Sms(receivedSms.phoneNumber, rh.gs(R.string.smscommunicator_loop_is_enabled)))
                receivedSms.processed = true
            }

            "STATUS"          -> {
                val reply = if (loop.isEnabled()) {
                    if (loop.isSuspended) rh.gs(R.string.sms_loop_suspended_for, loop.minutesToEndOfSuspend())
                    else rh.gs(R.string.smscommunicator_loop_is_enabled) + " - " + getApsModeText()
                } else
                    rh.gs(app.aaps.core.ui.R.string.loopisdisabled)
                sendSMS(Sms(receivedSms.phoneNumber, reply))
                receivedSms.processed = true
            }

            "RESUME"          -> {
                val passCode = generatePassCode()
                val reply = rh.gs(R.string.smscommunicator_loop_resume_reply_with_code, passCode)
                receivedSms.processed = true
                messageToConfirm = AuthRequest(injector, receivedSms, reply, passCode, object : SmsAction(pumpCommand = true) {
                    override fun run() {
                        disposable += persistenceLayer.cancelCurrentOfflineEvent(dateUtil.now(), Action.RESUME, Sources.SMS).subscribe()
                        rxBus.send(EventRefreshOverview("SMS_LOOP_RESUME"))
                        commandQueue.cancelTempBasal(true, object : Callback() {
                            override fun run() {
                                if (!result.success) {
                                    var replyText = rh.gs(R.string.smscommunicator_tempbasal_failed)
                                    replyText += "\n" + activePlugin.activePump.shortStatus(true)
                                    sendSMS(Sms(receivedSms.phoneNumber, replyText))
                                }
                            }
                        })
                        sendSMSToAllNumbers(Sms(receivedSms.phoneNumber, rh.gs(R.string.smscommunicator_loop_resumed)))
                    }
                })
            }

            "SUSPEND"         -> {
                var duration = 0
                if (divided.size == 3) duration = SafeParse.stringToInt(divided[2])
                duration = max(0, duration)
                duration = min(180, duration)
                if (duration == 0) {
                    receivedSms.processed = true
                    sendSMS(Sms(receivedSms.phoneNumber, rh.gs(R.string.smscommunicator_wrong_duration)))
                    return
                } else {
                    val passCode = generatePassCode()
                    val reply = rh.gs(R.string.smscommunicator_suspend_reply_with_code, duration, passCode)
                    receivedSms.processed = true
                    messageToConfirm = AuthRequest(injector, receivedSms, reply, passCode, object : SmsAction(pumpCommand = true, duration) {
                        override fun run() {
                            commandQueue.cancelTempBasal(true, object : Callback() {
                                override fun run() {
                                    if (result.success) {
                                        disposable += persistenceLayer.insertAndCancelCurrentOfflineEvent(
                                            offlineEvent = OE(timestamp = dateUtil.now(), duration = T.mins(anInteger().toLong()).msecs(), reason = OE.Reason.SUSPEND),
                                            action = Action.SUSPEND,
                                            source = Sources.SMS
                                        ).subscribe()
                                        rxBus.send(EventRefreshOverview("SMS_LOOP_SUSPENDED"))
                                        val replyText = rh.gs(R.string.smscommunicator_loop_suspended) + " " +
                                            rh.gs(if (result.success) R.string.smscommunicator_tempbasal_canceled else R.string.smscommunicator_tempbasal_cancel_failed)
                                        sendSMSToAllNumbers(Sms(receivedSms.phoneNumber, replyText))
                                    } else {
                                        var replyText = rh.gs(R.string.smscommunicator_tempbasal_cancel_failed)
                                        replyText += "\n" + activePlugin.activePump.shortStatus(true)
                                        sendSMS(Sms(receivedSms.phoneNumber, replyText))
                                    }
                                }
                            })
                        }
                    })
                }
            }

            "LGS"             -> {
                val passCode = generatePassCode()
                val reply = rh.gs(R.string.smscommunicator_set_lgs_reply_with_code, passCode)
                receivedSms.processed = true
                messageToConfirm = AuthRequest(injector, receivedSms, reply, passCode, object : SmsAction(pumpCommand = false) {
                    override fun run() {
                        uel.log(Action.LGS_LOOP_MODE, Sources.SMS)
                        preferences.put(StringKey.LoopApsMode, ApsMode.LGS.name)
                        rxBus.send(EventPreferenceChange(rh.gs(app.aaps.core.ui.R.string.lowglucosesuspend)))
                        val replyText = rh.gs(R.string.smscommunicator_current_loop_mode, getApsModeText())
                        sendSMSToAllNumbers(Sms(receivedSms.phoneNumber, replyText))
                    }
                })
            }

            "CLOSED"          -> {
                val passCode = generatePassCode()
                val reply = rh.gs(R.string.smscommunicator_set_closed_loop_reply_with_code, passCode)
                receivedSms.processed = true
                messageToConfirm = AuthRequest(injector, receivedSms, reply, passCode, object : SmsAction(pumpCommand = false) {
                    override fun run() {
                        uel.log(Action.CLOSED_LOOP_MODE, Sources.SMS)
                        preferences.put(StringKey.LoopApsMode, ApsMode.CLOSED.name)
                        rxBus.send(EventPreferenceChange(rh.gs(app.aaps.core.ui.R.string.closedloop)))
                        val replyText = rh.gs(R.string.smscommunicator_current_loop_mode, getApsModeText())
                        sendSMSToAllNumbers(Sms(receivedSms.phoneNumber, replyText))
                    }
                })
            }

            else              -> sendSMS(Sms(receivedSms.phoneNumber, rh.gs(R.string.wrong_format)))
        }
    }

    private fun processNSCLIENT(divided: Array<String>, receivedSms: Sms) {
        if (divided[1].uppercase(Locale.getDefault()) == "RESTART") {
            rxBus.send(EventNSClientRestart())
            sendSMS(Sms(receivedSms.phoneNumber, "AAPSCLIENT RESTART SENT"))
            receivedSms.processed = true
        } else
            sendSMS(Sms(receivedSms.phoneNumber, rh.gs(R.string.wrong_format)))
    }

    private fun processHELP(divided: Array<String>, receivedSms: Sms) {
        when {
            divided.size == 1                                                             -> {
                sendSMS(Sms(receivedSms.phoneNumber, commands.keys.toString().replace("[", "").replace("]", "")))
                receivedSms.processed = true
            }

            isCommand(divided[1].uppercase(Locale.getDefault()), receivedSms.phoneNumber) -> {
                commands[divided[1].uppercase(Locale.getDefault())]?.let {
                    sendSMS(Sms(receivedSms.phoneNumber, it))
                    receivedSms.processed = true
                }
            }

            else                                                                          -> sendSMS(Sms(receivedSms.phoneNumber, rh.gs(R.string.wrong_format)))
        }
    }

    private fun processRestart() {
        configBuilder.exitApp("SMS", Sources.SMS, true)
    }

    private fun processPUMP(divided: Array<String>, receivedSms: Sms) {
        if (divided.size == 1) {
            commandQueue.readStatus(rh.gs(app.aaps.core.ui.R.string.sms), object : Callback() {
                override fun run() {
                    val pump = activePlugin.activePump
                    if (result.success) {
                        val reply = pump.shortStatus(true)
                        sendSMS(Sms(receivedSms.phoneNumber, reply))
                    } else {
                        val reply = rh.gs(R.string.sms_read_status_failed)
                        sendSMS(Sms(receivedSms.phoneNumber, reply))
                    }
                }
            })
            receivedSms.processed = true
        } else if ((divided.size == 2) && (divided[1].equals("CONNECT", ignoreCase = true))) {
            val passCode = generatePassCode()
            val reply = rh.gs(R.string.smscommunicator_pump_connect_with_code, passCode)
            receivedSms.processed = true
            messageToConfirm = AuthRequest(injector, receivedSms, reply, passCode, object : SmsAction(pumpCommand = true) {
                override fun run() {
                    commandQueue.cancelTempBasal(true, object : Callback() {
                        override fun run() {
                            if (!result.success) {
                                sendSMS(Sms(receivedSms.phoneNumber, rh.gs(R.string.smscommunicator_pump_connect_fail)))
                            } else {
                                disposable += persistenceLayer.cancelCurrentOfflineEvent(dateUtil.now(), Action.RECONNECT, Sources.SMS).subscribe()
                                sendSMS(Sms(receivedSms.phoneNumber, rh.gs(R.string.smscommunicator_reconnect)))
                                rxBus.send(EventRefreshOverview("SMS_PUMP_START"))
                            }
                        }
                    })
                }
            })
        } else if ((divided.size == 3) && (divided[1].equals("DISCONNECT", ignoreCase = true))) {
            var duration = SafeParse.stringToInt(divided[2])
            duration = max(0, duration)
            duration = min(120, duration)
            if (duration == 0) {
                receivedSms.processed = true
                sendSMS(Sms(receivedSms.phoneNumber, rh.gs(R.string.smscommunicator_wrong_duration)))
                return
            } else {
                val passCode = generatePassCode()
                val reply = rh.gs(R.string.smscommunicator_pump_disconnect_with_code, duration, passCode)
                receivedSms.processed = true
                messageToConfirm = AuthRequest(injector, receivedSms, reply, passCode, object : SmsAction(pumpCommand = true) {
                    override fun run() {
                        val profile = profileFunction.getProfile() ?: return
                        loop.goToZeroTemp(durationInMinutes = duration, profile = profile, reason = OE.Reason.DISCONNECT_PUMP, action = Action.DISCONNECT, source = Sources.SMS)
                        rxBus.send(EventRefreshOverview("SMS_PUMP_DISCONNECT"))
                        sendSMS(Sms(receivedSms.phoneNumber, rh.gs(R.string.smscommunicator_pump_disconnected)))
                    }
                })
            }
        } else {
            sendSMS(Sms(receivedSms.phoneNumber, rh.gs(R.string.wrong_format)))
            return
        }
    }

    private fun processPROFILE(divided: Array<String>, receivedSms: Sms) { // load profiles
        val anInterface = activePlugin.activeProfileSource
        val store = anInterface.profile
        if (store == null) {
            sendSMS(Sms(receivedSms.phoneNumber, rh.gs(app.aaps.core.ui.R.string.notconfigured)))
            receivedSms.processed = true
            return
        }
        val profileName = profileFunction.getProfileName()
        val list = store.getProfileList()
        if (divided[1].uppercase(Locale.getDefault()) == "STATUS") {
            sendSMS(Sms(receivedSms.phoneNumber, profileName))
        } else if (divided[1].uppercase(Locale.getDefault()) == "LIST") {
            if (list.isEmpty()) sendSMS(Sms(receivedSms.phoneNumber, rh.gs(app.aaps.core.ui.R.string.invalid_profile)))
            else {
                var reply = ""
                for (i in list.indices) {
                    if (i > 0) reply += "\n"
                    reply += (i + 1).toString() + ". "
                    reply += list[i]
                }
                sendSMS(Sms(receivedSms.phoneNumber, reply))
            }
        } else {
            val pIndex = SafeParse.stringToInt(divided[1])
            var percentage = 100
            if (divided.size > 2) percentage = SafeParse.stringToInt(divided[2])
            if (pIndex > list.size) sendSMS(Sms(receivedSms.phoneNumber, rh.gs(R.string.wrong_format)))
            else if (percentage == 0) sendSMS(Sms(receivedSms.phoneNumber, rh.gs(R.string.wrong_format)))
            else if (pIndex == 0) sendSMS(Sms(receivedSms.phoneNumber, rh.gs(R.string.wrong_format)))
            else {
                val profile = store.getSpecificProfile(list[pIndex - 1] as String)
                if (profile == null) sendSMS(Sms(receivedSms.phoneNumber, rh.gs(app.aaps.core.ui.R.string.noprofile)))
                else {
                    val passCode = generatePassCode()
                    val reply = rh.gs(R.string.smscommunicator_profile_reply_with_code, list[pIndex - 1], percentage, passCode)
                    receivedSms.processed = true
                    val finalPercentage = percentage
                    messageToConfirm = AuthRequest(injector, receivedSms, reply, passCode, object : SmsAction(pumpCommand = true, list[pIndex - 1] as String, finalPercentage) {
                        override fun run() {
                            if (profileFunction.createProfileSwitch(
                                    profileStore = store,
                                    profileName = list[pIndex - 1] as String,
                                    durationInMinutes = 0,
                                    percentage = finalPercentage,
                                    timeShiftInHours = 0,
                                    timestamp = dateUtil.now(),
                                    action = Action.PROFILE_SWITCH,
                                    source = Sources.SMS,
                                    note = rh.gs(R.string.sms_profile_switch_created),
                                    listValues = listOf(ValueWithUnit.SimpleString(rh.gsNotLocalised(R.string.sms_profile_switch_created)))
                                )
                            ) {
                                val replyText = rh.gs(R.string.sms_profile_switch_created)
                                sendSMS(Sms(receivedSms.phoneNumber, replyText))
                            } else {
                                sendSMS(Sms(receivedSms.phoneNumber, rh.gs(app.aaps.core.ui.R.string.invalid_profile)))
                            }
                        }
                    })
                }
            }
        }
        receivedSms.processed = true
    }

    private fun processBASAL(divided: Array<String>, receivedSms: Sms) {
        if (divided[1].uppercase(Locale.getDefault()) == "CANCEL" || divided[1].uppercase(Locale.getDefault()) == "STOP") {
            val passCode = generatePassCode()
            val reply = rh.gs(R.string.smscommunicator_basal_stop_reply_with_code, passCode)
            receivedSms.processed = true
            messageToConfirm = AuthRequest(injector, receivedSms, reply, passCode, object : SmsAction(pumpCommand = true) {
                override fun run() {
                    commandQueue.cancelTempBasal(true, object : Callback() {
                        override fun run() {
                            if (result.success) {
                                var replyText = rh.gs(R.string.smscommunicator_tempbasal_canceled)
                                replyText += "\n" + activePlugin.activePump.shortStatus(true)
                                sendSMSToAllNumbers(Sms(receivedSms.phoneNumber, replyText))
                                uel.log(
                                    Action.TEMP_BASAL, Sources.SMS, activePlugin.activePump.shortStatus(true) + "\n" + rh.gs(R.string.smscommunicator_tempbasal_canceled),
                                    ValueWithUnit.SimpleString(rh.gsNotLocalised(R.string.smscommunicator_tempbasal_canceled))
                                )
                            } else {
                                var replyText = rh.gs(R.string.smscommunicator_tempbasal_cancel_failed)
                                replyText += "\n" + activePlugin.activePump.shortStatus(true)
                                sendSMS(Sms(receivedSms.phoneNumber, replyText))
                                uel.log(
                                    Action.TEMP_BASAL, Sources.SMS, activePlugin.activePump.shortStatus(true) + "\n" + rh.gs(R.string.smscommunicator_tempbasal_cancel_failed),
                                    ValueWithUnit.SimpleString(rh.gsNotLocalised(R.string.smscommunicator_tempbasal_cancel_failed))
                                )
                            }
                        }
                    })
                }
            })
        } else if (divided[1].endsWith("%")) {
            var tempBasalPct = SafeParse.stringToInt(StringUtils.removeEnd(divided[1], "%"))
            val durationStep = activePlugin.activePump.model().tbrSettings()?.durationStep ?: 60
            var duration = 30
            if (divided.size > 2) duration = SafeParse.stringToInt(divided[2])
            val profile = profileFunction.getProfile()
            if (profile == null) sendSMS(Sms(receivedSms.phoneNumber, rh.gs(app.aaps.core.ui.R.string.noprofile)))
            else if (tempBasalPct == 0 && divided[1] != "0%") sendSMS(Sms(receivedSms.phoneNumber, rh.gs(R.string.wrong_format)))
            else if (duration <= 0 || duration % durationStep != 0) sendSMS(Sms(receivedSms.phoneNumber, rh.gs(R.string.sms_wrong_tbr_duration, durationStep)))
            else {
                tempBasalPct = constraintChecker.applyBasalPercentConstraints(ConstraintObject(tempBasalPct, aapsLogger), profile).value()
                val passCode = generatePassCode()
                val reply = rh.gs(R.string.smscommunicator_basal_pct_reply_with_code, tempBasalPct, duration, passCode)
                receivedSms.processed = true
                messageToConfirm = AuthRequest(injector, receivedSms, reply, passCode, object : SmsAction(pumpCommand = true, tempBasalPct, duration) {
                    override fun run() {
                        commandQueue.tempBasalPercent(anInteger(), secondInteger(), true, profile, PumpSync.TemporaryBasalType.NORMAL, object : Callback() {
                            override fun run() {
                                if (result.success) {
                                    var replyText =
                                        if (result.isPercent) rh.gs(R.string.smscommunicator_tempbasal_set_percent, result.percent, result.duration) else rh.gs(
                                            R.string.smscommunicator_tempbasal_set,
                                            result.absolute,
                                            result.duration
                                        )
                                    replyText += "\n" + activePlugin.activePump.shortStatus(true)
                                    sendSMSToAllNumbers(Sms(receivedSms.phoneNumber, replyText))
                                    if (result.isPercent)
                                        uel.log(
                                            action = Action.TEMP_BASAL, source = Sources.SMS,
                                            note = activePlugin.activePump.shortStatus(true) + "\n" + rh.gs(R.string.smscommunicator_tempbasal_set_percent, result.percent, result.duration),
                                            listValues = listOf(
                                                ValueWithUnit.Percent(result.percent),
                                                ValueWithUnit.Minute(result.duration)
                                            )

                                        )
                                    else
                                        uel.log(
                                            action = Action.TEMP_BASAL,
                                            source = Sources.SMS,
                                            note = activePlugin.activePump.shortStatus(true) + "\n" + rh.gs(R.string.smscommunicator_tempbasal_set, result.absolute, result.duration),
                                            listValues = listOf(
                                                ValueWithUnit.UnitPerHour(result.absolute),
                                                ValueWithUnit.Minute(result.duration)
                                            )
                                        )
                                } else {
                                    var replyText = rh.gs(R.string.smscommunicator_tempbasal_failed)
                                    replyText += "\n" + activePlugin.activePump.shortStatus(true)
                                    sendSMS(Sms(receivedSms.phoneNumber, replyText))
                                    uel.log(
                                        Action.TEMP_BASAL, Sources.SMS, activePlugin.activePump.shortStatus(true) + "\n" + rh.gs(R.string.smscommunicator_tempbasal_failed),
                                        ValueWithUnit.SimpleString(rh.gsNotLocalised(R.string.smscommunicator_tempbasal_failed))
                                    )
                                }
                            }
                        })
                    }
                })
            }
        } else {
            var tempBasal = SafeParse.stringToDouble(divided[1])
            val durationStep = activePlugin.activePump.model().tbrSettings()?.durationStep ?: 60
            var duration = 30
            if (divided.size > 2) duration = SafeParse.stringToInt(divided[2])
            val profile = profileFunction.getProfile()
            if (profile == null) sendSMS(Sms(receivedSms.phoneNumber, rh.gs(app.aaps.core.ui.R.string.noprofile)))
            else if (tempBasal == 0.0 && divided[1] != "0") sendSMS(Sms(receivedSms.phoneNumber, rh.gs(R.string.wrong_format)))
            else if (duration <= 0 || duration % durationStep != 0) sendSMS(Sms(receivedSms.phoneNumber, rh.gs(R.string.sms_wrong_tbr_duration, durationStep)))
            else {
                tempBasal = constraintChecker.applyBasalConstraints(ConstraintObject(tempBasal, aapsLogger), profile).value()
                val passCode = generatePassCode()
                val reply = rh.gs(R.string.smscommunicator_basal_reply_with_code, tempBasal, duration, passCode)
                receivedSms.processed = true
                messageToConfirm = AuthRequest(injector, receivedSms, reply, passCode, object : SmsAction(pumpCommand = true, tempBasal, duration) {
                    override fun run() {
                        commandQueue.tempBasalAbsolute(aDouble(), secondInteger(), true, profile, PumpSync.TemporaryBasalType.NORMAL, object : Callback() {
                            override fun run() {
                                if (result.success) {
                                    var replyText = if (result.isPercent) rh.gs(R.string.smscommunicator_tempbasal_set_percent, result.percent, result.duration)
                                    else rh.gs(R.string.smscommunicator_tempbasal_set, result.absolute, result.duration)
                                    replyText += "\n" + activePlugin.activePump.shortStatus(true)
                                    sendSMSToAllNumbers(Sms(receivedSms.phoneNumber, replyText))
                                    if (result.isPercent)
                                        uel.log(
                                            action = Action.TEMP_BASAL,
                                            source = Sources.SMS,
                                            note = activePlugin.activePump.shortStatus(true) + "\n" + rh.gs(R.string.smscommunicator_tempbasal_set_percent, result.percent, result.duration),
                                            listValues = listOf(
                                                ValueWithUnit.Percent(result.percent),
                                                ValueWithUnit.Minute(result.duration)
                                            )
                                        )
                                    else
                                        uel.log(
                                            action = Action.TEMP_BASAL,
                                            source = Sources.SMS,
                                            note = activePlugin.activePump.shortStatus(true) + "\n" + rh.gs(R.string.smscommunicator_tempbasal_set, result.absolute, result.duration),
                                            listValues = listOf(
                                                ValueWithUnit.UnitPerHour(result.absolute),
                                                ValueWithUnit.Minute(result.duration)
                                            )
                                        )
                                } else {
                                    var replyText = rh.gs(R.string.smscommunicator_tempbasal_failed)
                                    replyText += "\n" + activePlugin.activePump.shortStatus(true)
                                    sendSMS(Sms(receivedSms.phoneNumber, replyText))
                                    uel.log(
                                        Action.TEMP_BASAL, Sources.SMS, activePlugin.activePump.shortStatus(true) + "\n" + rh.gs(R.string.smscommunicator_tempbasal_failed),
                                        ValueWithUnit.SimpleString(rh.gsNotLocalised(R.string.smscommunicator_tempbasal_failed))
                                    )
                                }
                            }
                        })
                    }
                })
            }
        }
    }

    private fun processEXTENDED(divided: Array<String>, receivedSms: Sms) {
        if (divided[1].uppercase(Locale.getDefault()) == "CANCEL" || divided[1].uppercase(Locale.getDefault()) == "STOP") {
            val passCode = generatePassCode()
            val reply = rh.gs(R.string.smscommunicator_extended_stop_reply_with_code, passCode)
            receivedSms.processed = true
            messageToConfirm = AuthRequest(injector, receivedSms, reply, passCode, object : SmsAction(pumpCommand = true) {
                override fun run() {
                    commandQueue.cancelExtended(object : Callback() {
                        override fun run() {
                            if (result.success) {
                                var replyText = rh.gs(R.string.smscommunicator_extended_canceled)
                                replyText += "\n" + activePlugin.activePump.shortStatus(true)
                                sendSMSToAllNumbers(Sms(receivedSms.phoneNumber, replyText))
                            } else {
                                var replyText = rh.gs(R.string.smscommunicator_extended_cancel_failed)
                                replyText += "\n" + activePlugin.activePump.shortStatus(true)
                                sendSMS(Sms(receivedSms.phoneNumber, replyText))
                                uel.log(
                                    Action.EXTENDED_BOLUS, Sources.SMS, activePlugin.activePump.shortStatus(true) + "\n" + rh.gs(R.string.smscommunicator_extended_canceled),
                                    ValueWithUnit.SimpleString(rh.gsNotLocalised(R.string.smscommunicator_extended_canceled))
                                )
                            }
                        }
                    })
                }
            })
        } else if (divided.size != 3) {
            sendSMS(Sms(receivedSms.phoneNumber, rh.gs(R.string.wrong_format)))
        } else {
            var extended = SafeParse.stringToDouble(divided[1])
            val duration = SafeParse.stringToInt(divided[2])
            extended = constraintChecker.applyExtendedBolusConstraints(ConstraintObject(extended, aapsLogger)).value()
            if (extended == 0.0 || duration == 0) sendSMS(Sms(receivedSms.phoneNumber, rh.gs(R.string.wrong_format)))
            else {
                val passCode = generatePassCode()
                val reply = rh.gs(R.string.smscommunicator_extended_reply_with_code, extended, duration, passCode)
                receivedSms.processed = true
                messageToConfirm = AuthRequest(injector, receivedSms, reply, passCode, object : SmsAction(pumpCommand = true, extended, duration) {
                    override fun run() {
                        commandQueue.extendedBolus(aDouble(), secondInteger(), object : Callback() {
                            override fun run() {
                                if (result.success) {
                                    var replyText = rh.gs(R.string.smscommunicator_extended_set, aDouble, duration)
                                    if (config.APS) replyText += "\n" + rh.gs(app.aaps.core.ui.R.string.loopsuspended)
                                    replyText += "\n" + activePlugin.activePump.shortStatus(true)
                                    sendSMSToAllNumbers(Sms(receivedSms.phoneNumber, replyText))
                                    if (config.APS)
                                        uel.log(
                                            action = Action.EXTENDED_BOLUS,
                                            source = Sources.SMS,
                                            note = activePlugin.activePump.shortStatus(true) + "\n" + rh.gs(
                                                R.string.smscommunicator_extended_set,
                                                aDouble,
                                                duration
                                            ) + " / " + rh.gs(app.aaps.core.ui.R.string.loopsuspended),
                                            listValues = listOf(
                                                ValueWithUnit.Insulin(aDouble ?: 0.0),
                                                ValueWithUnit.Minute(duration),
                                                ValueWithUnit.SimpleString(rh.gsNotLocalised(app.aaps.core.ui.R.string.loopsuspended))
                                            )
                                        )
                                    else
                                        uel.log(
                                            action = Action.EXTENDED_BOLUS,
                                            source = Sources.SMS,
                                            note = activePlugin.activePump.shortStatus(true) + "\n" + rh.gs(R.string.smscommunicator_extended_set, aDouble, duration),
                                            listValues = listOf(
                                                ValueWithUnit.Insulin(aDouble ?: 0.0),
                                                ValueWithUnit.Minute(duration)
                                            )
                                        )
                                } else {
                                    var replyText = rh.gs(R.string.smscommunicator_extended_failed)
                                    replyText += "\n" + activePlugin.activePump.shortStatus(true)
                                    sendSMS(Sms(receivedSms.phoneNumber, replyText))
                                    uel.log(
                                        Action.EXTENDED_BOLUS, Sources.SMS, activePlugin.activePump.shortStatus(true) + "\n" + rh.gs(R.string.smscommunicator_extended_failed),
                                        ValueWithUnit.SimpleString(rh.gsNotLocalised(R.string.smscommunicator_extended_failed))
                                    )
                                }
                            }
                        })
                    }
                })
            }
        }
    }

    private fun processBOLUS(divided: Array<String>, receivedSms: Sms) {
        var bolus = SafeParse.stringToDouble(divided[1])
        val isMeal = divided.size > 2 && divided[2].equals("MEAL", ignoreCase = true)
        bolus = constraintChecker.applyBolusConstraints(ConstraintObject(bolus, aapsLogger)).value()
        if (divided.size == 3 && !isMeal) {
            sendSMS(Sms(receivedSms.phoneNumber, rh.gs(R.string.wrong_format)))
        } else if (bolus > 0.0) {
            val passCode = generatePassCode()
            val iob = iobCobCalculator.calculateIobFromBolus().round().iob + iobCobCalculator.calculateIobFromTempBasalsIncludingConvertedExtended().round().basaliob
            val reply = if (isMeal)
                rh.gs(R.string.smscommunicator_meal_bolus_reply_with_code, bolus, passCode, iob)
            else
                rh.gs(R.string.smscommunicator_bolus_reply_with_code, bolus, passCode, iob)

            receivedSms.processed = true
            messageToConfirm = AuthRequest(injector, receivedSms, reply, passCode, object : SmsAction(pumpCommand = true, bolus) {
                override fun run() {
                    val detailedBolusInfo = DetailedBolusInfo()
                    detailedBolusInfo.insulin = aDouble()
                    commandQueue.bolus(detailedBolusInfo, object : Callback() {
                        override fun run() {
                            val resultSuccess = result.success
                            val resultBolusDelivered = result.bolusDelivered
                            commandQueue.readStatus(rh.gs(app.aaps.core.ui.R.string.sms), object : Callback() {
                                override fun run() {
                                    if (resultSuccess) {
                                        var replyText = if (isMeal)
                                            rh.gs(R.string.smscommunicator_meal_bolus_delivered, resultBolusDelivered)
                                        else
                                            rh.gs(R.string.smscommunicator_bolus_delivered, resultBolusDelivered)
                                        replyText += "\n" + activePlugin.activePump.shortStatus(true)
                                        lastRemoteBolusTime = dateUtil.now()
                                        if (isMeal) {
                                            profileFunction.getProfile()?.let { currentProfile ->
                                                val eatingSoonTTDuration = preferences.get(IntKey.OverviewEatingSoonDuration)
                                                val eatingSoonTT = preferences.get(UnitDoubleKey.OverviewEatingSoonTarget)
                                                disposable += persistenceLayer.insertAndCancelCurrentTemporaryTarget(
                                                    temporaryTarget = TT(
                                                        timestamp = dateUtil.now(),
                                                        duration = TimeUnit.MINUTES.toMillis(eatingSoonTTDuration.toLong()),
                                                        reason = TT.Reason.EATING_SOON,
                                                        lowTarget = profileUtil.convertToMgdl(eatingSoonTT, profileUtil.units),
                                                        highTarget = profileUtil.convertToMgdl(eatingSoonTT, profileUtil.units)
                                                    ),
                                                    action = Action.TT,
                                                    source = Sources.SMS,
                                                    note = null,
                                                    listValues = listOf(
                                                        ValueWithUnit.TETTReason(TT.Reason.EATING_SOON),
                                                        ValueWithUnit.Mgdl(profileUtil.convertToMgdl(eatingSoonTT, profileUtil.units)),
                                                        ValueWithUnit.Minute(TimeUnit.MILLISECONDS.toMinutes(TimeUnit.MINUTES.toMillis(eatingSoonTTDuration.toLong())).toInt())
                                                    )
                                                ).subscribe()
                                                val tt = if (currentProfile.units == GlucoseUnit.MMOL) {
                                                    decimalFormatter.to1Decimal(eatingSoonTT)
                                                } else decimalFormatter.to0Decimal(eatingSoonTT)
                                                replyText += "\n" + rh.gs(R.string.smscommunicator_meal_bolus_delivered_tt, tt, eatingSoonTTDuration)
                                            }
                                        }
                                        sendSMSToAllNumbers(Sms(receivedSms.phoneNumber, replyText))
                                        uel.log(Action.BOLUS, Sources.SMS, replyText)
                                    } else {
                                        var replyText = rh.gs(R.string.smscommunicator_bolus_failed)
                                        replyText += "\n" + activePlugin.activePump.shortStatus(true)
                                        sendSMS(Sms(receivedSms.phoneNumber, replyText))
                                        uel.log(
                                            Action.BOLUS, Sources.SMS, activePlugin.activePump.shortStatus(true) + "\n" + rh.gs(R.string.smscommunicator_bolus_failed),
                                            ValueWithUnit.SimpleString(rh.gsNotLocalised(R.string.smscommunicator_bolus_failed))
                                        )
                                    }
                                }
                            })
                        }
                    })
                }
            })
        } else sendSMS(Sms(receivedSms.phoneNumber, rh.gs(R.string.wrong_format)))
    }

    private fun parseTimeOrOffset(timeStr: String?, offset: String?): Long {
        return when {
            timeStr != null -> toTodayTime(timeStr.uppercase(Locale.getDefault()))
            offset != null  -> dateUtil.now() + T.mins(SafeParse.stringToInt(offset).toLong()).msecs()
            else            -> dateUtil.now()
        }
    }

    private fun clampBolus(amount: Double): Double {
        return constraintChecker.applyBolusConstraints(ConstraintObject(amount, aapsLogger)).value()
    }

    private fun clampCarbs(amount: Int): Int {
        return constraintChecker.applyCarbsConstraints(ConstraintObject(amount, aapsLogger)).value()
    }

    private fun processBOLUSCARBS(divided: Array<String>, receivedSms: Sms) {
        val matcher = BOLUSCARBS_PATTERN.matcher(receivedSms.text.uppercase())

        if (!matcher.matches()) {
            sendSMS(Sms(receivedSms.phoneNumber, rh.gs(R.string.wrong_format)))
            return
        }

        val bolus = clampBolus(SafeParse.stringToDouble(divided[1]))
        val carbs = clampCarbs(SafeParse.stringToInt(divided[2]))
        val carbsTime = parseTimeOrOffset(matcher.group(3), matcher.group(4))
        val currentTime = dateUtil.now()
        val offsetMin = SafeParse.stringToLong(matcher.group(4))
        val useAlarm = divided.size == 5

        if (bolus > 0) {
            val iob = iobCobCalculator.calculateIobFromBolus().round().iob +
                iobCobCalculator.calculateIobFromTempBasalsIncludingConvertedExtended().round().basaliob

            val passCode = generatePassCode()
            val reply = rh.gs(
                R.string.smscommunicator_boluscarbs_reply_with_code,
                bolus, carbs, matcher.group(4) ?: dateUtil.timeString(carbsTime), passCode, iob
            )

            receivedSms.processed = true
            messageToConfirm = AuthRequest(injector, receivedSms, reply, passCode,
                                           object : SmsAction(pumpCommand = true, bolus, carbs, currentTime, carbsTime) {
                                               override fun run() {
                                                   val detailedBolusInfo = DetailedBolusInfo()
                                                   detailedBolusInfo.insulin = aDouble()
                                                   detailedBolusInfo.carbs = anInteger().toDouble()
                                                   detailedBolusInfo.timestamp = aLong()
                                                   detailedBolusInfo.carbsTimestamp = secondLong()

                                                   commandQueue.bolus(detailedBolusInfo, object : Callback() {
                                                       override fun run() {
                                                           if (result.success) {
                                                               // If bolusDelivered > 0 is ugly workaround for double callback call.
                                                               // Since commandqueue will call it twice, once with only carbs set and another time with insulin and carbs.
                                                               // For some reason it handles carbs and insulin separately, but we want to perform code below only once.
                                                               if (result.bolusDelivered > 0.0) {
                                                                   val replyText = rh.gs(
                                                                       R.string.smscommunicator_boluscarbs_delivered,
                                                                       result.bolusDelivered, anInteger()
                                                                   )
                                                                   lastRemoteBolusTime = dateUtil.now()
                                                                   sendSMSToAllNumbers(Sms(receivedSms.phoneNumber, replyText))
                                                                   uel.log(Action.BOLUS, Sources.SMS, replyText)
                                                                   if (useAlarm && carbs > 0 && offsetMin > 0) {
                                                                       automation.scheduleTimeToEatReminder(T.mins(offsetMin).secs().toInt())
                                                                   }
                                                               }
                                                           } else {
                                                               var replyText = rh.gs(R.string.smscommunicator_boluscarbs_failed)
                                                               replyText += "\n" + activePlugin.activePump.shortStatus(true)
                                                               sendSMS(Sms(receivedSms.phoneNumber, replyText))
                                                               uel.log(Action.BOLUS, Sources.SMS, replyText)
                                                           }
                                                       }
                                                   })
                                               }
                                           }
            )
        } else {
            sendSMS(Sms(receivedSms.phoneNumber, rh.gs(R.string.wrong_format)))
        }
    }

    private fun toTodayTime(hhColonMm: String): Long {
        val p = Pattern.compile("(\\d+):(\\d+)( a.m.| p.m.| AM| PM|AM|PM|)")
        val m = p.matcher(hhColonMm)
        var retVal: Long = 0
        if (m.find()) {
            var hours = SafeParse.stringToInt(m.group(1))
            val minutes = SafeParse.stringToInt(m.group(2))
            if ((m.group(3) == " a.m." || m.group(3) == " AM" || m.group(3) == "AM") && m.group(1) == "12") hours -= 12
            if ((m.group(3) == " p.m." || m.group(3) == " PM" || m.group(3) == "PM") && m.group(1) != "12") hours += 12
            val t = DateTime()
                .withHourOfDay(hours)
                .withMinuteOfHour(minutes)
                .withSecondOfMinute(0)
                .withMillisOfSecond(0)
            retVal = t.millis
        }
        return retVal
    }

    private fun processCARBS(divided: Array<String>, receivedSms: Sms) {
        val matcher = CARBS_PATTERN.matcher(receivedSms.text.uppercase())
        if (!matcher.matches()) {
            sendSMS(Sms(receivedSms.phoneNumber, rh.gs(R.string.wrong_format)))
            return
        }

        var carbs = SafeParse.stringToInt(matcher.group(1))
        var targetTime: String? = matcher.group(2)
        val offsetMin = SafeParse.stringToLong(matcher.group(3))
        val useAlarm = matcher.group(4) != null
        var time = dateUtil.now()

        if (targetTime != null) {
            time = toTodayTime(targetTime.uppercase(Locale.getDefault()))
            if (time == 0L) {
                sendSMS(Sms(receivedSms.phoneNumber, rh.gs(R.string.wrong_format)))
                return
            }
        } else if (offsetMin > 0) {
            time = dateUtil.now() + T.mins(offsetMin).msecs()
        }

        carbs = constraintChecker.applyCarbsConstraints(ConstraintObject(carbs, aapsLogger)).value()
        if (carbs == 0) sendSMS(Sms(receivedSms.phoneNumber, rh.gs(R.string.wrong_format)))
        else {
            val passCode = generatePassCode()
            val reply = rh.gs(R.string.smscommunicator_carbs_reply_with_code, carbs, matcher.group(3) ?: dateUtil.timeString(time), passCode)
            receivedSms.processed = true
            messageToConfirm = AuthRequest(injector, receivedSms, reply, passCode, object : SmsAction(pumpCommand = true, carbs, time) {
                override fun run() {
                    val detailedBolusInfo = DetailedBolusInfo()
                    detailedBolusInfo.carbs = anInteger().toDouble()
                    detailedBolusInfo.timestamp = aLong()
                    commandQueue.bolus(detailedBolusInfo, object : Callback() {
                        override fun run() {
                            if (result.success) {
                                var replyText = rh.gs(R.string.smscommunicator_carbs_set, anInteger)
                                replyText += "\n" + activePlugin.activePump.shortStatus(true)
                                sendSMSToAllNumbers(Sms(receivedSms.phoneNumber, replyText))
                                uel.log(
                                    Action.CARBS, Sources.SMS, activePlugin.activePump.shortStatus(true) + ": " + rh.gs(R.string.smscommunicator_carbs_set, anInteger),
                                    ValueWithUnit.Gram(anInteger ?: 0)
                                )
                            } else {
                                var replyText = rh.gs(R.string.smscommunicator_carbs_failed, anInteger)
                                replyText += "\n" + activePlugin.activePump.shortStatus(true)
                                sendSMS(Sms(receivedSms.phoneNumber, replyText))
                                uel.log(
                                    Action.CARBS, Sources.SMS, activePlugin.activePump.shortStatus(true) + ": " + rh.gs(R.string.smscommunicator_carbs_failed, anInteger),
                                    ValueWithUnit.Gram(anInteger ?: 0)
                                )
                            }
                        }
                    })

                    if (useAlarm && carbs > 0 && offsetMin > 0) {
                        automation.scheduleTimeToEatReminder(T.mins(offsetMin).secs().toInt())
                    }
                }
            })
        }
    }

    private fun processTARGET(divided: Array<String>, receivedSms: Sms) {
        val isMeal = divided[1].equals("MEAL", ignoreCase = true)
        val isActivity = divided[1].equals("ACTIVITY", ignoreCase = true)
        val isHypo = divided[1].equals("HYPO", ignoreCase = true)
        val isStop = divided[1].equals("STOP", ignoreCase = true) || divided[1].equals("CANCEL", ignoreCase = true)
        if (isMeal || isActivity || isHypo) {
            val passCode = generatePassCode()
            val reply = rh.gs(R.string.smscommunicator_temptarget_with_code, divided[1].uppercase(Locale.getDefault()), passCode)
            receivedSms.processed = true
            messageToConfirm = AuthRequest(injector, receivedSms, reply, passCode, object : SmsAction(pumpCommand = false) {
                override fun run() {
                    val units = profileUtil.units
                    var reason = TT.Reason.EATING_SOON
                    var ttDuration = 0
                    var tt = 0.0
                    when {
                        isMeal     -> {
                            ttDuration = preferences.get(IntKey.OverviewEatingSoonDuration)
                            tt = preferences.get(UnitDoubleKey.OverviewEatingSoonTarget)
                            reason = TT.Reason.EATING_SOON
                        }

                        isActivity -> {
                            ttDuration = preferences.get(IntKey.OverviewActivityDuration)
                            tt = preferences.get(UnitDoubleKey.OverviewActivityTarget)
                            reason = TT.Reason.ACTIVITY
                        }

                        isHypo     -> {
                            ttDuration = preferences.get(IntKey.OverviewHypoDuration)
                            tt = preferences.get(UnitDoubleKey.OverviewHypoTarget)
                            reason = TT.Reason.HYPOGLYCEMIA
                        }
                    }
                    disposable += persistenceLayer.insertAndCancelCurrentTemporaryTarget(
                        temporaryTarget = TT(
                            timestamp = dateUtil.now(),
                            duration = TimeUnit.MINUTES.toMillis(ttDuration.toLong()),
                            reason = reason,
                            lowTarget = profileUtil.convertToMgdl(tt, profileUtil.units),
                            highTarget = profileUtil.convertToMgdl(tt, profileUtil.units)
                        ),
                        action = Action.TT,
                        source = Sources.SMS,
                        note = null,
                        listValues = listOf(
                            ValueWithUnit.fromGlucoseUnit(tt, units),
                            ValueWithUnit.Minute(ttDuration)
                        )
                    ).subscribe()
                    val ttString = if (units == GlucoseUnit.MMOL) decimalFormatter.to1Decimal(tt) else decimalFormatter.to0Decimal(tt)
                    val replyText = rh.gs(R.string.smscommunicator_tt_set, ttString, ttDuration)
                    sendSMSToAllNumbers(Sms(receivedSms.phoneNumber, replyText))
                }
            })
        } else if (isStop) {
            val passCode = generatePassCode()
            val reply = rh.gs(R.string.smscommunicator_temptarget_cancel, passCode)
            receivedSms.processed = true
            messageToConfirm = AuthRequest(injector, receivedSms, reply, passCode, object : SmsAction(pumpCommand = false) {
                override fun run() {
                    disposable += persistenceLayer.cancelCurrentTemporaryTargetIfAny(
                        timestamp = dateUtil.now(),
                        action = Action.CANCEL_TT,
                        source = Sources.SMS,
                        note = rh.gs(R.string.smscommunicator_tt_canceled),
                        listValues = listOf(ValueWithUnit.SimpleString(rh.gsNotLocalised(R.string.smscommunicator_tt_canceled)))
                    ).subscribe()
                    val replyText = rh.gs(R.string.smscommunicator_tt_canceled)
                    sendSMSToAllNumbers(Sms(receivedSms.phoneNumber, replyText))
                    uel.log(
                        Action.CANCEL_TT, Sources.SMS, rh.gs(R.string.smscommunicator_tt_canceled),
                        ValueWithUnit.SimpleString(rh.gsNotLocalised(R.string.smscommunicator_tt_canceled))
                    )
                }
            })
        } else
            sendSMS(Sms(receivedSms.phoneNumber, rh.gs(R.string.wrong_format)))
    }

    private fun processSMS(divided: Array<String>, receivedSms: Sms) {
        val isStop = (divided[1].equals("STOP", ignoreCase = true)
            || divided[1].equals("DISABLE", ignoreCase = true))
        if (isStop) {
            val passCode = generatePassCode()
            val reply = rh.gs(R.string.smscommunicator_stops_ns_with_code, passCode)
            receivedSms.processed = true
            messageToConfirm = AuthRequest(injector, receivedSms, reply, passCode, object : SmsAction(pumpCommand = false) {
                override fun run() {
                    preferences.put(BooleanKey.SmsAllowRemoteCommands, false)
                    val replyText = rh.gs(R.string.smscommunicator_stopped_sms)
                    sendSMSToAllNumbers(Sms(receivedSms.phoneNumber, replyText))
                    uel.log(
                        Action.STOP_SMS, Sources.SMS, rh.gs(R.string.smscommunicator_stopped_sms),
                        ValueWithUnit.SimpleString(rh.gsNotLocalised(R.string.smscommunicator_stopped_sms))
                    )
                }
            })
        } else sendSMS(Sms(receivedSms.phoneNumber, rh.gs(R.string.wrong_format)))
    }

    private fun processCAL(divided: Array<String>, receivedSms: Sms) {
        val cal = SafeParse.stringToDouble(divided[1])
        if (cal > 0.0) {
            val passCode = generatePassCode()
            val reply = rh.gs(R.string.smscommunicator_calibration_reply_with_code, cal, passCode)
            receivedSms.processed = true
            messageToConfirm = AuthRequest(injector, receivedSms, reply, passCode, object : SmsAction(pumpCommand = false, cal) {
                override fun run() {
                    val result = xDripBroadcast.sendCalibration(aDouble!!)
                    val replyText =
                        if (result) rh.gs(R.string.smscommunicator_calibration_sent) else rh.gs(R.string.smscommunicator_calibration_failed)
                    sendSMSToAllNumbers(Sms(receivedSms.phoneNumber, replyText))
                    if (result)
                        uel.log(
                            Action.CALIBRATION, Sources.SMS, rh.gs(R.string.smscommunicator_calibration_sent),
                            ValueWithUnit.SimpleString(rh.gsNotLocalised(R.string.smscommunicator_calibration_sent))
                        )
                    else
                        uel.log(
                            Action.CALIBRATION, Sources.SMS, rh.gs(R.string.smscommunicator_calibration_failed),
                            ValueWithUnit.SimpleString(rh.gsNotLocalised(R.string.smscommunicator_calibration_failed))
                        )
                }
            })
        } else sendSMS(Sms(receivedSms.phoneNumber, rh.gs(R.string.wrong_format)))
    }

    override fun sendNotificationToAllNumbers(text: String): Boolean {
        var result = true
        for (i in allowedNumbers.indices) {
            val sms = Sms(allowedNumbers[i], text)
            result = result && sendSMS(sms)
        }
        return result
    }

    private fun sendSMSToAllNumbers(sms: Sms) {
        for (number in allowedNumbers) {
            sendSMS(Sms(sms, number))
        }
    }

    override fun sendSMS(sms: Sms): Boolean {
        sms.text = stripAccents(sms.text)

        try {
            aapsLogger.debug(LTag.SMS, "Sending SMS to " + sms.phoneNumber + ": " + sms.text)
            if (sms.text.toByteArray().size <= 140) smsManager?.sendTextMessage(sms.phoneNumber, null, sms.text, null, null)
            else {
                val parts = smsManager?.divideMessage(sms.text)
                smsManager?.sendMultipartTextMessage(
                    sms.phoneNumber, null, parts,
                    null, null
                )
            }
            messages.add(sms)
        } catch (e: IllegalArgumentException) {
            return if (e.message == "Invalid message body") {
                val notification = Notification(Notification.INVALID_MESSAGE_BODY, rh.gs(R.string.smscommunicator_message_body), Notification.NORMAL)
                rxBus.send(EventNewNotification(notification))
                false
            } else {
                val notification = Notification(Notification.INVALID_PHONE_NUMBER, rh.gs(R.string.smscommunicator_invalid_phone_number), Notification.NORMAL)
                rxBus.send(EventNewNotification(notification))
                false
            }
        } catch (_: SecurityException) {
            val notification = Notification(Notification.MISSING_SMS_PERMISSION, rh.gs(app.aaps.core.ui.R.string.smscommunicator_missingsmspermission), Notification.NORMAL)
            rxBus.send(EventNewNotification(notification))
            return false
        }
        rxBus.send(EventSmsCommunicatorUpdateGui())
        return true
    }

    private fun generatePassCode(): String =
        rh.gs(R.string.smscommunicator_code_from_authenticator_for, otp.name())

    private fun stripAccents(str: String): String {
        var s = str
        s = Normalizer.normalize(s, Normalizer.Form.NFD)
        s = s.replace("\\p{InCombiningDiacriticalMarks}".toRegex(), "")
        s = s.replace("ł", "l") // hack for Polish language (bug in libs)
        return s
    }

    private fun areMoreNumbers(allowedNumbers: String?): Boolean {
        return allowedNumbers?.let {
            val knownNumbers = HashSet<String>()
            val substrings = it.split(";").toTypedArray()
            for (number in substrings) {
                var cleaned = number.replace(Regex("\\s+"), "")
                if (cleaned.length < 4) continue
                cleaned = cleaned.replace("+", "")
                cleaned = cleaned.replace("-", "")
                if (!cleaned.matches(Regex("[0-9]+"))) continue
                knownNumbers.add(cleaned)
            }
            knownNumbers.size > 1
        } == true
    }

    private fun getApsModeText(): String =
        when (ApsMode.fromString(preferences.get(StringKey.LoopApsMode))) {
            ApsMode.OPEN   -> rh.gs(app.aaps.core.ui.R.string.openloop)
            ApsMode.CLOSED -> rh.gs(app.aaps.core.ui.R.string.closedloop)
            ApsMode.LGS    -> rh.gs(app.aaps.core.ui.R.string.lowglucosesuspend)
            else           -> rh.gs(app.aaps.core.ui.R.string.unknown)
        }

    override fun addPreferenceScreen(preferenceManager: PreferenceManager, parent: PreferenceScreen, context: Context, requiredKey: String?) {
        if (requiredKey != null) return
        val category = PreferenceCategory(context)
        parent.addPreference(category)
        category.apply {
            key = "smscommunicator_settings"
            title = rh.gs(R.string.smscommunicator)
            initialExpandedChildrenCount = 0
            addPreference(
                AdaptiveStringPreference(
                    ctx = context, stringKey = StringKey.SmsAllowedNumbers, summary = R.string.smscommunicator_allowednumbers_summary, title = R.string.smscommunicator_allowednumbers,
                    validatorParams = DefaultEditTextValidator.Parameters(testType = EditTextValidator.TEST_MULTI_PHONE)
                )
            )
            addPreference(AdaptiveSwitchPreference(ctx = context, booleanKey = BooleanKey.SmsAllowRemoteCommands, title = R.string.smscommunicator_remote_commands_allowed))
            addPreference(AdaptiveIntPreference(ctx = context, intKey = IntKey.SmsRemoteBolusDistance, summary = R.string.smscommunicator_remote_bolus_min_distance_summary, title = R.string.smscommunicator_remote_bolus_min_distance))
            addPreference(
                AdaptiveStringPreference(
                    ctx = context, stringKey = StringKey.SmsOtpPassword, summary = R.string.smscommunicator_otp_pin_summary, title = R.string.smscommunicator_otp_pin,
                    validatorParams = DefaultEditTextValidator.Parameters(testType = EditTextValidator.TEST_PIN_STRENGTH)
                )
            )
            addPreference(
                AdaptiveIntentPreference(
                    ctx = context,
                    intentKey = IntentKey.SmsOtpSetup,
                    title = R.string.smscommunicator_tab_otp_label,
                    intent = Intent().apply { action = SmsCommunicatorOtpActivity::class.java.name }
                )
            )
            addPreference(AdaptiveSwitchPreference(ctx = context, booleanKey = BooleanKey.SmsReportPumpUnreachable, summary = R.string.smscommunicator_report_pump_unreachable_summary, title = R.string.smscommunicator_pump_unreachable))
        }
    }
}
