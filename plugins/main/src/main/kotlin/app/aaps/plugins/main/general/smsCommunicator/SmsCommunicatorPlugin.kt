package app.aaps.plugins.main.general.smsCommunicator

import android.content.Context
import android.telephony.SmsManager
import android.telephony.SmsMessage
import android.text.TextUtils
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.aaps.annotations.OpenForTesting
import app.aaps.core.interfaces.aps.ApsMode
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.configuration.Constants
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.db.GlucoseUnit
import app.aaps.core.interfaces.iob.GlucoseStatusProvider
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.notifications.Notification
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.plugin.PluginType
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
import app.aaps.core.interfaces.rx.events.EventPreferenceChange
import app.aaps.core.interfaces.rx.events.EventRefreshOverview
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.interfaces.smsCommunicator.Sms
import app.aaps.core.interfaces.smsCommunicator.SmsCommunicator
import app.aaps.core.interfaces.sync.XDripBroadcast
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.interfaces.utils.SafeParse
import app.aaps.core.interfaces.utils.T
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.main.constraints.ConstraintObject
import app.aaps.core.main.events.EventNewNotification
import app.aaps.core.main.iob.generateCOBString
import app.aaps.core.main.iob.round
import app.aaps.core.main.utils.worker.LoggingWorker
import app.aaps.core.utils.receivers.DataWorkerStorage
import app.aaps.core.validators.ValidatingEditTextPreference
import app.aaps.database.entities.OfflineEvent
import app.aaps.database.entities.TemporaryTarget
import app.aaps.database.entities.UserEntry.Action
import app.aaps.database.entities.UserEntry.Sources
import app.aaps.database.entities.ValueWithUnit
import app.aaps.database.impl.AppRepository
import app.aaps.database.impl.transactions.CancelCurrentOfflineEventIfAnyTransaction
import app.aaps.database.impl.transactions.CancelCurrentTemporaryTargetIfAnyTransaction
import app.aaps.database.impl.transactions.InsertAndCancelCurrentOfflineEventTransaction
import app.aaps.database.impl.transactions.InsertAndCancelCurrentTemporaryTargetTransaction
import app.aaps.plugins.main.R
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

@OpenForTesting
@Singleton
class SmsCommunicatorPlugin @Inject constructor(
    injector: HasAndroidInjector,
    aapsLogger: AAPSLogger,
    rh: ResourceHelper,
    private val smsManager: SmsManager?,
    private val aapsSchedulers: AapsSchedulers,
    private val sp: SP,
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
    private val repository: AppRepository,
    private val decimalFormatter: DecimalFormatter
) : PluginBase(
    PluginDescription()
        .mainType(PluginType.GENERAL)
        .fragmentClass(SmsCommunicatorFragment::class.java.name)
        .pluginIcon(app.aaps.core.main.R.drawable.ic_sms)
        .pluginName(R.string.smscommunicator)
        .shortName(R.string.smscommunicator_shortname)
        .preferencesId(R.xml.pref_smscommunicator)
        .description(R.string.description_sms_communicator),
    aapsLogger, rh, injector
), SmsCommunicator {

    private val disposable = CompositeDisposable()
    var allowedNumbers: MutableList<String> = ArrayList()
    @Volatile var messageToConfirm: AuthRequest? = null
    @Volatile var lastRemoteBolusTime: Long = 0
    override var messages = ArrayList<Sms>()

    val commands = mapOf(
        "BG" to "BG",
        "LOOP" to "LOOP STOP/DISABLE/START/ENABLE/RESUME/STATUS/CLOSED/LGS\nLOOP SUSPEND 20",
        "NSCLIENT" to "NSCLIENT RESTART",
        "PUMP" to "PUMP\nPUMP CONNECT\nPUMP DISCONNECT 30\n",
        "BASAL" to "BASAL STOP/CANCEL\nBASAL 0.3\nBASAL 0.3 20\nBASAL 30%\nBASAL 30% 20\n",
        "BOLUS" to "BOLUS 1.2\nBOLUS 1.2 MEAL",
        "EXTENDED" to "EXTENDED STOP/CANCEL\nEXTENDED 2 120",
        "CAL" to "CAL 5.6",
        "PROFILE" to "PROFILE STATUS/LIST\nPROFILE 1\nPROFILE 2 30",
        "TARGET" to "TARGET MEAL/ACTIVITY/HYPO/STOP",
        "SMS" to "SMS DISABLE/STOP",
        "CARBS" to "CARBS 12\nCARBS 12 23:05\nCARBS 12 11:05PM",
        "HELP" to "HELP\nHELP command"
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
        val distance = preferenceFragment.findPreference(rh.gs(R.string.key_smscommunicator_remote_bolus_min_distance)) as ValidatingEditTextPreference?
            ?: return
        val allowedNumbers = preferenceFragment.findPreference(rh.gs(R.string.key_smscommunicator_allowednumbers)) as EditTextPreference?
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
            if (pref.getKey().contains("smscommunicator_allowednumbers") && (TextUtils.isEmpty(pref.text?.trim { it <= ' ' }))) {
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
        if (ev == null || ev.isChanged(rh.gs(R.string.key_smscommunicator_allowednumbers))) {
            val settings = sp.getString(R.string.key_smscommunicator_allowednumbers, "")
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
        val pump = activePlugin.activePump
        messages.add(receivedSms)
        aapsLogger.debug(LTag.SMS, receivedSms.toString())
        val divided = receivedSms.text.trim().split(Regex("\\s+")).toTypedArray()
        val remoteCommandsAllowed = sp.getBoolean(R.string.key_smscommunicator_remote_commands_allowed, false)

        val minDistance =
            if (areMoreNumbers(sp.getString(R.string.key_smscommunicator_allowednumbers, "")))
                T.mins(sp.getLong(R.string.key_smscommunicator_remote_bolus_min_distance, T.msecs(Constants.remoteBolusMinDistance).mins())).msecs()
            else Constants.remoteBolusMinDistance

        if (divided.isNotEmpty() && isCommand(divided[0].uppercase(Locale.getDefault()), receivedSms.phoneNumber)) {
            when (divided[0].uppercase(Locale.getDefault())) {
                "BG"       ->
                    if (divided.size == 1) processBG(receivedSms)
                    else sendSMS(Sms(receivedSms.phoneNumber, rh.gs(R.string.wrong_format)))

                "LOOP"     ->
                    if (!remoteCommandsAllowed) sendSMS(Sms(receivedSms.phoneNumber, rh.gs(R.string.smscommunicator_remote_command_not_allowed)))
                    else if (divided.size == 2 || divided.size == 3) processLOOP(divided, receivedSms)
                    else sendSMS(Sms(receivedSms.phoneNumber, rh.gs(R.string.wrong_format)))

                "NSCLIENT" ->
                    if (divided.size == 2) processNSCLIENT(divided, receivedSms)
                    else sendSMS(Sms(receivedSms.phoneNumber, rh.gs(R.string.wrong_format)))

                "PUMP"     ->
                    if (!remoteCommandsAllowed && divided.size > 1) sendSMS(Sms(receivedSms.phoneNumber, rh.gs(R.string.smscommunicator_remote_command_not_allowed)))
                    else if (divided.size <= 3) processPUMP(divided, receivedSms)
                    else sendSMS(Sms(receivedSms.phoneNumber, rh.gs(R.string.wrong_format)))

                "PROFILE"  ->
                    if (!remoteCommandsAllowed) sendSMS(Sms(receivedSms.phoneNumber, rh.gs(R.string.smscommunicator_remote_command_not_allowed)))
                    else if (divided.size == 2 || divided.size == 3) processPROFILE(divided, receivedSms)
                    else sendSMS(Sms(receivedSms.phoneNumber, rh.gs(R.string.wrong_format)))

                "BASAL"    ->
                    if (!remoteCommandsAllowed) sendSMS(Sms(receivedSms.phoneNumber, rh.gs(R.string.smscommunicator_remote_command_not_allowed)))
                    else if (divided.size == 2 || divided.size == 3) processBASAL(divided, receivedSms)
                    else sendSMS(Sms(receivedSms.phoneNumber, rh.gs(R.string.wrong_format)))

                "EXTENDED" ->
                    if (!remoteCommandsAllowed) sendSMS(Sms(receivedSms.phoneNumber, rh.gs(R.string.smscommunicator_remote_command_not_allowed)))
                    else if (divided.size == 2 || divided.size == 3) processEXTENDED(divided, receivedSms)
                    else sendSMS(Sms(receivedSms.phoneNumber, rh.gs(R.string.wrong_format)))

                "BOLUS"    ->
                    if (!remoteCommandsAllowed) sendSMS(Sms(receivedSms.phoneNumber, rh.gs(R.string.smscommunicator_remote_command_not_allowed)))
                    else if (commandQueue.bolusInQueue()) sendSMS(Sms(receivedSms.phoneNumber, rh.gs(R.string.smscommunicator_another_bolus_in_queue)))
                    else if (divided.size == 2 && dateUtil.now() - lastRemoteBolusTime < minDistance) sendSMS(Sms(receivedSms.phoneNumber, rh.gs(R.string.smscommunicator_remote_bolus_not_allowed)))
                    else if (divided.size == 2 && pump.isSuspended()) sendSMS(Sms(receivedSms.phoneNumber, rh.gs(app.aaps.core.ui.R.string.pumpsuspended)))
                    else if (divided.size == 2 || divided.size == 3) processBOLUS(divided, receivedSms)
                    else sendSMS(Sms(receivedSms.phoneNumber, rh.gs(R.string.wrong_format)))

                "CARBS"    ->
                    if (!remoteCommandsAllowed) sendSMS(Sms(receivedSms.phoneNumber, rh.gs(R.string.smscommunicator_remote_command_not_allowed)))
                    else if (divided.size == 2 || divided.size == 3) processCARBS(divided, receivedSms)
                    else sendSMS(Sms(receivedSms.phoneNumber, rh.gs(R.string.wrong_format)))

                "CAL"      ->
                    if (!remoteCommandsAllowed) sendSMS(Sms(receivedSms.phoneNumber, rh.gs(R.string.smscommunicator_remote_command_not_allowed)))
                    else if (divided.size == 2) processCAL(divided, receivedSms)
                    else sendSMS(Sms(receivedSms.phoneNumber, rh.gs(R.string.wrong_format)))

                "TARGET"   ->
                    if (!remoteCommandsAllowed) sendSMS(Sms(receivedSms.phoneNumber, rh.gs(R.string.smscommunicator_remote_command_not_allowed)))
                    else if (divided.size == 2) processTARGET(divided, receivedSms)
                    else sendSMS(Sms(receivedSms.phoneNumber, rh.gs(R.string.wrong_format)))

                "SMS"      ->
                    if (!remoteCommandsAllowed) sendSMS(Sms(receivedSms.phoneNumber, rh.gs(R.string.smscommunicator_remote_command_not_allowed)))
                    else if (divided.size == 2) processSMS(divided, receivedSms)
                    else sendSMS(Sms(receivedSms.phoneNumber, rh.gs(R.string.wrong_format)))

                "HELP"     ->
                    if (divided.size == 1 || divided.size == 2) processHELP(divided, receivedSms)
                    else sendSMS(Sms(receivedSms.phoneNumber, rh.gs(R.string.wrong_format)))

                else       ->
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
                if (loop.enabled) {
                    val passCode = generatePassCode()
                    val reply = rh.gs(R.string.smscommunicator_loop_disable_reply_with_code, passCode)
                    receivedSms.processed = true
                    messageToConfirm = AuthRequest(injector, receivedSms, reply, passCode, object : SmsAction(pumpCommand = false) {
                        override fun run() {
                            uel.log(Action.LOOP_DISABLED, Sources.SMS)
                            loop.enabled = false
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
                if (!loop.enabled) {
                    val passCode = generatePassCode()
                    val reply = rh.gs(R.string.smscommunicator_loop_enable_reply_with_code, passCode)
                    receivedSms.processed = true
                    messageToConfirm = AuthRequest(injector, receivedSms, reply, passCode, object : SmsAction(pumpCommand = false) {
                        override fun run() {
                            uel.log(Action.LOOP_ENABLED, Sources.SMS)
                            loop.enabled = true
                            sendSMS(Sms(receivedSms.phoneNumber, rh.gs(R.string.smscommunicator_loop_has_been_enabled)))
                            rxBus.send(EventRefreshOverview("SMS_LOOP_START"))
                        }
                    })
                } else
                    sendSMS(Sms(receivedSms.phoneNumber, rh.gs(R.string.smscommunicator_loop_is_enabled)))
                receivedSms.processed = true
            }

            "STATUS"          -> {
                val reply = if (loop.enabled) {
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
                        uel.log(Action.RESUME, Sources.SMS)
                        disposable += repository.runTransactionForResult(CancelCurrentOfflineEventIfAnyTransaction(dateUtil.now()))
                            .subscribe({ result ->
                                           result.updated.forEach { aapsLogger.debug(LTag.DATABASE, "Updated OfflineEvent $it") }
                                       }, {
                                           aapsLogger.error(LTag.DATABASE, "Error while saving OfflineEvent", it)
                                       })
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
                            uel.log(Action.SUSPEND, Sources.SMS)
                            commandQueue.cancelTempBasal(true, object : Callback() {
                                override fun run() {
                                    if (result.success) {
                                        disposable += repository.runTransactionForResult(
                                            InsertAndCancelCurrentOfflineEventTransaction(
                                                dateUtil.now(),
                                                T.mins(anInteger().toLong()).msecs(),
                                                OfflineEvent.Reason.SUSPEND
                                            )
                                        )
                                            .subscribe({ result ->
                                                           result.updated.forEach { aapsLogger.debug(LTag.DATABASE, "Updated OfflineEvent $it") }
                                                           result.inserted.forEach { aapsLogger.debug(LTag.DATABASE, "Inserted OfflineEvent $it") }
                                                       }, {
                                                           aapsLogger.error(LTag.DATABASE, "Error while saving OfflineEvent", it)
                                                       })
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
                        sp.putString(app.aaps.core.utils.R.string.key_aps_mode, ApsMode.LGS.name)
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
                        sp.putString(app.aaps.core.utils.R.string.key_aps_mode, ApsMode.CLOSED.name)
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
            sendSMS(Sms(receivedSms.phoneNumber, "NSCLIENT RESTART SENT"))
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
                    uel.log(Action.RECONNECT, Sources.SMS)
                    commandQueue.cancelTempBasal(true, object : Callback() {
                        override fun run() {
                            if (!result.success) {
                                sendSMS(Sms(receivedSms.phoneNumber, rh.gs(R.string.smscommunicator_pump_connect_fail)))
                            } else {
                                disposable += repository.runTransactionForResult(CancelCurrentOfflineEventIfAnyTransaction(dateUtil.now()))
                                    .subscribe({ result ->
                                                   result.updated.forEach { aapsLogger.debug(LTag.DATABASE, "Updated OfflineEvent $it") }
                                               }, {
                                                   aapsLogger.error(LTag.DATABASE, "Error while saving OfflineEvent", it)
                                               })
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
                        uel.log(Action.DISCONNECT, Sources.SMS)
                        val profile = profileFunction.getProfile() ?: return
                        loop.goToZeroTemp(duration, profile, OfflineEvent.Reason.DISCONNECT_PUMP)
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
                            if (profileFunction.createProfileSwitch(store, list[pIndex - 1] as String, 0, finalPercentage, 0, dateUtil.now())) {
                                val replyText = rh.gs(R.string.sms_profile_switch_created)
                                sendSMS(Sms(receivedSms.phoneNumber, replyText))
                                uel.log(
                                    Action.PROFILE_SWITCH, Sources.SMS, rh.gs(R.string.sms_profile_switch_created),
                                    ValueWithUnit.SimpleString(rh.gsNotLocalised(R.string.sms_profile_switch_created))
                                )
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
            val durationStep = activePlugin.activePump.model().tbrSettings?.durationStep ?: 60
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
                                            Action.TEMP_BASAL, Sources.SMS,
                                            activePlugin.activePump.shortStatus(true) + "\n" + rh.gs(R.string.smscommunicator_tempbasal_set_percent, result.percent, result.duration),
                                            ValueWithUnit.Percent(result.percent),
                                            ValueWithUnit.Minute(result.duration)
                                        )
                                    else
                                        uel.log(
                                            Action.TEMP_BASAL, Sources.SMS,
                                            activePlugin.activePump.shortStatus(true) + "\n" + rh.gs(R.string.smscommunicator_tempbasal_set, result.absolute, result.duration),
                                            ValueWithUnit.UnitPerHour(result.absolute),
                                            ValueWithUnit.Minute(result.duration)
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
            val durationStep = activePlugin.activePump.model().tbrSettings?.durationStep ?: 60
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
                                            Action.TEMP_BASAL,
                                            Sources.SMS,
                                            activePlugin.activePump.shortStatus(true) + "\n" + rh.gs(R.string.smscommunicator_tempbasal_set_percent, result.percent, result.duration),
                                            ValueWithUnit.Percent(result.percent),
                                            ValueWithUnit.Minute(result.duration)
                                        )
                                    else
                                        uel.log(
                                            Action.TEMP_BASAL,
                                            Sources.SMS,
                                            activePlugin.activePump.shortStatus(true) + "\n" + rh.gs(R.string.smscommunicator_tempbasal_set, result.absolute, result.duration),
                                            ValueWithUnit.UnitPerHour(result.absolute),
                                            ValueWithUnit.Minute(result.duration)
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
                                            Action.EXTENDED_BOLUS,
                                            Sources.SMS,
                                            activePlugin.activePump.shortStatus(true) + "\n" + rh.gs(
                                                R.string.smscommunicator_extended_set,
                                                aDouble,
                                                duration
                                            ) + " / " + rh.gs(app.aaps.core.ui.R.string.loopsuspended),
                                            ValueWithUnit.Insulin(aDouble ?: 0.0),
                                            ValueWithUnit.Minute(duration),
                                            ValueWithUnit.SimpleString(rh.gsNotLocalised(app.aaps.core.ui.R.string.loopsuspended))
                                        )
                                    else
                                        uel.log(
                                            Action.EXTENDED_BOLUS, Sources.SMS, activePlugin.activePump.shortStatus(true) + "\n" + rh.gs(R.string.smscommunicator_extended_set, aDouble, duration),
                                            ValueWithUnit.Insulin(aDouble ?: 0.0),
                                            ValueWithUnit.Minute(duration)
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
            val reply = if (isMeal)
                rh.gs(R.string.smscommunicator_meal_bolus_reply_with_code, bolus, passCode)
            else
                rh.gs(R.string.smscommunicator_bolus_reply_with_code, bolus, passCode)
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
                                                var eatingSoonTTDuration = sp.getInt(app.aaps.core.utils.R.string.key_eatingsoon_duration, Constants.defaultEatingSoonTTDuration)
                                                eatingSoonTTDuration =
                                                    if (eatingSoonTTDuration > 0) eatingSoonTTDuration
                                                    else Constants.defaultEatingSoonTTDuration
                                                var eatingSoonTT =
                                                    sp.getDouble(
                                                        app.aaps.core.utils.R.string.key_eatingsoon_target,
                                                        if (currentProfile.units == GlucoseUnit.MMOL) Constants.defaultEatingSoonTTmmol else Constants.defaultEatingSoonTTmgdl
                                                    )
                                                eatingSoonTT =
                                                    when {
                                                        eatingSoonTT > 0                         -> eatingSoonTT
                                                        currentProfile.units == GlucoseUnit.MMOL -> Constants.defaultEatingSoonTTmmol
                                                        else                                     -> Constants.defaultEatingSoonTTmgdl
                                                    }
                                                disposable += repository.runTransactionForResult(
                                                    InsertAndCancelCurrentTemporaryTargetTransaction(
                                                        timestamp = dateUtil.now(),
                                                        duration = TimeUnit.MINUTES.toMillis(eatingSoonTTDuration.toLong()),
                                                        reason = TemporaryTarget.Reason.EATING_SOON,
                                                        lowTarget = profileUtil.convertToMgdl(eatingSoonTT, profileUtil.units),
                                                        highTarget = profileUtil.convertToMgdl(eatingSoonTT, profileUtil.units)
                                                    )
                                                ).subscribe({ result ->
                                                                result.inserted.forEach { aapsLogger.debug(LTag.DATABASE, "Inserted temp target $it") }
                                                                result.updated.forEach { aapsLogger.debug(LTag.DATABASE, "Updated temp target $it") }
                                                            }, {
                                                                aapsLogger.error(LTag.DATABASE, "Error while saving temporary target", it)
                                                            })
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

    private fun toTodayTime(hh_colon_mm: String): Long {
        val p = Pattern.compile("(\\d+):(\\d+)( a.m.| p.m.| AM| PM|AM|PM|)")
        val m = p.matcher(hh_colon_mm)
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
        var grams = SafeParse.stringToInt(divided[1])
        var time = dateUtil.now()
        if (divided.size > 2) {
            time = toTodayTime(divided[2].uppercase(Locale.getDefault()))
            if (time == 0L) {
                sendSMS(Sms(receivedSms.phoneNumber, rh.gs(R.string.wrong_format)))
                return
            }
        }
        grams = constraintChecker.applyCarbsConstraints(ConstraintObject(grams, aapsLogger)).value()
        if (grams == 0) sendSMS(Sms(receivedSms.phoneNumber, rh.gs(R.string.wrong_format)))
        else {
            val passCode = generatePassCode()
            val reply = rh.gs(R.string.smscommunicator_carbs_reply_with_code, grams, dateUtil.timeString(time), passCode)
            receivedSms.processed = true
            messageToConfirm = AuthRequest(injector, receivedSms, reply, passCode, object : SmsAction(pumpCommand = true, grams, time) {
                override fun run() {
                    val detailedBolusInfo = DetailedBolusInfo()
                    detailedBolusInfo.carbs = anInteger().toDouble()
                    detailedBolusInfo.timestamp = secondLong()
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
                    var keyDuration = 0
                    var defaultTargetDuration = 0
                    var keyTarget = 0
                    var defaultTargetMMOL = 0.0
                    var defaultTargetMGDL = 0.0
                    var reason = TemporaryTarget.Reason.EATING_SOON
                    when {
                        isMeal     -> {
                            keyDuration = app.aaps.core.utils.R.string.key_eatingsoon_duration
                            defaultTargetDuration = Constants.defaultEatingSoonTTDuration
                            keyTarget = app.aaps.core.utils.R.string.key_eatingsoon_target
                            defaultTargetMMOL = Constants.defaultEatingSoonTTmmol
                            defaultTargetMGDL = Constants.defaultEatingSoonTTmgdl
                            reason = TemporaryTarget.Reason.EATING_SOON
                        }

                        isActivity -> {
                            keyDuration = app.aaps.core.utils.R.string.key_activity_duration
                            defaultTargetDuration = Constants.defaultActivityTTDuration
                            keyTarget = app.aaps.core.utils.R.string.key_activity_target
                            defaultTargetMMOL = Constants.defaultActivityTTmmol
                            defaultTargetMGDL = Constants.defaultActivityTTmgdl
                            reason = TemporaryTarget.Reason.ACTIVITY
                        }

                        isHypo     -> {
                            keyDuration = app.aaps.core.utils.R.string.key_hypo_duration
                            defaultTargetDuration = Constants.defaultHypoTTDuration
                            keyTarget = app.aaps.core.utils.R.string.key_hypo_target
                            defaultTargetMMOL = Constants.defaultHypoTTmmol
                            defaultTargetMGDL = Constants.defaultHypoTTmgdl
                            reason = TemporaryTarget.Reason.HYPOGLYCEMIA
                        }
                    }
                    var ttDuration = sp.getInt(keyDuration, defaultTargetDuration)
                    ttDuration = if (ttDuration > 0) ttDuration else defaultTargetDuration
                    var tt = sp.getDouble(keyTarget, if (units == GlucoseUnit.MMOL) defaultTargetMMOL else defaultTargetMGDL)
                    tt = profileUtil.valueInCurrentUnitsDetect(tt)
                    tt = if (tt > 0) tt else if (units == GlucoseUnit.MMOL) defaultTargetMMOL else defaultTargetMGDL
                    disposable += repository.runTransactionForResult(
                        InsertAndCancelCurrentTemporaryTargetTransaction(
                            timestamp = dateUtil.now(),
                            duration = TimeUnit.MINUTES.toMillis(ttDuration.toLong()),
                            reason = reason,
                            lowTarget = profileUtil.convertToMgdl(tt, profileUtil.units),
                            highTarget = profileUtil.convertToMgdl(tt, profileUtil.units)
                        )
                    ).subscribe({ result ->
                                    result.inserted.forEach { aapsLogger.debug(LTag.DATABASE, "Inserted temp target $it") }
                                    result.updated.forEach { aapsLogger.debug(LTag.DATABASE, "Updated temp target $it") }
                                }, {
                                    aapsLogger.error(LTag.DATABASE, "Error while saving temporary target", it)
                                })
                    val ttString = if (units == GlucoseUnit.MMOL) decimalFormatter.to1Decimal(tt) else decimalFormatter.to0Decimal(tt)
                    val replyText = rh.gs(R.string.smscommunicator_tt_set, ttString, ttDuration)
                    sendSMSToAllNumbers(Sms(receivedSms.phoneNumber, replyText))
                    uel.log(
                        Action.TT, Sources.SMS,
                        ValueWithUnit.fromGlucoseUnit(tt, units.asText),
                        ValueWithUnit.Minute(ttDuration)
                    )
                }
            })
        } else if (isStop) {
            val passCode = generatePassCode()
            val reply = rh.gs(R.string.smscommunicator_temptarget_cancel, passCode)
            receivedSms.processed = true
            messageToConfirm = AuthRequest(injector, receivedSms, reply, passCode, object : SmsAction(pumpCommand = false) {
                override fun run() {
                    disposable += repository.runTransactionForResult(CancelCurrentTemporaryTargetIfAnyTransaction(dateUtil.now()))
                        .subscribe({ result ->
                                       result.updated.forEach { aapsLogger.debug(LTag.DATABASE, "Updated temp target $it") }
                                   }, {
                                       aapsLogger.error(LTag.DATABASE, "Error while saving temporary target", it)
                                   })
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
                    sp.putBoolean(R.string.key_smscommunicator_remote_commands_allowed, false)
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
        } catch (e: SecurityException) {
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
        s = s.replace("[\\p{InCombiningDiacriticalMarks}]".toRegex(), "")
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
        } ?: false
    }

    private fun getApsModeText(): String =
        when (ApsMode.fromString(sp.getString(app.aaps.core.utils.R.string.key_aps_mode, ApsMode.OPEN.name))) {
            ApsMode.OPEN   -> rh.gs(app.aaps.core.ui.R.string.openloop)
            ApsMode.CLOSED -> rh.gs(app.aaps.core.ui.R.string.closedloop)
            ApsMode.LGS    -> rh.gs(app.aaps.core.ui.R.string.lowglucosesuspend)
            else           -> rh.gs(app.aaps.core.ui.R.string.unknown)
        }
}
