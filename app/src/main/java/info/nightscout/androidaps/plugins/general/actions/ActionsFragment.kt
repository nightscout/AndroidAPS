package info.nightscout.androidaps.plugins.general.actions

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import dagger.android.support.DaggerFragment
import info.nightscout.androidaps.R
import info.nightscout.androidaps.activities.ErrorHelperActivity
import info.nightscout.androidaps.activities.HistoryBrowseActivity
import info.nightscout.androidaps.activities.TDDStatsActivity
import info.nightscout.androidaps.database.AppRepository
import info.nightscout.androidaps.database.ValueWrapper
import info.nightscout.androidaps.database.entities.UserEntry.Action
import info.nightscout.androidaps.database.entities.UserEntry.Sources
import info.nightscout.androidaps.databinding.ActionsFragmentBinding
import info.nightscout.androidaps.dialogs.*
import info.nightscout.androidaps.events.EventCustomActionsChanged
import info.nightscout.androidaps.events.EventExtendedBolusChange
import info.nightscout.androidaps.events.EventInitializationChanged
import info.nightscout.androidaps.events.EventTempBasalChange
import info.nightscout.androidaps.events.EventTherapyEventChange
import info.nightscout.androidaps.extensions.toStringMedium
import info.nightscout.androidaps.extensions.toStringShort
import info.nightscout.androidaps.extensions.toVisibility
import info.nightscout.androidaps.interfaces.*
import info.nightscout.shared.logging.AAPSLogger
import info.nightscout.androidaps.logging.UserEntryLogger
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.plugins.general.actions.defs.CustomAction
import info.nightscout.androidaps.plugins.general.overview.StatusLightHandler
import info.nightscout.androidaps.queue.Callback
import info.nightscout.androidaps.skins.SkinProvider
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.alertDialogs.OKDialog
import info.nightscout.androidaps.utils.buildHelper.BuildHelper
import info.nightscout.androidaps.utils.protection.ProtectionCheck
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.rx.AapsSchedulers
import info.nightscout.shared.sharedPreferences.SP
import info.nightscout.androidaps.utils.ui.SingleClickButton
import info.nightscout.androidaps.utils.ui.UIRunnable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import java.util.*
import javax.inject.Inject

class ActionsFragment : DaggerFragment() {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var sp: SP
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var ctx: Context
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var statusLightHandler: StatusLightHandler
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var iobCobCalculator: IobCobCalculator
    @Inject lateinit var commandQueue: CommandQueue
    @Inject lateinit var buildHelper: BuildHelper
    @Inject lateinit var protectionCheck: ProtectionCheck
    @Inject lateinit var skinProvider: SkinProvider
    @Inject lateinit var config: Config
    @Inject lateinit var uel: UserEntryLogger
    @Inject lateinit var repository: AppRepository
    @Inject lateinit var loop: Loop

    private var disposable: CompositeDisposable = CompositeDisposable()

    private val pumpCustomActions = HashMap<String, CustomAction>()
    private val pumpCustomButtons = ArrayList<SingleClickButton>()
    private lateinit var dm: DisplayMetrics

    private var _binding: ActionsFragmentBinding? = null
    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        //check screen width
        dm = DisplayMetrics()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            @Suppress("DEPRECATION")
            activity?.display?.getRealMetrics(dm)
        } else {
            @Suppress("DEPRECATION") activity?.windowManager?.defaultDisplay?.getMetrics(dm)
        }
        _binding = ActionsFragmentBinding.inflate(inflater, container, false)
        return  binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        skinProvider.activeSkin().preProcessLandscapeActionsLayout(dm, binding)

        binding.profileSwitch.setOnClickListener {
            activity?.let { activity ->
                protectionCheck.queryProtection(
                    activity,
                    ProtectionCheck.Protection.BOLUS,
                    UIRunnable { ProfileSwitchDialog().show(childFragmentManager, "ProfileSwitchDialog")})
            }
        }
        binding.tempTarget.setOnClickListener {
            activity?.let { activity ->
                protectionCheck.queryProtection(
                    activity,
                    ProtectionCheck.Protection.BOLUS,
                    UIRunnable { TempTargetDialog().show(childFragmentManager, "Actions") })
            }
        }
        binding.extendedBolus.setOnClickListener {
            activity?.let { activity ->
                protectionCheck.queryProtection(activity, ProtectionCheck.Protection.BOLUS, UIRunnable {
                    OKDialog.showConfirmation(
                        activity, rh.gs(R.string.extended_bolus), rh.gs(R.string.ebstopsloop),
                        Runnable {
                            ExtendedBolusDialog().show(childFragmentManager, "Actions")
                        }, null
                    )
                })
            }
        }
        binding.extendedBolusCancel.setOnClickListener {
            if (iobCobCalculator.getExtendedBolus(dateUtil.now()) != null) {
                uel.log(Action.CANCEL_EXTENDED_BOLUS, Sources.Actions)
                commandQueue.cancelExtended(object : Callback() {
                    override fun run() {
                        if (!result.success) {
                            ErrorHelperActivity.runAlarm(ctx, result.comment, rh.gs(R.string.extendedbolusdeliveryerror), R.raw.boluserror)
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
                    UIRunnable { TempBasalDialog().show(childFragmentManager, "Actions") })
            }
        }
        binding.cancelTempBasal.setOnClickListener {
            if (iobCobCalculator.getTempBasalIncludingConvertedExtended(dateUtil.now()) != null) {
                uel.log(Action.CANCEL_TEMP_BASAL, Sources.Actions)
                commandQueue.cancelTempBasal(true, object : Callback() {
                    override fun run() {
                        if (!result.success) {
                            ErrorHelperActivity.runAlarm(ctx, result.comment, rh.gs(R.string.tempbasaldeliveryerror), R.raw.boluserror)
                        }
                    }
                })
            }
        }
        binding.fill.setOnClickListener {
            activity?.let { activity ->
                protectionCheck.queryProtection(activity, ProtectionCheck.Protection.BOLUS, UIRunnable { FillDialog().show(childFragmentManager, "FillDialog") })
            }
        }
        binding.historyBrowser.setOnClickListener { startActivity(Intent(context, HistoryBrowseActivity::class.java)) }
        binding.tddStats.setOnClickListener { startActivity(Intent(context, TDDStatsActivity::class.java)) }
        binding.bgCheck.setOnClickListener {
            CareDialog().setOptions(CareDialog.EventType.BGCHECK, R.string.careportal_bgcheck).show(childFragmentManager, "Actions")
        }
        binding.cgmSensorInsert.setOnClickListener {
            CareDialog().setOptions(CareDialog.EventType.SENSOR_INSERT, R.string.careportal_cgmsensorinsert).show(childFragmentManager, "Actions")
        }
        binding.pumpBatteryChange.setOnClickListener {
            CareDialog().setOptions(CareDialog.EventType.BATTERY_CHANGE, R.string.careportal_pumpbatterychange).show(childFragmentManager, "Actions")
        }
        binding.note.setOnClickListener {
            CareDialog().setOptions(CareDialog.EventType.NOTE, R.string.careportal_note).show(childFragmentManager, "Actions")
        }
        binding.exercise.setOnClickListener {
            CareDialog().setOptions(CareDialog.EventType.EXERCISE, R.string.careportal_exercise).show(childFragmentManager, "Actions")
        }
        binding.question.setOnClickListener {
            CareDialog().setOptions(CareDialog.EventType.QUESTION, R.string.careportal_question).show(childFragmentManager, "Actions")
        }
        binding.announcement.setOnClickListener {
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
                !pump.isSuspended() &&
                !loop.isDisconnected).toVisibility()

        if (!pump.pumpDescription.isExtendedBolusCapable || !pump.isInitialized() || pump.isSuspended() || loop.isDisconnected || pump.isFakingTempsByExtendedBoluses || config.NSCLIENT) {
            binding.extendedBolus.visibility = View.GONE
            binding.extendedBolusCancel.visibility = View.GONE
        } else {
            val activeExtendedBolus = repository.getExtendedBolusActiveAt(dateUtil.now()).blockingGet()
            if (activeExtendedBolus is ValueWrapper.Existing) {
                binding.extendedBolus.visibility = View.GONE
                binding.extendedBolusCancel.visibility = View.VISIBLE
                @Suppress("SetTextI18n")
                binding.extendedBolusCancel.text = rh.gs(R.string.cancel) + " " + activeExtendedBolus.value.toStringMedium(dateUtil)
            } else {
                binding.extendedBolus.visibility = View.VISIBLE
                binding.extendedBolusCancel.visibility = View.GONE
            }
        }

        if (!pump.pumpDescription.isTempBasalCapable || !pump.isInitialized() || pump.isSuspended() || loop.isDisconnected || config.NSCLIENT) {
            binding.setTempBasal.visibility = View.GONE
            binding.cancelTempBasal.visibility = View.GONE
        } else {
            val activeTemp = iobCobCalculator.getTempBasalIncludingConvertedExtended(System.currentTimeMillis())
            if (activeTemp != null) {
                binding.setTempBasal.visibility = View.GONE
                binding.cancelTempBasal.visibility = View.VISIBLE
                @Suppress("SetTextI18n")
                binding.cancelTempBasal.text = rh.gs(R.string.cancel) + " " + activeTemp.toStringShort()
            } else {
                binding.setTempBasal.visibility = View.VISIBLE
                binding.cancelTempBasal.visibility = View.GONE
            }
        }
        val activeBgSource = activePlugin.activeBgSource
        binding.historyBrowser.visibility = (profile != null).toVisibility()
        binding.fill.visibility = (pump.pumpDescription.isRefillingCapable && pump.isInitialized() && !pump.isSuspended()).toVisibility()
        binding.pumpBatteryChange.visibility = (pump.pumpDescription.isBatteryReplaceable || pump.isBatteryChangeLoggingEnabled()).toVisibility()
        binding.tempTarget.visibility = (profile != null && !loop.isDisconnected).toVisibility()
        binding.tddStats.visibility = pump.pumpDescription.supportsTDDs.toVisibility()
        val isPatchPump = pump.pumpDescription.isPatchPump
        binding.status.apply {
            cannulaOrPatch.text = if (isPatchPump) rh.gs(R.string.patch_pump) else rh.gs(R.string.cannula)
            val imageResource = if (isPatchPump) R.drawable.ic_patch_pump_outline else R.drawable.ic_cp_age_cannula
            cannulaOrPatch.setCompoundDrawablesWithIntrinsicBounds(imageResource, 0, 0, 0)
            batteryLayout.visibility = (!isPatchPump || pump.pumpDescription.useHardwareLink).toVisibility()

            if (!config.NSCLIENT) {
                statusLightHandler.updateStatusLights(cannulaAge, insulinAge, reservoirLevel, sensorAge, sensorLevel, pbAge, batteryLevel)
                sensorLevelLabel.text = if (activeBgSource.sensorBatteryLevel == -1) "" else rh.gs(R.string.careportal_level_label)
            } else {
                statusLightHandler.updateStatusLights(cannulaAge, insulinAge, null, sensorAge, null, pbAge, null)
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

            val btn = SingleClickButton(currentContext, null, android.R.attr.buttonStyle)
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
