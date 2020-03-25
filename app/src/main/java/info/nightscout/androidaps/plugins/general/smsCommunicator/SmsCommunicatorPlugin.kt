package info.nightscout.androidaps.plugins.general.smsCommunicator

import android.content.Intent
import android.preference.EditTextPreference
import android.preference.Preference
import android.preference.Preference.OnPreferenceChangeListener
import android.preference.PreferenceFragment
import android.telephony.SmsManager
import android.telephony.SmsMessage
import android.text.TextUtils
import com.andreabaccega.widget.ValidatingEditTextPreference
import info.nightscout.androidaps.Config
import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.R
import info.nightscout.androidaps.data.DetailedBolusInfo
import info.nightscout.androidaps.data.Profile
import info.nightscout.androidaps.db.DatabaseHelper
import info.nightscout.androidaps.db.Source
import info.nightscout.androidaps.db.TempTarget
import info.nightscout.androidaps.events.EventPreferenceChange
import info.nightscout.androidaps.events.EventRefreshOverview
import info.nightscout.androidaps.interfaces.Constraint
import info.nightscout.androidaps.interfaces.PluginBase
import info.nightscout.androidaps.interfaces.PluginDescription
import info.nightscout.androidaps.interfaces.PluginType
import info.nightscout.androidaps.logging.L
import info.nightscout.androidaps.plugins.aps.loop.LoopPlugin
import info.nightscout.androidaps.plugins.bus.RxBus.send
import info.nightscout.androidaps.plugins.bus.RxBus.toObservable
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin
import info.nightscout.androidaps.plugins.configBuilder.ProfileFunctions
import info.nightscout.androidaps.plugins.general.nsclient.NSUpload
import info.nightscout.androidaps.plugins.general.nsclient.events.EventNSClientRestart
import info.nightscout.androidaps.plugins.general.overview.events.EventNewNotification
import info.nightscout.androidaps.plugins.general.overview.notifications.Notification
import info.nightscout.androidaps.plugins.general.smsCommunicator.events.EventSmsCommunicatorUpdateGui
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.GlucoseStatus
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.IobCobCalculatorPlugin
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin
import info.nightscout.androidaps.queue.Callback
import info.nightscout.androidaps.utils.*
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import org.apache.commons.lang3.StringUtils
import org.slf4j.LoggerFactory
import java.text.Normalizer
import java.util.*

object SmsCommunicatorPlugin : PluginBase(PluginDescription()
        .mainType(PluginType.GENERAL)
        .fragmentClass(SmsCommunicatorFragment::class.java.name)
        .pluginName(R.string.smscommunicator)
        .shortName(R.string.smscommunicator_shortname)
        .preferencesId(R.xml.pref_smscommunicator)
        .description(R.string.description_sms_communicator)
) {
    private val log = LoggerFactory.getLogger(L.SMS)
    private val disposable = CompositeDisposable()
    var allowedNumbers: MutableList<String> = ArrayList()
    var messageToConfirm: AuthRequest? = null
    var lastRemoteBolusTime: Long = 0
    var messages = ArrayList<Sms>()

    val commands = mapOf(
            "BG" to "BG",
            "LOOP" to "LOOP STOP/DISABLE/START/ENABLE/RESUME/STATUS\nLOOP SUSPEND 20",
            "TREATMENTS" to "TREATMENTS REFRESH",
            "NSCLIENT" to "NSCLIENT RESTART",
            "PUMP" to "PUMP",
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

    init {
        processSettings(null)
    }

    override fun onStart() {
        super.onStart()
        disposable.add(toObservable(EventPreferenceChange::class.java)
                .observeOn(Schedulers.io())
                .subscribe({ event: EventPreferenceChange? -> processSettings(event) }) { throwable: Throwable? -> FabricPrivacy.logException(throwable) }
        )
    }

    override fun onStop() {
        disposable.clear()
        super.onStop()
    }

    override fun preprocessPreferences(preferenceFragment: PreferenceFragment) {
        super.preprocessPreferences(preferenceFragment)
        val distance = preferenceFragment.findPreference(MainApp.gs(R.string.key_smscommunicator_remotebolusmindistance)) as ValidatingEditTextPreference?
                ?: return
        val allowedNumbers = preferenceFragment.findPreference(MainApp.gs(R.string.key_smscommunicator_allowednumbers)) as EditTextPreference?
                ?: return
        if (!areMoreNumbers(allowedNumbers.text)) {
            distance.title = (MainApp.gs(R.string.smscommunicator_remotebolusmindistance)
                    + ".\n"
                    + MainApp.gs(R.string.smscommunicator_remotebolusmindistance_caveat))
            distance.isEnabled = false
        } else {
            distance.title = MainApp.gs(R.string.smscommunicator_remotebolusmindistance)
            distance.isEnabled = true
        }
        allowedNumbers.onPreferenceChangeListener = OnPreferenceChangeListener { _: Preference?, newValue: Any ->
            if (!areMoreNumbers(newValue as String)) {
                distance.text = (Constants.remoteBolusMinDistance / (60 * 1000L)).toString()
                distance.title = (MainApp.gs(R.string.smscommunicator_remotebolusmindistance)
                        + ".\n"
                        + MainApp.gs(R.string.smscommunicator_remotebolusmindistance_caveat))
                distance.isEnabled = false
            } else {
                distance.title = MainApp.gs(R.string.smscommunicator_remotebolusmindistance)
                distance.isEnabled = true
            }
            true
        }
    }

    override fun updatePreferenceSummary(pref: Preference) {
        super.updatePreferenceSummary(pref)
        if (pref is EditTextPreference) {
            val editTextPref = pref
            if (pref.getKey().contains("smscommunicator_allowednumbers") && (editTextPref.text == null || TextUtils.isEmpty(editTextPref.text.trim { it <= ' ' }))) {
                pref.setSummary(MainApp.gs(R.string.smscommunicator_allowednumbers_summary))
            }
        }
    }

    private fun processSettings(ev: EventPreferenceChange?) {
        if (ev == null || ev.isChanged(R.string.key_smscommunicator_allowednumbers)) {
            val settings = SP.getString(R.string.key_smscommunicator_allowednumbers, "")
            allowedNumbers.clear()
            val substrings = settings.split(";").toTypedArray()
            for (number in substrings) {
                val cleaned = number.replace("\\s+".toRegex(), "")
                allowedNumbers.add(cleaned)
                log.debug("Found allowed number: $cleaned")
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

    fun handleNewData(intent: Intent) {
        val bundle = intent.extras ?: return
        val format = bundle.getString("format") ?: return
        val pdus = bundle["pdus"] as Array<*>
        for (pdu in pdus) {
            val message = SmsMessage.createFromPdu(pdu as ByteArray, format)
            processSms(Sms(message))
        }
    }

    fun processSms(receivedSms: Sms) {
        if (!isEnabled(PluginType.GENERAL)) {
            log.debug("Ignoring SMS. Plugin disabled.")
            return
        }
        if (!isAllowedNumber(receivedSms.phoneNumber)) {
            log.debug("Ignoring SMS from: " + receivedSms.phoneNumber + ". Sender not allowed")
            receivedSms.ignored = true
            messages.add(receivedSms)
            send(EventSmsCommunicatorUpdateGui())
            return
        }
        val pump = ConfigBuilderPlugin.getPlugin().activePump ?: return
        messages.add(receivedSms)
        log.debug(receivedSms.toString())
        val splitted = receivedSms.text.split(Regex("\\s+")).toTypedArray()
        val remoteCommandsAllowed = SP.getBoolean(R.string.key_smscommunicator_remotecommandsallowed, false)

        val minDistance =
            if (areMoreNumbers(SP.getString(R.string.key_smscommunicator_allowednumbers, "")))
                T.mins(SP.getLong(R.string.key_smscommunicator_remotebolusmindistance, T.msecs(Constants.remoteBolusMinDistance).mins())).msecs()
            else Constants.remoteBolusMinDistance

        if (splitted.isNotEmpty() && isCommand(splitted[0].toUpperCase(Locale.getDefault()), receivedSms.phoneNumber)) {
            when (splitted[0].toUpperCase(Locale.getDefault())) {
                "BG" ->
                    if (splitted.size == 1) processBG(receivedSms)
                    else sendSMS(Sms(receivedSms.phoneNumber, R.string.wrongformat))
                "LOOP" ->
                    if (!remoteCommandsAllowed) sendSMS(Sms(receivedSms.phoneNumber, R.string.smscommunicator_remotecommandnotallowed))
                    else if (splitted.size == 2 || splitted.size == 3) processLOOP(splitted, receivedSms)
                    else sendSMS(Sms(receivedSms.phoneNumber, R.string.wrongformat))
                "TREATMENTS" ->
                    if (splitted.size == 2) processTREATMENTS(splitted, receivedSms)
                    else sendSMS(Sms(receivedSms.phoneNumber, R.string.wrongformat))
                "NSCLIENT" ->
                    if (splitted.size == 2) processNSCLIENT(splitted, receivedSms)
                    else sendSMS(Sms(receivedSms.phoneNumber, R.string.wrongformat))
                "PUMP" ->
                    if (splitted.size == 1) processPUMP(receivedSms)
                    else sendSMS(Sms(receivedSms.phoneNumber, R.string.wrongformat))
                "PROFILE" ->
                    if (!remoteCommandsAllowed) sendSMS(Sms(receivedSms.phoneNumber, R.string.smscommunicator_remotecommandnotallowed))
                    else if (splitted.size == 2 || splitted.size == 3) processPROFILE(splitted, receivedSms)
                    else sendSMS(Sms(receivedSms.phoneNumber, R.string.wrongformat))
                "BASAL" ->
                    if (!remoteCommandsAllowed) sendSMS(Sms(receivedSms.phoneNumber, R.string.smscommunicator_remotecommandnotallowed))
                    else if (splitted.size == 2 || splitted.size == 3) processBASAL(splitted, receivedSms)
                    else sendSMS(Sms(receivedSms.phoneNumber, R.string.wrongformat))
                "EXTENDED" ->
                    if (!remoteCommandsAllowed) sendSMS(Sms(receivedSms.phoneNumber, R.string.smscommunicator_remotecommandnotallowed))
                    else if (splitted.size == 2 || splitted.size == 3) processEXTENDED(splitted, receivedSms)
                    else sendSMS(Sms(receivedSms.phoneNumber, R.string.wrongformat))
                "BOLUS" ->
                    if (!remoteCommandsAllowed) sendSMS(Sms(receivedSms.phoneNumber, R.string.smscommunicator_remotecommandnotallowed))
                    else if (splitted.size == 2 && DateUtil.now() - lastRemoteBolusTime < minDistance) sendSMS(Sms(receivedSms.phoneNumber, R.string.smscommunicator_remotebolusnotallowed))
                    else if (splitted.size == 2 && pump.isSuspended) sendSMS(Sms(receivedSms.phoneNumber, R.string.pumpsuspended))
                    else if (splitted.size == 2 || splitted.size == 3) processBOLUS(splitted, receivedSms)
                    else sendSMS(Sms(receivedSms.phoneNumber, R.string.wrongformat))
                "CARBS" ->
                    if (!remoteCommandsAllowed) sendSMS(Sms(receivedSms.phoneNumber, R.string.smscommunicator_remotecommandnotallowed))
                    else if (splitted.size == 2 || splitted.size == 3) processCARBS(splitted, receivedSms)
                    else sendSMS(Sms(receivedSms.phoneNumber, R.string.wrongformat))
                "CAL" ->
                    if (!remoteCommandsAllowed) sendSMS(Sms(receivedSms.phoneNumber, R.string.smscommunicator_remotecommandnotallowed))
                    else if (splitted.size == 2) processCAL(splitted, receivedSms)
                    else sendSMS(Sms(receivedSms.phoneNumber, R.string.wrongformat))
                "TARGET" ->
                    if (!remoteCommandsAllowed) sendSMS(Sms(receivedSms.phoneNumber, R.string.smscommunicator_remotecommandnotallowed))
                    else if (splitted.size == 2) processTARGET(splitted, receivedSms)
                    else sendSMS(Sms(receivedSms.phoneNumber, R.string.wrongformat))
                "SMS" ->
                    if (!remoteCommandsAllowed) sendSMS(Sms(receivedSms.phoneNumber, R.string.smscommunicator_remotecommandnotallowed))
                    else if (splitted.size == 2) processSMS(splitted, receivedSms)
                    else sendSMS(Sms(receivedSms.phoneNumber, R.string.wrongformat))
                "HELP" ->
                    if (splitted.size == 1 || splitted.size == 2) processHELP(splitted, receivedSms)
                    else sendSMS(Sms(receivedSms.phoneNumber, R.string.wrongformat))
                else ->
                    if (messageToConfirm?.requester?.phoneNumber == receivedSms.phoneNumber) {
                        messageToConfirm?.action(splitted[0])
                        messageToConfirm = null
                    } else sendSMS(Sms(receivedSms.phoneNumber, R.string.smscommunicator_unknowncommand))
            }
        }
        send(EventSmsCommunicatorUpdateGui())
    }

    private fun processBG(receivedSms: Sms) {
        val actualBG = DatabaseHelper.actualBg()
        val lastBG = DatabaseHelper.lastBg()
        var reply = ""
        val units = ProfileFunctions.getSystemUnits()
        if (actualBG != null) {
            reply = MainApp.gs(R.string.sms_actualbg) + " " + actualBG.valueToUnitsToString(units) + ", "
        } else if (lastBG != null) {
            val agoMsec = System.currentTimeMillis() - lastBG.date
            val agoMin = (agoMsec / 60.0 / 1000.0).toInt()
            reply = MainApp.gs(R.string.sms_lastbg) + " " + lastBG.valueToUnitsToString(units) + " " + String.format(MainApp.gs(R.string.sms_minago), agoMin) + ", "
        }
        val glucoseStatus = GlucoseStatus.getGlucoseStatusData()
        if (glucoseStatus != null) reply += MainApp.gs(R.string.sms_delta) + " " + Profile.toUnitsString(glucoseStatus.delta, glucoseStatus.delta * Constants.MGDL_TO_MMOLL, units) + " " + units + ", "
        TreatmentsPlugin.getPlugin().updateTotalIOBTreatments()
        val bolusIob = TreatmentsPlugin.getPlugin().lastCalculationTreatments.round()
        TreatmentsPlugin.getPlugin().updateTotalIOBTempBasals()
        val basalIob = TreatmentsPlugin.getPlugin().lastCalculationTempBasals.round()
        val cobInfo = IobCobCalculatorPlugin.getPlugin().getCobInfo(false, "SMS COB")
        reply += (MainApp.gs(R.string.sms_iob) + " " + DecimalFormatter.to2Decimal(bolusIob.iob + basalIob.basaliob) + "U ("
                + MainApp.gs(R.string.sms_bolus) + " " + DecimalFormatter.to2Decimal(bolusIob.iob) + "U "
                + MainApp.gs(R.string.sms_basal) + " " + DecimalFormatter.to2Decimal(basalIob.basaliob) + "U), "
                + MainApp.gs(R.string.cob) + ": " + cobInfo.generateCOBString())
        sendSMS(Sms(receivedSms.phoneNumber, reply))
        receivedSms.processed = true
    }

    private fun processLOOP(splitted: Array<String>, receivedSms: Sms) {
        when (splitted[1].toUpperCase(Locale.getDefault())) {
            "DISABLE", "STOP" -> {
                val loopPlugin = LoopPlugin.getPlugin()
                if (loopPlugin.isEnabled(PluginType.LOOP)) {
                    loopPlugin.setPluginEnabled(PluginType.LOOP, false)
                    ConfigBuilderPlugin.getPlugin().commandQueue.cancelTempBasal(true, object : Callback() {
                        override fun run() {
                            send(EventRefreshOverview("SMS_LOOP_STOP"))
                            val replyText = MainApp.gs(R.string.smscommunicator_loophasbeendisabled) + " " +
                                    MainApp.gs(if (result.success) R.string.smscommunicator_tempbasalcanceled else R.string.smscommunicator_tempbasalcancelfailed)
                            sendSMS(Sms(receivedSms.phoneNumber, replyText))
                        }
                    })
                } else
                    sendSMS(Sms(receivedSms.phoneNumber, R.string.smscommunicator_loopisdisabled))
                receivedSms.processed = true
            }
            "ENABLE", "START" -> {
                val loopPlugin = LoopPlugin.getPlugin()
                if (!loopPlugin.isEnabled(PluginType.LOOP)) {
                    loopPlugin.setPluginEnabled(PluginType.LOOP, true)
                    sendSMS(Sms(receivedSms.phoneNumber, R.string.smscommunicator_loophasbeenenabled))
                    send(EventRefreshOverview("SMS_LOOP_START"))
                } else
                    sendSMS(Sms(receivedSms.phoneNumber, R.string.smscommunicator_loopisenabled))
                receivedSms.processed = true
            }
            "STATUS" -> {
                val loopPlugin = LoopPlugin.getPlugin()
                val reply = if (loopPlugin.isEnabled(PluginType.LOOP)) {
                    if (loopPlugin.isSuspended()) String.format(MainApp.gs(R.string.loopsuspendedfor), loopPlugin.minutesToEndOfSuspend())
                    else MainApp.gs(R.string.smscommunicator_loopisenabled)
                } else
                    MainApp.gs(R.string.smscommunicator_loopisdisabled)
                sendSMS(Sms(receivedSms.phoneNumber, reply))
                receivedSms.processed = true
            }
            "RESUME" -> {
                LoopPlugin.getPlugin().suspendTo(0)
                send(EventRefreshOverview("SMS_LOOP_RESUME"))
                LoopPlugin.getPlugin().createOfflineEvent(0)
                sendSMSToAllNumbers(Sms(receivedSms.phoneNumber, R.string.smscommunicator_loopresumed))
            }
            "SUSPEND" -> {
                var duration = 0
                if (splitted.size == 3) duration = SafeParse.stringToInt(splitted[2])
                duration = Math.max(0, duration)
                duration = Math.min(180, duration)
                if (duration == 0) {
                    receivedSms.processed = true
                    sendSMS(Sms(receivedSms.phoneNumber, R.string.smscommunicator_wrongduration))
                    return
                } else {
                    val passCode = generatePasscode()
                    val reply = String.format(MainApp.gs(R.string.smscommunicator_suspendreplywithcode), duration, passCode)
                    receivedSms.processed = true
                    messageToConfirm = AuthRequest(this, receivedSms, reply, passCode, object : SmsAction(duration) {
                        override fun run() {
                            ConfigBuilderPlugin.getPlugin().commandQueue.cancelTempBasal(true, object : Callback() {
                                override fun run() {
                                    if (result.success) {
                                        LoopPlugin.getPlugin().suspendTo(System.currentTimeMillis() + anInteger() * 60L * 1000)
                                        LoopPlugin.getPlugin().createOfflineEvent(anInteger() * 60)
                                        send(EventRefreshOverview("SMS_LOOP_SUSPENDED"))
                                        val replyText = MainApp.gs(R.string.smscommunicator_loopsuspended) + " " +
                                                MainApp.gs(if (result.success) R.string.smscommunicator_tempbasalcanceled else R.string.smscommunicator_tempbasalcancelfailed)
                                        sendSMSToAllNumbers(Sms(receivedSms.phoneNumber, replyText))
                                    } else {
                                        var replyText = MainApp.gs(R.string.smscommunicator_tempbasalcancelfailed)
                                        replyText += "\n" + ConfigBuilderPlugin.getPlugin().activePump?.shortStatus(true)
                                        sendSMS(Sms(receivedSms.phoneNumber, replyText))
                                    }
                                }
                            })
                        }
                    })
                }
            }
            else -> sendSMS(Sms(receivedSms.phoneNumber, R.string.wrongformat))
        }
    }

    private fun processTREATMENTS(splitted: Array<String>, receivedSms: Sms) {
        if (splitted[1].toUpperCase(Locale.getDefault()) == "REFRESH") {
            TreatmentsPlugin.getPlugin().service.resetTreatments()
            send(EventNSClientRestart())
            sendSMS(Sms(receivedSms.phoneNumber, "TREATMENTS REFRESH SENT"))
            receivedSms.processed = true
        } else
            sendSMS(Sms(receivedSms.phoneNumber, R.string.wrongformat))
    }

    private fun processNSCLIENT(splitted: Array<String>, receivedSms: Sms) {
        if (splitted[1].toUpperCase(Locale.getDefault()) == "RESTART") {
            send(EventNSClientRestart())
            sendSMS(Sms(receivedSms.phoneNumber, "NSCLIENT RESTART SENT"))
            receivedSms.processed = true
        } else
            sendSMS(Sms(receivedSms.phoneNumber, R.string.wrongformat))
    }

    private fun processHELP(splitted: Array<String>, receivedSms: Sms) {
        if (splitted.size == 1) {
            sendSMS(Sms(receivedSms.phoneNumber, commands.keys.toString().replace("[", "").replace("]", "")))
            receivedSms.processed = true
        } else if (isCommand(splitted[1].toUpperCase(Locale.getDefault()), receivedSms.phoneNumber)) {
            commands[splitted[1].toUpperCase(Locale.getDefault())]?.let {
                sendSMS(Sms(receivedSms.phoneNumber, it))
                receivedSms.processed = true
            }
        } else
            sendSMS(Sms(receivedSms.phoneNumber, R.string.wrongformat))
    }

    private fun processPUMP(receivedSms: Sms) {
        ConfigBuilderPlugin.getPlugin().commandQueue.readStatus("SMS", object : Callback() {
            override fun run() {
                val pump = ConfigBuilderPlugin.getPlugin().activePump
                if (result.success) {
                    if (pump != null) {
                        val reply = pump.shortStatus(true)
                        sendSMS(Sms(receivedSms.phoneNumber, reply))
                    }
                } else {
                    val reply = MainApp.gs(R.string.readstatusfailed)
                    sendSMS(Sms(receivedSms.phoneNumber, reply))
                }
            }
        })
        receivedSms.processed = true
    }

    private fun processPROFILE(splitted: Array<String>, receivedSms: Sms) { // load profiles
        val anInterface = ConfigBuilderPlugin.getPlugin().activeProfileInterface
        if (anInterface == null) {
            sendSMS(Sms(receivedSms.phoneNumber, R.string.notconfigured))
            receivedSms.processed = true
            return
        }
        val store = anInterface.profile
        if (store == null) {
            sendSMS(Sms(receivedSms.phoneNumber, R.string.notconfigured))
            receivedSms.processed = true
            return
        }
        val list = store.getProfileList()
        if (splitted[1].toUpperCase(Locale.getDefault()) == "STATUS") {
            sendSMS(Sms(receivedSms.phoneNumber, ProfileFunctions.getInstance().profileName))
        } else if (splitted[1].toUpperCase(Locale.getDefault()) == "LIST") {
            if (list.isEmpty()) sendSMS(Sms(receivedSms.phoneNumber, R.string.invalidprofile))
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
            val pindex = SafeParse.stringToInt(splitted[1])
            var percentage = 100
            if (splitted.size > 2) percentage = SafeParse.stringToInt(splitted[2])
            if (pindex > list.size) sendSMS(Sms(receivedSms.phoneNumber, R.string.wrongformat))
            else if (percentage == 0) sendSMS(Sms(receivedSms.phoneNumber, R.string.wrongformat))
            else if (pindex == 0) sendSMS(Sms(receivedSms.phoneNumber, R.string.wrongformat))
            else {
                val profile = store.getSpecificProfile(list[pindex - 1] as String)
                if (profile == null) sendSMS(Sms(receivedSms.phoneNumber, R.string.noprofile))
                else {
                    val passCode = generatePasscode()
                    val reply = String.format(MainApp.gs(R.string.smscommunicator_profilereplywithcode), list[pindex - 1], percentage, passCode)
                    receivedSms.processed = true
                    val finalPercentage = percentage
                    messageToConfirm = AuthRequest(this, receivedSms, reply, passCode, object : SmsAction(list[pindex - 1] as String, finalPercentage) {
                        override fun run() {
                            ProfileFunctions.doProfileSwitch(store, list[pindex - 1] as String, 0, finalPercentage, 0, DateUtil.now())
                            sendSMS(Sms(receivedSms.phoneNumber, R.string.profileswitchcreated))
                        }
                    })
                }
            }
        }
        receivedSms.processed = true
    }

    private fun processBASAL(splitted: Array<String>, receivedSms: Sms) {
        if (splitted[1].toUpperCase(Locale.getDefault()) == "CANCEL" || splitted[1].toUpperCase(Locale.getDefault()) == "STOP") {
            val passCode = generatePasscode()
            val reply = String.format(MainApp.gs(R.string.smscommunicator_basalstopreplywithcode), passCode)
            receivedSms.processed = true
            messageToConfirm = AuthRequest(this, receivedSms, reply, passCode, object : SmsAction() {
                override fun run() {
                    ConfigBuilderPlugin.getPlugin().commandQueue.cancelTempBasal(true, object : Callback() {
                        override fun run() {
                            if (result.success) {
                                var replyText = MainApp.gs(R.string.smscommunicator_tempbasalcanceled)
                                replyText += "\n" + ConfigBuilderPlugin.getPlugin().activePump?.shortStatus(true)
                                sendSMSToAllNumbers(Sms(receivedSms.phoneNumber, replyText))
                            } else {
                                var replyText = MainApp.gs(R.string.smscommunicator_tempbasalcancelfailed)
                                replyText += "\n" + ConfigBuilderPlugin.getPlugin().activePump?.shortStatus(true)
                                sendSMS(Sms(receivedSms.phoneNumber, replyText))
                            }
                        }
                    })
                }
            })
        } else if (splitted[1].endsWith("%")) {
            var tempBasalPct = SafeParse.stringToInt(StringUtils.removeEnd(splitted[1], "%"))
            var duration = 30
            if (splitted.size > 2) duration = SafeParse.stringToInt(splitted[2])
            val profile = ProfileFunctions.getInstance().profile
            if (profile == null) sendSMS(Sms(receivedSms.phoneNumber, R.string.noprofile))
            else if (tempBasalPct == 0 && splitted[1] != "0%") sendSMS(Sms(receivedSms.phoneNumber, R.string.wrongformat))
            else if (duration == 0) sendSMS(Sms(receivedSms.phoneNumber, R.string.wrongformat))
            else {
                tempBasalPct = MainApp.getConstraintChecker().applyBasalPercentConstraints(Constraint(tempBasalPct), profile).value()
                val passCode = generatePasscode()
                val reply = String.format(MainApp.gs(R.string.smscommunicator_basalpctreplywithcode), tempBasalPct, duration, passCode)
                receivedSms.processed = true
                messageToConfirm = AuthRequest(this, receivedSms, reply, passCode, object : SmsAction(tempBasalPct, duration) {
                    override fun run() {
                        ConfigBuilderPlugin.getPlugin().commandQueue.tempBasalPercent(anInteger(), secondInteger(), true, profile, object : Callback() {
                            override fun run() {
                                if (result.success) {
                                    var replyText: String
                                    replyText = if (result.isPercent) String.format(MainApp.gs(R.string.smscommunicator_tempbasalset_percent), result.percent, result.duration) else String.format(MainApp.gs(R.string.smscommunicator_tempbasalset), result.absolute, result.duration)
                                    replyText += "\n" + ConfigBuilderPlugin.getPlugin().activePump?.shortStatus(true)
                                    sendSMSToAllNumbers(Sms(receivedSms.phoneNumber, replyText))
                                } else {
                                    var replyText = MainApp.gs(R.string.smscommunicator_tempbasalfailed)
                                    replyText += "\n" + ConfigBuilderPlugin.getPlugin().activePump?.shortStatus(true)
                                    sendSMS(Sms(receivedSms.phoneNumber, replyText))
                                }
                            }
                        })
                    }
                })
            }
        } else {
            var tempBasal = SafeParse.stringToDouble(splitted[1])
            var duration = 30
            if (splitted.size > 2) duration = SafeParse.stringToInt(splitted[2])
            val profile = ProfileFunctions.getInstance().profile
            if (profile == null) sendSMS(Sms(receivedSms.phoneNumber, R.string.noprofile))
            else if (tempBasal == 0.0 && splitted[1] != "0") sendSMS(Sms(receivedSms.phoneNumber, R.string.wrongformat))
            else if (duration == 0) sendSMS(Sms(receivedSms.phoneNumber, R.string.wrongformat))
            else {
                tempBasal = MainApp.getConstraintChecker().applyBasalConstraints(Constraint(tempBasal), profile).value()
                val passCode = generatePasscode()
                val reply = String.format(MainApp.gs(R.string.smscommunicator_basalreplywithcode), tempBasal, duration, passCode)
                receivedSms.processed = true
                messageToConfirm = AuthRequest(this, receivedSms, reply, passCode, object : SmsAction(tempBasal, duration) {
                    override fun run() {
                        ConfigBuilderPlugin.getPlugin().commandQueue.tempBasalAbsolute(aDouble(), secondInteger(), true, profile, object : Callback() {
                            override fun run() {
                                if (result.success) {
                                    var replyText = if (result.isPercent) String.format(MainApp.gs(R.string.smscommunicator_tempbasalset_percent), result.percent, result.duration)
                                    else String.format(MainApp.gs(R.string.smscommunicator_tempbasalset), result.absolute, result.duration)
                                    replyText += "\n" + ConfigBuilderPlugin.getPlugin().activePump?.shortStatus(true)
                                    sendSMSToAllNumbers(Sms(receivedSms.phoneNumber, replyText))
                                } else {
                                    var replyText = MainApp.gs(R.string.smscommunicator_tempbasalfailed)
                                    replyText += "\n" + ConfigBuilderPlugin.getPlugin().activePump?.shortStatus(true)
                                    sendSMS(Sms(receivedSms.phoneNumber, replyText))
                                }
                            }
                        })
                    }
                })
            }
        }
    }

    private fun processEXTENDED(splitted: Array<String>, receivedSms: Sms) {
        if (splitted[1].toUpperCase(Locale.getDefault()) == "CANCEL" || splitted[1].toUpperCase(Locale.getDefault()) == "STOP") {
            val passCode = generatePasscode()
            val reply = String.format(MainApp.gs(R.string.smscommunicator_extendedstopreplywithcode), passCode)
            receivedSms.processed = true
            messageToConfirm = AuthRequest(this, receivedSms, reply, passCode, object : SmsAction() {
                override fun run() {
                    ConfigBuilderPlugin.getPlugin().commandQueue.cancelExtended(object : Callback() {
                        override fun run() {
                            if (result.success) {
                                var replyText = MainApp.gs(R.string.smscommunicator_extendedcanceled)
                                replyText += "\n" + ConfigBuilderPlugin.getPlugin().activePump?.shortStatus(true)
                                sendSMSToAllNumbers(Sms(receivedSms.phoneNumber, replyText))
                            } else {
                                var replyText = MainApp.gs(R.string.smscommunicator_extendedcancelfailed)
                                replyText += "\n" + ConfigBuilderPlugin.getPlugin().activePump?.shortStatus(true)
                                sendSMS(Sms(receivedSms.phoneNumber, replyText))
                            }
                        }
                    })
                }
            })
        } else if (splitted.size != 3) {
            sendSMS(Sms(receivedSms.phoneNumber, R.string.wrongformat))
        } else {
            var extended = SafeParse.stringToDouble(splitted[1])
            val duration = SafeParse.stringToInt(splitted[2])
            extended = MainApp.getConstraintChecker().applyExtendedBolusConstraints(Constraint(extended)).value()
            if (extended == 0.0 || duration == 0) sendSMS(Sms(receivedSms.phoneNumber, R.string.wrongformat))
            else {
                val passCode = generatePasscode()
                val reply = String.format(MainApp.gs(R.string.smscommunicator_extendedreplywithcode), extended, duration, passCode)
                receivedSms.processed = true
                messageToConfirm = AuthRequest(this, receivedSms, reply, passCode, object : SmsAction(extended, duration) {
                    override fun run() {
                        ConfigBuilderPlugin.getPlugin().commandQueue.extendedBolus(aDouble(), secondInteger(), object : Callback() {
                            override fun run() {
                                if (result.success) {
                                    var replyText = String.format(MainApp.gs(R.string.smscommunicator_extendedset), aDouble, duration)
                                    if (Config.APS) replyText += "\n" + MainApp.gs(R.string.loopsuspended)
                                    replyText += "\n" + ConfigBuilderPlugin.getPlugin().activePump?.shortStatus(true)
                                    sendSMSToAllNumbers(Sms(receivedSms.phoneNumber, replyText))
                                } else {
                                    var replyText = MainApp.gs(R.string.smscommunicator_extendedfailed)
                                    replyText += "\n" + ConfigBuilderPlugin.getPlugin().activePump?.shortStatus(true)
                                    sendSMS(Sms(receivedSms.phoneNumber, replyText))
                                }
                            }
                        })
                    }
                })
            }
        }
    }

    private fun processBOLUS(splitted: Array<String>, receivedSms: Sms) {
        var bolus = SafeParse.stringToDouble(splitted[1])
        val isMeal = splitted.size > 2 && splitted[2].equals("MEAL", ignoreCase = true)
        bolus = MainApp.getConstraintChecker().applyBolusConstraints(Constraint(bolus)).value()
        if (splitted.size == 3 && !isMeal) {
            sendSMS(Sms(receivedSms.phoneNumber, R.string.wrongformat))
        } else if (bolus > 0.0) {
            val passCode = generatePasscode()
            val reply = if (isMeal)
                String.format(MainApp.gs(R.string.smscommunicator_mealbolusreplywithcode), bolus, passCode)
            else
                String.format(MainApp.gs(R.string.smscommunicator_bolusreplywithcode), bolus, passCode)
            receivedSms.processed = true
            messageToConfirm = AuthRequest(this, receivedSms, reply, passCode, object : SmsAction(bolus) {
                override fun run() {
                    val detailedBolusInfo = DetailedBolusInfo()
                    detailedBolusInfo.insulin = aDouble()
                    detailedBolusInfo.source = Source.USER
                    ConfigBuilderPlugin.getPlugin().commandQueue.bolus(detailedBolusInfo, object : Callback() {
                        override fun run() {
                            val resultSuccess = result.success
                            val resultBolusDelivered = result.bolusDelivered
                            ConfigBuilderPlugin.getPlugin().commandQueue.readStatus("SMS", object : Callback() {
                                override fun run() {
                                    if (resultSuccess) {
                                        var replyText = if (isMeal)
                                            String.format(MainApp.gs(R.string.smscommunicator_mealbolusdelivered), resultBolusDelivered)
                                        else
                                            String.format(MainApp.gs(R.string.smscommunicator_bolusdelivered), resultBolusDelivered)
                                        replyText += "\n" + ConfigBuilderPlugin.getPlugin().activePump?.shortStatus(true)
                                        lastRemoteBolusTime = DateUtil.now()
                                        if (isMeal) {
                                            ProfileFunctions.getInstance().profile?.let { currentProfile ->
                                                var eatingSoonTTDuration = SP.getInt(R.string.key_eatingsoon_duration, Constants.defaultEatingSoonTTDuration)
                                                eatingSoonTTDuration =
                                                        if (eatingSoonTTDuration > 0) eatingSoonTTDuration
                                                        else Constants.defaultEatingSoonTTDuration
                                                var eatingSoonTT = SP.getDouble(R.string.key_eatingsoon_target, if (currentProfile.units == Constants.MMOL) Constants.defaultEatingSoonTTmmol else Constants.defaultEatingSoonTTmgdl)
                                                eatingSoonTT =
                                                        if (eatingSoonTT > 0) eatingSoonTT
                                                        else if (currentProfile.units == Constants.MMOL) Constants.defaultEatingSoonTTmmol
                                                        else Constants.defaultEatingSoonTTmgdl
                                                val tempTarget = TempTarget()
                                                        .date(System.currentTimeMillis())
                                                        .duration(eatingSoonTTDuration)
                                                        .reason(MainApp.gs(R.string.eatingsoon))
                                                        .source(Source.USER)
                                                        .low(Profile.toMgdl(eatingSoonTT, currentProfile.units))
                                                        .high(Profile.toMgdl(eatingSoonTT, currentProfile.units))
                                                TreatmentsPlugin.getPlugin().addToHistoryTempTarget(tempTarget)
                                                val tt = if (currentProfile.units == Constants.MMOL) {
                                                    DecimalFormatter.to1Decimal(eatingSoonTT)
                                                } else DecimalFormatter.to0Decimal(eatingSoonTT)
                                                replyText += "\n" + String.format(MainApp.gs(R.string.smscommunicator_mealbolusdelivered_tt), tt, eatingSoonTTDuration)
                                            }
                                        }
                                        sendSMSToAllNumbers(Sms(receivedSms.phoneNumber, replyText))
                                    } else {
                                        var replyText = MainApp.gs(R.string.smscommunicator_bolusfailed)
                                        replyText += "\n" + ConfigBuilderPlugin.getPlugin().activePump?.shortStatus(true)
                                        sendSMS(Sms(receivedSms.phoneNumber, replyText))
                                    }
                                }
                            })
                        }
                    })
                }
            })
        } else sendSMS(Sms(receivedSms.phoneNumber, R.string.wrongformat))
    }

    private fun processCARBS(splitted: Array<String>, receivedSms: Sms) {
        var grams = SafeParse.stringToInt(splitted[1])
        var time = DateUtil.now()
        if (splitted.size > 2) {
            val seconds = DateUtil.toSeconds(splitted[2].toUpperCase(Locale.getDefault()))
            val midnight = MidnightTime.calc()
            if (seconds == 0 && (!splitted[2].startsWith("00:00") || !splitted[2].startsWith("12:00"))) {
                sendSMS(Sms(receivedSms.phoneNumber, R.string.wrongformat))
                return
            }
            time = midnight + T.secs(seconds.toLong()).msecs()
        }
        grams = MainApp.getConstraintChecker().applyCarbsConstraints(Constraint(grams)).value()
        if (grams == 0) sendSMS(Sms(receivedSms.phoneNumber, R.string.wrongformat))
        else {
            val passCode = generatePasscode()
            val reply = String.format(MainApp.gs(R.string.smscommunicator_carbsreplywithcode), grams, DateUtil.timeString(time), passCode)
            receivedSms.processed = true
            messageToConfirm = AuthRequest(this, receivedSms, reply, passCode, object : SmsAction(grams, time) {
                override fun run() {
                    val detailedBolusInfo = DetailedBolusInfo()
                    detailedBolusInfo.carbs = anInteger().toDouble()
                    detailedBolusInfo.date = secondLong()
                    ConfigBuilderPlugin.getPlugin().commandQueue.bolus(detailedBolusInfo, object : Callback() {
                        override fun run() {
                            if (result.success) {
                                var replyText = String.format(MainApp.gs(R.string.smscommunicator_carbsset), anInteger)
                                replyText += "\n" + ConfigBuilderPlugin.getPlugin().activePump?.shortStatus(true)
                                sendSMSToAllNumbers(Sms(receivedSms.phoneNumber, replyText))
                            } else {
                                var replyText = MainApp.gs(R.string.smscommunicator_carbsfailed)
                                replyText += "\n" + ConfigBuilderPlugin.getPlugin().activePump?.shortStatus(true)
                                sendSMS(Sms(receivedSms.phoneNumber, replyText))
                            }
                        }
                    })
                }
            })
        }
    }

    private fun processTARGET(splitted: Array<String>, receivedSms: Sms) {
        val isMeal = splitted[1].equals("MEAL", ignoreCase = true)
        val isActivity = splitted[1].equals("ACTIVITY", ignoreCase = true)
        val isHypo = splitted[1].equals("HYPO", ignoreCase = true)
        val isStop = splitted[1].equals("STOP", ignoreCase = true) || splitted[1].equals("CANCEL", ignoreCase = true)
        if (isMeal || isActivity || isHypo) {
            val passCode = generatePasscode()
            val reply = String.format(MainApp.gs(R.string.smscommunicator_temptargetwithcode), splitted[1].toUpperCase(Locale.getDefault()), passCode)
            receivedSms.processed = true
            messageToConfirm = AuthRequest(this, receivedSms, reply, passCode, object : SmsAction() {
                override fun run() {
                    val units = ProfileFunctions.getSystemUnits()
                    var keyDuration = 0
                    var defaultTargetDuration = 0
                    var keyTarget = 0
                    var defaultTargetMMOL = 0.0
                    var defaultTargetMGDL = 0.0
                    if (isMeal) {
                        keyDuration = R.string.key_eatingsoon_duration
                        defaultTargetDuration = Constants.defaultEatingSoonTTDuration
                        keyTarget = R.string.key_eatingsoon_target
                        defaultTargetMMOL = Constants.defaultEatingSoonTTmmol
                        defaultTargetMGDL = Constants.defaultEatingSoonTTmgdl
                    } else if (isActivity) {
                        keyDuration = R.string.key_activity_duration
                        defaultTargetDuration = Constants.defaultActivityTTDuration
                        keyTarget = R.string.key_activity_target
                        defaultTargetMMOL = Constants.defaultActivityTTmmol
                        defaultTargetMGDL = Constants.defaultActivityTTmgdl
                    } else if (isHypo) {
                        keyDuration = R.string.key_hypo_duration
                        defaultTargetDuration = Constants.defaultHypoTTDuration
                        keyTarget = R.string.key_hypo_target
                        defaultTargetMMOL = Constants.defaultHypoTTmmol
                        defaultTargetMGDL = Constants.defaultHypoTTmgdl
                    }
                    var ttDuration = SP.getInt(keyDuration, defaultTargetDuration)
                    ttDuration = if (ttDuration > 0) ttDuration else defaultTargetDuration
                    var tt = SP.getDouble(keyTarget, if (units == Constants.MMOL) defaultTargetMMOL else defaultTargetMGDL)
                    tt = Profile.toCurrentUnits(tt)
                    tt = if (tt > 0) tt else if (units == Constants.MMOL) defaultTargetMMOL else defaultTargetMGDL
                    val tempTarget = TempTarget()
                            .date(System.currentTimeMillis())
                            .duration(ttDuration)
                            .reason(MainApp.gs(R.string.eatingsoon))
                            .source(Source.USER)
                            .low(Profile.toMgdl(tt, units))
                            .high(Profile.toMgdl(tt, units))
                    TreatmentsPlugin.getPlugin().addToHistoryTempTarget(tempTarget)
                    val ttString = if (units == Constants.MMOL) DecimalFormatter.to1Decimal(tt) else DecimalFormatter.to0Decimal(tt)
                    val replyText = String.format(MainApp.gs(R.string.smscommunicator_tt_set), ttString, ttDuration)
                    sendSMSToAllNumbers(Sms(receivedSms.phoneNumber, replyText))
                }
            })
        } else if (isStop) {
            val passCode = generatePasscode()
            val reply = String.format(MainApp.gs(R.string.smscommunicator_temptargetcancel), passCode)
            receivedSms.processed = true
            messageToConfirm = AuthRequest(this, receivedSms, reply, passCode, object : SmsAction() {
                override fun run() {
                    val tempTarget = TempTarget()
                            .source(Source.USER)
                            .date(DateUtil.now())
                            .duration(0)
                            .low(0.0)
                            .high(0.0)
                    TreatmentsPlugin.getPlugin().addToHistoryTempTarget(tempTarget)
                    val replyText = String.format(MainApp.gs(R.string.smscommunicator_tt_canceled))
                    sendSMSToAllNumbers(Sms(receivedSms.phoneNumber, replyText))
                }
            })
        } else
            sendSMS(Sms(receivedSms.phoneNumber, R.string.wrongformat))
    }

    private fun processSMS(splitted: Array<String>, receivedSms: Sms) {
        val isStop = (splitted[1].equals("STOP", ignoreCase = true)
                || splitted[1].equals("DISABLE", ignoreCase = true))
        if (isStop) {
            val passCode = generatePasscode()
            val reply = String.format(MainApp.gs(R.string.smscommunicator_stopsmswithcode), passCode)
            receivedSms.processed = true
            messageToConfirm = AuthRequest(this, receivedSms, reply, passCode, object : SmsAction() {
                override fun run() {
                    SP.putBoolean(R.string.key_smscommunicator_remotecommandsallowed, false)
                    val replyText = String.format(MainApp.gs(R.string.smscommunicator_stoppedsms))
                    sendSMSToAllNumbers(Sms(receivedSms.phoneNumber, replyText))
                }
            })
        } else sendSMS(Sms(receivedSms.phoneNumber, R.string.wrongformat))
    }

    private fun processCAL(splitted: Array<String>, receivedSms: Sms) {
        val cal = SafeParse.stringToDouble(splitted[1])
        if (cal > 0.0) {
            val passCode = generatePasscode()
            val reply = String.format(MainApp.gs(R.string.smscommunicator_calibrationreplywithcode), cal, passCode)
            receivedSms.processed = true
            messageToConfirm = AuthRequest(this, receivedSms, reply, passCode, object : SmsAction(cal) {
                override fun run() {
                    val result = XdripCalibrations.sendIntent(aDouble)
                    if (result) sendSMSToAllNumbers(Sms(receivedSms.phoneNumber, R.string.smscommunicator_calibrationsent)) else sendSMS(Sms(receivedSms.phoneNumber, R.string.smscommunicator_calibrationfailed))
                }
            })
        } else sendSMS(Sms(receivedSms.phoneNumber, R.string.wrongformat))
    }

    fun sendNotificationToAllNumbers(text: String): Boolean {
        var result = true
        for (i in allowedNumbers.indices) {
            val sms = Sms(allowedNumbers[i], text)
            result = result && sendSMS(sms)
        }
        return result
    }

    private fun sendSMSToAllNumbers(sms: Sms) {
        for (number in allowedNumbers) {
            sms.phoneNumber = number
            sendSMS(sms)
        }
    }

    fun sendSMS(sms: Sms): Boolean {
        val smsManager = SmsManager.getDefault()
        sms.text = stripAccents(sms.text)
        try {
            if (L.isEnabled(L.SMS)) log.debug("Sending SMS to " + sms.phoneNumber + ": " + sms.text)
            if (sms.text.toByteArray().size <= 140) smsManager.sendTextMessage(sms.phoneNumber, null, sms.text, null, null)
            else {
                val parts = smsManager.divideMessage(sms.text)
                smsManager.sendMultipartTextMessage(sms.phoneNumber, null, parts,
                        null, null)
            }
            messages.add(sms)
        } catch (e: IllegalArgumentException) {
            return if (e.message == "Invalid message body") {
                val notification = Notification(Notification.INVALID_MESSAGE_BODY, MainApp.gs(R.string.smscommunicator_messagebody), Notification.NORMAL)
                send(EventNewNotification(notification))
                false
            } else {
                val notification = Notification(Notification.INVALID_PHONE_NUMBER, MainApp.gs(R.string.smscommunicator_invalidphonennumber), Notification.NORMAL)
                send(EventNewNotification(notification))
                false
            }
        } catch (e: SecurityException) {
            val notification = Notification(Notification.MISSING_SMS_PERMISSION, MainApp.gs(R.string.smscommunicator_missingsmspermission), Notification.NORMAL)
            send(EventNewNotification(notification))
            return false
        }
        send(EventSmsCommunicatorUpdateGui())
        return true
    }

    private fun generatePasscode(): String {
        val startChar1 = 'A'.toInt() // on iphone 1st char is uppercase :)
        var passCode = Character.toString((startChar1 + Math.random() * ('z' - 'a' + 1)).toChar())
        val startChar2: Int = if (Math.random() > 0.5) 'a'.toInt() else 'A'.toInt()
        passCode += Character.toString((startChar2 + Math.random() * ('z' - 'a' + 1)).toChar())
        val startChar3: Int = if (Math.random() > 0.5) 'a'.toInt() else 'A'.toInt()
        passCode += Character.toString((startChar3 + Math.random() * ('z' - 'a' + 1)).toChar())
        passCode = passCode.replace('l', 'k').replace('I', 'J')
        return passCode
    }

    private fun stripAccents(str: String): String {
        var s = str
        s = Normalizer.normalize(s, Normalizer.Form.NFD)
        s = s.replace("[\\p{InCombiningDiacriticalMarks}]".toRegex(), "")
        return s
    }

    fun areMoreNumbers(allowednumbers: String?): Boolean {
        return allowednumbers?.let {
            var countNumbers = 0
            val substrings = it.split(";").toTypedArray()
            for (number in substrings) {
                var cleaned = number.replace(Regex("\\s+"), "")
                if (cleaned.length < 4) continue
                cleaned = cleaned.replace("+", "")
                cleaned = cleaned.replace("-", "")
                if (!cleaned.matches(Regex("[0-9]+"))) continue
                countNumbers++
            }
            countNumbers > 1
        } ?: false
    }
}