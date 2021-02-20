package info.nightscout.androidaps.plugins.general.actions

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import dagger.android.support.DaggerFragment
import info.nightscout.androidaps.Config
import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.R
import info.nightscout.androidaps.activities.ErrorHelperActivity
import info.nightscout.androidaps.activities.TDDStatsActivity
import info.nightscout.androidaps.dialogs.*
import info.nightscout.androidaps.events.*
import info.nightscout.androidaps.historyBrowser.HistoryBrowseActivity
import info.nightscout.androidaps.interfaces.ActivePluginProvider
import info.nightscout.androidaps.interfaces.CommandQueueProvider
import info.nightscout.androidaps.interfaces.ProfileFunction
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.UserEntryLogger
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.general.actions.defs.CustomAction
import info.nightscout.androidaps.plugins.general.overview.StatusLightHandler
import info.nightscout.androidaps.plugins.pump.omnipod.eros.OmnipodErosPumpPlugin
import info.nightscout.androidaps.queue.Callback
import info.nightscout.androidaps.skins.SkinProvider
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.alertDialogs.OKDialog
import info.nightscout.androidaps.utils.buildHelper.BuildHelper
import info.nightscout.androidaps.utils.extensions.toVisibility
import info.nightscout.androidaps.utils.protection.ProtectionCheck
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.rx.AapsSchedulers
import info.nightscout.androidaps.utils.sharedPreferences.SP
import info.nightscout.androidaps.utils.ui.SingleClickButton
import info.nightscout.androidaps.utils.ui.UIRunnable
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.plusAssign
import java.util.*
import javax.inject.Inject

class ActionsFragment : DaggerFragment() {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var rxBus: RxBusWrapper
    @Inject lateinit var sp: SP
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var ctx: Context
    @Inject lateinit var resourceHelper: ResourceHelper
    @Inject lateinit var statusLightHandler: StatusLightHandler
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var activePlugin: ActivePluginProvider
    @Inject lateinit var commandQueue: CommandQueueProvider
    @Inject lateinit var buildHelper: BuildHelper
    @Inject lateinit var protectionCheck: ProtectionCheck
    @Inject lateinit var skinProvider: SkinProvider
    @Inject lateinit var config: Config
    @Inject lateinit var uel: UserEntryLogger

    private var disposable: CompositeDisposable = CompositeDisposable()

    private val pumpCustomActions = HashMap<String, CustomAction>()
    private val pumpCustomButtons = ArrayList<SingleClickButton>()
    private var smallWidth = false
    private var smallHeight = false
    private lateinit var dm: DisplayMetrics

    private var buttonsLayout: LinearLayout? = null
    private var profileSwitch: SingleClickButton? = null
    private var tempTarget: SingleClickButton? = null
    private var extendedBolus: SingleClickButton? = null
    private var extendedBolusCancel: SingleClickButton? = null
    private var setTempBasal: SingleClickButton? = null
    private var cancelTempBasal: SingleClickButton? = null
    private var fill: SingleClickButton? = null
    private var historyBrowser: SingleClickButton? = null
    private var tddStats: SingleClickButton? = null
    private var pumpBatteryChange: SingleClickButton? = null

    private var cannulaAge: TextView? = null
    private var insulinAge: TextView? = null
    private var reservoirLevel: TextView? = null
    private var sensorAge: TextView? = null
    private var sensorLevel: TextView? = null
    private var pbAge: TextView? = null
    private var batteryLevel: TextView? = null
    private var sensorLevelLabel: TextView? = null
    private var insulinLevelLabel: TextView? = null
    private var pbLevelLabel: TextView? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        //check screen width
        dm = DisplayMetrics()
        activity?.windowManager?.defaultDisplay?.getMetrics(dm)

        val screenWidth = dm.widthPixels
        val screenHeight = dm.heightPixels
        smallWidth = screenWidth <= Constants.SMALL_WIDTH
        smallHeight = screenHeight <= Constants.SMALL_HEIGHT
        val landscape = screenHeight < screenWidth

        return inflater.inflate(skinProvider.activeSkin().actionsLayout(landscape, smallWidth), container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        buttonsLayout = view.findViewById(R.id.action_buttons_layout)
        profileSwitch = view.findViewById(R.id.actions_profileswitch)
        tempTarget = view.findViewById(R.id.actions_temptarget)
        extendedBolus = view.findViewById(R.id.actions_extendedbolus)
        extendedBolusCancel = view.findViewById(R.id.actions_extendedbolus_cancel)
        setTempBasal = view.findViewById(R.id.actions_settempbasal)
        cancelTempBasal = view.findViewById(R.id.actions_canceltempbasal)
        fill = view.findViewById(R.id.actions_fill)
        historyBrowser = view.findViewById(R.id.actions_historybrowser)
        tddStats = view.findViewById(R.id.actions_tddstats)
        pumpBatteryChange = view.findViewById(R.id.actions_pumpbatterychange)

        cannulaAge = view.findViewById(R.id.cannula_age)
        insulinAge = view.findViewById(R.id.insulin_age)
        reservoirLevel = view.findViewById(R.id.reservoir_level)
        sensorAge = view.findViewById(R.id.sensor_age)
        sensorLevel = view.findViewById(R.id.sensor_level)
        pbAge = view.findViewById(R.id.pb_age)
        batteryLevel = view.findViewById(R.id.battery_level)
        sensorLevelLabel = view.findViewById(R.id.sensor_level_label)
        insulinLevelLabel = view.findViewById(R.id.insulin_level_label)
        pbLevelLabel = view.findViewById(R.id.pb_level_label)

        profileSwitch?.setOnClickListener {
            ProfileSwitchDialog().show(childFragmentManager, "Actions")
        }
        tempTarget?.setOnClickListener {
            TempTargetDialog().show(childFragmentManager, "Actions")
        }
        extendedBolus?.setOnClickListener {
            activity?.let { activity ->
                protectionCheck.queryProtection(activity, ProtectionCheck.Protection.BOLUS, UIRunnable {
                    OKDialog.showConfirmation(activity, resourceHelper.gs(R.string.extended_bolus), resourceHelper.gs(R.string.ebstopsloop),
                        Runnable {
                            ExtendedBolusDialog().show(childFragmentManager, "Actions")
                        }, null)
                })
            }
        }
        extendedBolusCancel?.setOnClickListener {
            if (activePlugin.activeTreatments.isInHistoryExtendedBoluslInProgress) {
                uel.log("CANCEL EXTENDED BOLUS")
                commandQueue.cancelExtended(object : Callback() {
                    override fun run() {
                        if (!result.success) {
                            ErrorHelperActivity.runAlarm(ctx, result.comment, resourceHelper.gs(R.string.extendedbolusdeliveryerror), R.raw.boluserror)
                        }
                    }
                })
            }
        }
        setTempBasal?.setOnClickListener {
            TempBasalDialog().show(childFragmentManager, "Actions")
        }
        cancelTempBasal?.setOnClickListener {
            if (activePlugin.activeTreatments.isTempBasalInProgress) {
                uel.log("CANCEL TEMP BASAL")
                commandQueue.cancelTempBasal(true, object : Callback() {
                    override fun run() {
                        if (!result.success) {
                            ErrorHelperActivity.runAlarm(ctx, result.comment, resourceHelper.gs(R.string.tempbasaldeliveryerror), R.raw.boluserror)
                        }
                    }
                })
            }
        }
        fill?.setOnClickListener {
            activity?.let { activity ->
                protectionCheck.queryProtection(activity, ProtectionCheck.Protection.BOLUS, UIRunnable { FillDialog().show(childFragmentManager, "FillDialog") })
            }
        }
        historyBrowser?.setOnClickListener { startActivity(Intent(context, HistoryBrowseActivity::class.java)) }
        tddStats?.setOnClickListener { startActivity(Intent(context, TDDStatsActivity::class.java)) }
        view.findViewById<SingleClickButton>(R.id.actions_bgcheck).setOnClickListener {
            CareDialog().setOptions(CareDialog.EventType.BGCHECK, R.string.careportal_bgcheck).show(childFragmentManager, "Actions")
        }
        view.findViewById<SingleClickButton>(R.id.actions_cgmsensorinsert).setOnClickListener {
            CareDialog().setOptions(CareDialog.EventType.SENSOR_INSERT, R.string.careportal_cgmsensorinsert).show(childFragmentManager, "Actions")
        }
        pumpBatteryChange?.setOnClickListener {
            CareDialog().setOptions(CareDialog.EventType.BATTERY_CHANGE, R.string.careportal_pumpbatterychange).show(childFragmentManager, "Actions")
        }
        view.findViewById<SingleClickButton>(R.id.actions_note).setOnClickListener {
            CareDialog().setOptions(CareDialog.EventType.NOTE, R.string.careportal_note).show(childFragmentManager, "Actions")
        }
        view.findViewById<SingleClickButton>(R.id.actions_exercise).setOnClickListener {
            CareDialog().setOptions(CareDialog.EventType.EXERCISE, R.string.careportal_exercise).show(childFragmentManager, "Actions")
        }
        view.findViewById<SingleClickButton>(R.id.actions_question).setOnClickListener {
            CareDialog().setOptions(CareDialog.EventType.QUESTION, R.string.careportal_question).show(childFragmentManager, "Actions")
        }
        view.findViewById<SingleClickButton>(R.id.actions_announcement).setOnClickListener {
            CareDialog().setOptions(CareDialog.EventType.ANNOUNCEMENT, R.string.careportal_announcement).show(childFragmentManager, "Actions")
        }

        sp.putBoolean(R.string.key_objectiveuseactions, true)
    }

    @Synchronized
    override fun onResume() {
        super.onResume()
        disposable += rxBus
            .toObservable(EventInitializationChanged::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({ updateGui() }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventRefreshOverview::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({ updateGui() }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventExtendedBolusChange::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({ updateGui() }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventTempBasalChange::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({ updateGui() }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventCustomActionsChanged::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({ updateGui() }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventCareportalEventChange::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({ updateGui() }, fabricPrivacy::logException)
        updateGui()
    }

    @Synchronized
    override fun onPause() {
        super.onPause()
        disposable.clear()
    }

    @Synchronized
    fun updateGui() {

        val profile = profileFunction.getProfile()
        val pump = activePlugin.activePump

        profileSwitch?.visibility = (
            activePlugin.activeProfileInterface.profile != null &&
                pump.pumpDescription.isSetBasalProfileCapable &&
                pump.isInitialized() &&
                !pump.isSuspended()).toVisibility()

        if (!pump.pumpDescription.isExtendedBolusCapable || !pump.isInitialized() || pump.isSuspended() || pump.isFakingTempsByExtendedBoluses) {
            extendedBolus?.visibility = View.GONE
            extendedBolusCancel?.visibility = View.GONE
        } else {
            val activeExtendedBolus = activePlugin.activeTreatments.getExtendedBolusFromHistory(System.currentTimeMillis())
            if (activeExtendedBolus != null) {
                extendedBolus?.visibility = View.GONE
                extendedBolusCancel?.visibility = View.VISIBLE
                @Suppress("SetTextI18n")
                extendedBolusCancel?.text = resourceHelper.gs(R.string.cancel) + " " + activeExtendedBolus.toStringMedium()
            } else {
                extendedBolus?.visibility = View.VISIBLE
                extendedBolusCancel?.visibility = View.GONE
            }
        }

        if (!pump.pumpDescription.isTempBasalCapable || !pump.isInitialized() || pump.isSuspended()) {
            setTempBasal?.visibility = View.GONE
            cancelTempBasal?.visibility = View.GONE
        } else {
            val activeTemp = activePlugin.activeTreatments.getTempBasalFromHistory(System.currentTimeMillis())
            if (activeTemp != null) {
                setTempBasal?.visibility = View.GONE
                cancelTempBasal?.visibility = View.VISIBLE
                @Suppress("SetTextI18n")
                cancelTempBasal?.text = resourceHelper.gs(R.string.cancel) + " " + activeTemp.toStringShort()
            } else {
                setTempBasal?.visibility = View.VISIBLE
                cancelTempBasal?.visibility = View.GONE
            }
        }
        val activeBgSource = activePlugin.activeBgSource
        historyBrowser?.visibility = (profile != null).toVisibility()
        fill?.visibility = (pump.pumpDescription.isRefillingCapable && pump.isInitialized() && !pump.isSuspended()).toVisibility()
        pumpBatteryChange?.visibility = (pump.pumpDescription.isBatteryReplaceable || (pump is OmnipodErosPumpPlugin && pump.isUseRileyLinkBatteryLevel && pump.isBatteryChangeLoggingEnabled)).toVisibility()
        tempTarget?.visibility = (profile != null && config.APS).toVisibility()
        tddStats?.visibility = pump.pumpDescription.supportsTDDs.toVisibility()

        if (!config.NSCLIENT) {
            statusLightHandler.updateStatusLights(cannulaAge, insulinAge, reservoirLevel, sensorAge, sensorLevel, pbAge, batteryLevel)
            sensorLevelLabel?.text = if (activeBgSource.sensorBatteryLevel == -1) "" else resourceHelper.gs(R.string.careportal_level_label)
        } else {
            statusLightHandler.updateStatusLights(cannulaAge, insulinAge, null, sensorAge, null, pbAge, null)
            sensorLevelLabel?.text = ""
            insulinLevelLabel?.text = ""
            pbLevelLabel?.text = ""
        }
        checkPumpCustomActions()

    }

    private fun checkPumpCustomActions() {
        val activePump = activePlugin.activePump
        val customActions = activePump.getCustomActions() ?: return
        val currentContext = context ?: return
        removePumpCustomActions()

        for (customAction in customActions) {
            if (!customAction.isEnabled) continue

            val btn = SingleClickButton(currentContext, null, android.R.attr.buttonStyle)
            btn.text = resourceHelper.gs(customAction.name)

            val layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 0.5f)
            layoutParams.setMargins(20, 8, 20, 8) // 10,3,10,3

            btn.layoutParams = layoutParams
            btn.setOnClickListener { v ->
                val b = v as SingleClickButton
                this.pumpCustomActions[b.text.toString()]?.let {
                    activePlugin.activePump.executeCustomAction(it.customActionType)
                }
            }
            val top = activity?.let { ContextCompat.getDrawable(it, customAction.iconResourceId) }
            btn.setCompoundDrawablesWithIntrinsicBounds(null, top, null, null)

            buttonsLayout?.addView(btn)

            this.pumpCustomActions[resourceHelper.gs(customAction.name)] = customAction
            this.pumpCustomButtons.add(btn)
        }
    }

    private fun removePumpCustomActions() {
        for (customButton in pumpCustomButtons) buttonsLayout?.removeView(customButton)
        pumpCustomButtons.clear()
    }
}