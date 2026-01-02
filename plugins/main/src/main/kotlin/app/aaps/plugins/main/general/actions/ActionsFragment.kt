package app.aaps.plugins.main.general.actions

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import app.aaps.core.data.model.RM
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.db.ProcessedTbrEbData
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.protection.ProtectionCheck
import app.aaps.core.interfaces.pump.actions.CustomAction
import app.aaps.core.interfaces.queue.Callback
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventCustomActionsChanged
import app.aaps.core.interfaces.rx.events.EventExtendedBolusChange
import app.aaps.core.interfaces.rx.events.EventInitializationChanged
import app.aaps.core.interfaces.rx.events.EventTempBasalChange
import app.aaps.core.interfaces.rx.events.EventTherapyEventChange
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.BooleanNonKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.extensions.toStringMedium
import app.aaps.core.objects.extensions.toStringShort
import app.aaps.core.ui.UIRunnable
import app.aaps.core.ui.dialogs.OKDialog
import app.aaps.core.ui.elements.SingleClickButton
import app.aaps.core.ui.extensions.toVisibility
import app.aaps.plugins.main.R
import app.aaps.plugins.main.databinding.ActionsFragmentBinding
import app.aaps.plugins.main.general.overview.ui.StatusLightHandler
import app.aaps.plugins.main.skins.SkinProvider
import dagger.android.support.DaggerFragment
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import javax.inject.Inject

class ActionsFragment : DaggerFragment() {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var preferences: Preferences
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var decimalFormatter: DecimalFormatter
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var statusLightHandler: StatusLightHandler
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var iobCobCalculator: IobCobCalculator
    @Inject lateinit var processedTbrEbData: ProcessedTbrEbData
    @Inject lateinit var commandQueue: CommandQueue
    @Inject lateinit var config: Config
    @Inject lateinit var protectionCheck: ProtectionCheck
    @Inject lateinit var skinProvider: SkinProvider
    @Inject lateinit var uel: UserEntryLogger
    @Inject lateinit var persistenceLayer: PersistenceLayer
    @Inject lateinit var loop: Loop
    @Inject lateinit var uiInteraction: UiInteraction

    private var disposable: CompositeDisposable = CompositeDisposable()

    private val pumpCustomActions = HashMap<String, CustomAction>()
    private val pumpCustomButtons = ArrayList<SingleClickButton>()

    private var _binding: ActionsFragmentBinding? = null

    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        ActionsFragmentBinding.inflate(inflater, container, false).also { _binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val screenWidth = activity?.window?.decorView?.width ?: 0
        val screenHeight = activity?.window?.decorView?.height ?: 0
        val isLandscape = screenHeight < screenWidth
        skinProvider.activeSkin().preProcessLandscapeActionsLayout(isLandscape, binding)

        binding.profileSwitch.setOnClickListener {
            activity?.let { activity ->
                protectionCheck.queryProtection(
                    activity,
                    ProtectionCheck.Protection.BOLUS,
                    UIRunnable { uiInteraction.runProfileSwitchDialog(childFragmentManager) })
            }
        }
        binding.tempTarget.setOnClickListener {
            activity?.let { activity ->
                protectionCheck.queryProtection(
                    activity,
                    ProtectionCheck.Protection.BOLUS,
                    UIRunnable { uiInteraction.runTempTargetDialog(childFragmentManager) })
            }
        }
        binding.extendedBolus.setOnClickListener {
            activity?.let { activity ->
                protectionCheck.queryProtection(activity, ProtectionCheck.Protection.BOLUS, UIRunnable {
                    OKDialog.showConfirmation(
                        activity, rh.gs(app.aaps.core.ui.R.string.extended_bolus), rh.gs(R.string.ebstopsloop),
                        {
                            uiInteraction.runExtendedBolusDialog(childFragmentManager)
                        }, null
                    )
                })
            }
        }
        binding.extendedBolusCancel.setOnClickListener {
            if (persistenceLayer.getExtendedBolusActiveAt(dateUtil.now()) != null) {
                uel.log(Action.CANCEL_EXTENDED_BOLUS, Sources.Actions)
                commandQueue.cancelExtended(object : Callback() {
                    override fun run() {
                        if (!result.success) {
                            uiInteraction.runAlarm(result.comment, rh.gs(app.aaps.core.ui.R.string.extendedbolusdeliveryerror), app.aaps.core.ui.R.raw.boluserror)
                        }
                    }
                })
            }
        }
        binding.setTempBasal.setOnClickListener {
            activity?.let { activity ->
                protectionCheck.queryProtection(
                    activity,
                    ProtectionCheck.Protection.BOLUS,
                    UIRunnable { uiInteraction.runTempBasalDialog(childFragmentManager) })
            }
        }
        binding.cancelTempBasal.setOnClickListener {
            if (processedTbrEbData.getTempBasalIncludingConvertedExtended(dateUtil.now()) != null) {
                uel.log(Action.CANCEL_TEMP_BASAL, Sources.Actions)
                commandQueue.cancelTempBasal(enforceNew = true, callback = object : Callback() {
                    override fun run() {
                        if (!result.success) {
                            uiInteraction.runAlarm(result.comment, rh.gs(app.aaps.core.ui.R.string.temp_basal_delivery_error), app.aaps.core.ui.R.raw.boluserror)
                        }
                    }
                })
            }
        }
        binding.fill.setOnClickListener {
            activity?.let { activity ->
                protectionCheck.queryProtection(activity, ProtectionCheck.Protection.BOLUS, UIRunnable { uiInteraction.runFillDialog(childFragmentManager) })
            }
        }
        binding.historyBrowser.setOnClickListener { startActivity(Intent(context, uiInteraction.historyBrowseActivity)) }
        binding.tddStats.setOnClickListener { startActivity(Intent(context, uiInteraction.tddStatsActivity)) }
        binding.bgCheck.setOnClickListener {
            uiInteraction.runCareDialog(childFragmentManager, UiInteraction.EventType.BGCHECK, app.aaps.core.ui.R.string.careportal_bgcheck)
        }
        binding.cgmSensorInsert.setOnClickListener {
            uiInteraction.runCareDialog(childFragmentManager, UiInteraction.EventType.SENSOR_INSERT, app.aaps.core.ui.R.string.cgm_sensor_insert)
        }
        binding.pumpBatteryChange.setOnClickListener {
            uiInteraction.runCareDialog(childFragmentManager, UiInteraction.EventType.BATTERY_CHANGE, app.aaps.core.ui.R.string.pump_battery_change)
        }
        binding.note.setOnClickListener {
            uiInteraction.runCareDialog(childFragmentManager, UiInteraction.EventType.NOTE, app.aaps.core.ui.R.string.careportal_note)
        }
        binding.exercise.setOnClickListener {
            uiInteraction.runCareDialog(childFragmentManager, UiInteraction.EventType.EXERCISE, app.aaps.core.ui.R.string.careportal_exercise)
        }
        binding.question.setOnClickListener {
            uiInteraction.runCareDialog(childFragmentManager, UiInteraction.EventType.QUESTION, app.aaps.core.ui.R.string.careportal_question)
        }
        binding.announcement.setOnClickListener {
            uiInteraction.runCareDialog(childFragmentManager, UiInteraction.EventType.ANNOUNCEMENT, app.aaps.core.ui.R.string.careportal_announcement)
        }
        binding.siteRotation.setOnClickListener {
            uiInteraction.runSiteRotationDialog(childFragmentManager)
        }

        preferences.put(BooleanNonKey.ObjectivesActionsUsed, true)
    }

    @Synchronized
    override fun onResume() {
        super.onResume()
        disposable += rxBus
            .toObservable(EventInitializationChanged::class.java)
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
            .toObservable(EventTherapyEventChange::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({ updateGui() }, fabricPrivacy::logException)
        updateGui()
    }

    @Synchronized
    override fun onPause() {
        super.onPause()
        disposable.clear()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    @Synchronized
    fun updateGui() {

        val profile = profileFunction.getProfile()
        val pump = activePlugin.activePump

        binding.profileSwitch.visibility = (
            activePlugin.activeProfileSource.profile != null &&
                pump.pumpDescription.isSetBasalProfileCapable &&
                pump.isInitialized() &&
                loop.runningMode != RM.Mode.DISCONNECTED_PUMP &&
                !pump.isSuspended()).toVisibility()

        if (!pump.pumpDescription.isExtendedBolusCapable || !pump.isInitialized()  || pump.isSuspended() || loop.runningMode == RM.Mode.DISCONNECTED_PUMP || pump.isFakingTempsByExtendedBoluses || config.AAPSCLIENT) {
            binding.extendedBolus.visibility = View.GONE
            binding.extendedBolusCancel.visibility = View.GONE
        } else {
            val activeExtendedBolus = persistenceLayer.getExtendedBolusActiveAt(dateUtil.now())
            if (activeExtendedBolus != null) {
                binding.extendedBolus.visibility = View.GONE
                binding.extendedBolusCancel.visibility = View.VISIBLE
                @Suppress("SetTextI18n")
                binding.extendedBolusCancel.text = rh.gs(app.aaps.core.ui.R.string.cancel) + " " + activeExtendedBolus.toStringMedium(dateUtil, rh)
            } else {
                binding.extendedBolus.visibility = View.VISIBLE
                binding.extendedBolusCancel.visibility = View.GONE
            }
        }

        if (!pump.pumpDescription.isTempBasalCapable || !pump.isInitialized() || pump.isSuspended() || loop.runningMode == RM.Mode.DISCONNECTED_PUMP || config.AAPSCLIENT) {
            binding.setTempBasal.visibility = View.GONE
            binding.cancelTempBasal.visibility = View.GONE
        } else {
            val activeTemp = processedTbrEbData.getTempBasalIncludingConvertedExtended(System.currentTimeMillis())
            if (activeTemp != null) {
                binding.setTempBasal.visibility = View.GONE
                binding.cancelTempBasal.visibility = View.VISIBLE
                @Suppress("SetTextI18n")
                binding.cancelTempBasal.text = rh.gs(app.aaps.core.ui.R.string.cancel) + " " + activeTemp.toStringShort(rh)
            } else {
                binding.setTempBasal.visibility = View.VISIBLE
                binding.cancelTempBasal.visibility = View.GONE
            }
        }
        val activeBgSource = activePlugin.activeBgSource
        binding.historyBrowser.visibility = (profile != null).toVisibility()
        binding.fill.visibility = (pump.pumpDescription.isRefillingCapable && pump.isInitialized()).toVisibility()
        binding.pumpBatteryChange.visibility = (pump.pumpDescription.isBatteryReplaceable || pump.isBatteryChangeLoggingEnabled()).toVisibility()
        binding.tempTarget.visibility = (profile != null && loop.runningMode.isLoopRunning()).toVisibility()
        binding.tddStats.visibility = pump.pumpDescription.supportsTDDs.toVisibility()
        val isPatchPump = pump.pumpDescription.isPatchPump
        binding.status.apply {
            cannulaOrPatch.text = if (cannulaOrPatch.text.isEmpty()) "" else if (isPatchPump) rh.gs(R.string.patch_pump) else rh.gs(R.string.cannula)
            val imageResource = if (isPatchPump) app.aaps.core.objects.R.drawable.ic_patch_pump_outline else R.drawable.ic_cp_age_cannula
            cannulaOrPatch.setCompoundDrawablesWithIntrinsicBounds(imageResource, 0, 0, 0)
            batteryLayout.visibility = (!isPatchPump || pump.pumpDescription.useHardwareLink).toVisibility()

            if (!config.AAPSCLIENT) {
                statusLightHandler.updateStatusLights(
                    cannulaAge, cannulaUsage, insulinAge,
                    reservoirLevel, sensorAge, sensorLevel,
                    pbAge, pbLevel
                )
                sensorLevelLabel.text = if (activeBgSource.sensorBatteryLevel == -1) "" else rh.gs(R.string.level_label)
            } else {
                statusLightHandler.updateStatusLights(cannulaAge, cannulaUsage, insulinAge, null, sensorAge, null, pbAge, null)
                sensorLevelLabel.text = ""
                insulinLevelLabel.text = ""
                pbLevelLabel.text = ""
            }
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

            val btn = SingleClickButton(currentContext, null, app.aaps.core.ui.R.attr.customBtnStyle)
            btn.text = rh.gs(customAction.name)

            val layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 0.5f
            )
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

            binding.buttonsLayout.addView(btn)

            this.pumpCustomActions[rh.gs(customAction.name)] = customAction
            this.pumpCustomButtons.add(btn)
        }
    }

    private fun removePumpCustomActions() {
        for (customButton in pumpCustomButtons) binding.buttonsLayout.removeView(customButton)
        pumpCustomButtons.clear()
    }
}
