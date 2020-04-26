package info.nightscout.androidaps.plugins.general.overview

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.os.Handler
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.ContextMenu
import android.view.ContextMenu.ContextMenuInfo
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.View.OnLongClickListener
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import com.jjoe64.graphview.GraphView
import dagger.android.HasAndroidInjector
import dagger.android.support.DaggerFragment
import info.nightscout.androidaps.Config
import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.R
import info.nightscout.androidaps.data.Profile
import info.nightscout.androidaps.dialogs.CalibrationDialog
import info.nightscout.androidaps.dialogs.CarbsDialog
import info.nightscout.androidaps.dialogs.InsulinDialog
import info.nightscout.androidaps.dialogs.TreatmentDialog
import info.nightscout.androidaps.dialogs.WizardDialog
import info.nightscout.androidaps.events.*
import info.nightscout.androidaps.interfaces.ActivePluginProvider
import info.nightscout.androidaps.interfaces.Constraint
import info.nightscout.androidaps.interfaces.PluginType
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.plugins.aps.loop.LoopPlugin
import info.nightscout.androidaps.plugins.aps.loop.events.EventNewOpenLoopNotification
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin
import info.nightscout.androidaps.plugins.configBuilder.ConstraintChecker
import info.nightscout.androidaps.plugins.configBuilder.ProfileFunction
import info.nightscout.androidaps.plugins.general.nsclient.data.NSDeviceStatus
import info.nightscout.androidaps.plugins.general.overview.activities.QuickWizardListActivity
import info.nightscout.androidaps.plugins.general.overview.graphData.GraphData
import info.nightscout.androidaps.plugins.general.overview.notifications.NotificationStore
import info.nightscout.androidaps.plugins.general.wear.ActionStringHandler
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.GlucoseStatus
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.IobCobCalculatorPlugin
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.events.EventAutosensCalculationFinished
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.events.EventIobCalculationProgress
import info.nightscout.androidaps.plugins.source.DexcomPlugin
import info.nightscout.androidaps.plugins.source.XdripPlugin
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin
import info.nightscout.androidaps.queue.CommandQueue
import info.nightscout.androidaps.utils.*
import info.nightscout.androidaps.utils.alertDialogs.OKDialog
import info.nightscout.androidaps.utils.buildHelper.BuildHelper
import info.nightscout.androidaps.utils.extensions.toVisibility
import info.nightscout.androidaps.utils.protection.ProtectionCheck
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.sharedPreferences.SP
import info.nightscout.androidaps.utils.wizard.QuickWizard
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.overview_fragment.*
import kotlinx.android.synthetic.main.overview_fragment.careportal_canulaage
import kotlinx.android.synthetic.main.overview_fragment.careportal_insulinage
import kotlinx.android.synthetic.main.overview_fragment.careportal_reservoirlevel
import kotlinx.android.synthetic.main.overview_fragment.careportal_sensorage
import kotlinx.android.synthetic.main.overview_fragment.careportal_pbage
import kotlinx.android.synthetic.main.overview_fragment.careportal_batterylevel
import kotlinx.android.synthetic.main.overview_fragment.overview_activeprofile
import kotlinx.android.synthetic.main.overview_fragment.overview_apsmode
import kotlinx.android.synthetic.main.overview_fragment.overview_arrow
import kotlinx.android.synthetic.main.overview_fragment.overview_basebasal
import kotlinx.android.synthetic.main.overview_fragment.overview_bg
import kotlinx.android.synthetic.main.overview_fragment.overview_bggraph
import kotlinx.android.synthetic.main.overview_fragment.overview_carbsbutton
import kotlinx.android.synthetic.main.overview_fragment.overview_chartMenuButton
import kotlinx.android.synthetic.main.overview_fragment.overview_cob
import kotlinx.android.synthetic.main.overview_fragment.overview_extendedbolus
import kotlinx.android.synthetic.main.overview_fragment.overview_insulinbutton
import kotlinx.android.synthetic.main.overview_fragment.overview_iob
import kotlinx.android.synthetic.main.overview_fragment.overview_iobcalculationprogess
import kotlinx.android.synthetic.main.overview_fragment.overview_iobgraph
import kotlinx.android.synthetic.main.overview_fragment.overview_looplayout
import kotlinx.android.synthetic.main.overview_fragment.overview_notifications
import kotlinx.android.synthetic.main.overview_fragment.overview_pumpstatus
import kotlinx.android.synthetic.main.overview_fragment.overview_pumpstatuslayout
import kotlinx.android.synthetic.main.overview_fragment.overview_quickwizardbutton
import kotlinx.android.synthetic.main.overview_fragment.overview_sensitivity
import kotlinx.android.synthetic.main.overview_fragment.overview_temptarget
import kotlinx.android.synthetic.main.overview_fragment.overview_treatmentbutton
import kotlinx.android.synthetic.main.overview_fragment.overview_wizardbutton
import kotlinx.android.synthetic.main.overview_fragment_nsclient_tablet.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.collections.ArrayList
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

class OverviewFragment : DaggerFragment(), View.OnClickListener, OnLongClickListener {
    @Inject lateinit var injector: HasAndroidInjector
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var sp: SP
    @Inject lateinit var rxBus: RxBusWrapper
    @Inject lateinit var resourceHelper: ResourceHelper
    @Inject lateinit var defaultValueHelper: DefaultValueHelper
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var constraintChecker: ConstraintChecker
    @Inject lateinit var statusLightHandler: StatusLightHandler
    @Inject lateinit var nsDeviceStatus: NSDeviceStatus
    @Inject lateinit var loopPlugin: LoopPlugin
    @Inject lateinit var configBuilderPlugin: ConfigBuilderPlugin
    @Inject lateinit var activePlugin: ActivePluginProvider
    @Inject lateinit var treatmentsPlugin: TreatmentsPlugin
    @Inject lateinit var iobCobCalculatorPlugin: IobCobCalculatorPlugin
    @Inject lateinit var dexcomPlugin: DexcomPlugin
    @Inject lateinit var xdripPlugin: XdripPlugin
    @Inject lateinit var notificationStore: NotificationStore
    @Inject lateinit var actionStringHandler: ActionStringHandler
    @Inject lateinit var quickWizard: QuickWizard
    @Inject lateinit var buildHelper: BuildHelper
    @Inject lateinit var commandQueue: CommandQueue
    @Inject lateinit var protectionCheck: ProtectionCheck
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var overviewMenus: OverviewMenus

    private val disposable = CompositeDisposable()

    private var smallWidth = false
    private var smallHeight = false
    private lateinit var dm: DisplayMetrics
    private var axisWidth: Int = 0
    private var rangeToDisplay = 6 // for graph
    private var loopHandler = Handler()
    private var refreshLoop: Runnable? = null

    private val worker = Executors.newSingleThreadScheduledExecutor()
    private var scheduledUpdate: ScheduledFuture<*>? = null

    private val secondaryGraphs = ArrayList<GraphView>()
    private val secondaryGraphsLabel = ArrayList<TextView>()

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

        return when {
            resourceHelper.gb(R.bool.isTablet) && Config.NSCLIENT ->
                inflater.inflate(R.layout.overview_fragment_nsclient_tablet, container, false)

            Config.NSCLIENT                                       ->
                inflater.inflate(R.layout.overview_fragment_nsclient, container, false)

            smallHeight || landscape                              ->
                inflater.inflate(R.layout.overview_fragment_landscape, container, false)

            else                                                  ->
                inflater.inflate(R.layout.overview_fragment, container, false)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (smallWidth) overview_arrow?.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 35f)
        overview_pumpstatus?.setBackgroundColor(resourceHelper.gc(R.color.colorInitializingBorder))

        overview_notifications?.setHasFixedSize(false)
        overview_notifications?.layoutManager = LinearLayoutManager(view.context)
        axisWidth = if (dm.densityDpi <= 120) 3 else if (dm.densityDpi <= 160) 10 else if (dm.densityDpi <= 320) 35 else if (dm.densityDpi <= 420) 50 else if (dm.densityDpi <= 560) 70 else 80
        overview_bggraph?.gridLabelRenderer?.gridColor = resourceHelper.gc(R.color.graphgrid)
        overview_bggraph?.gridLabelRenderer?.reloadStyles()
        overview_bggraph?.gridLabelRenderer?.labelVerticalWidth = axisWidth

        rangeToDisplay = sp.getInt(R.string.key_rangetodisplay, 6)

        overview_bggraph?.setOnLongClickListener {
            rangeToDisplay += 6
            rangeToDisplay = if (rangeToDisplay > 24) 6 else rangeToDisplay
            sp.putInt(R.string.key_rangetodisplay, rangeToDisplay)
            updateGUI("rangeChange")
            sp.putBoolean(R.string.key_objectiveusescale, true)
            false
        }
        overviewMenus.setupChartMenu(overview_chartMenuButton)
        prepareGraphs()

        overview_accepttempbutton?.setOnClickListener(this)
        overview_treatmentbutton?.setOnClickListener(this)
        overview_wizardbutton?.setOnClickListener(this)
        overview_calibrationbutton?.setOnClickListener(this)
        overview_cgmbutton?.setOnClickListener(this)
        overview_insulinbutton?.setOnClickListener(this)
        overview_carbsbutton?.setOnClickListener(this)
        overview_quickwizardbutton?.setOnClickListener(this)
        overview_quickwizardbutton?.setOnLongClickListener(this)
    }

    override fun onPause() {
        super.onPause()
        disposable.clear()
        loopHandler.removeCallbacksAndMessages(null)
        overview_apsmode?.let { unregisterForContextMenu(it) }
        overview_activeprofile?.let { unregisterForContextMenu(it) }
        overview_temptarget?.let { unregisterForContextMenu(it) }
    }

    override fun onResume() {
        super.onResume()
        disposable.add(rxBus
            .toObservable(EventRefreshOverview::class.java)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                prepareGraphs()
                if (it.now) updateGUI(it.from)
                else scheduleUpdateGUI(it.from)
            }) { fabricPrivacy.logException(it) })
        disposable.add(rxBus
            .toObservable(EventExtendedBolusChange::class.java)
            .observeOn(Schedulers.io())
            .subscribe({ scheduleUpdateGUI("EventExtendedBolusChange") }) { fabricPrivacy.logException(it) })
        disposable.add(rxBus
            .toObservable(EventTempBasalChange::class.java)
            .observeOn(Schedulers.io())
            .subscribe({ scheduleUpdateGUI("EventTempBasalChange") }) { fabricPrivacy.logException(it) })
        disposable.add(rxBus
            .toObservable(EventTreatmentChange::class.java)
            .observeOn(Schedulers.io())
            .subscribe({ scheduleUpdateGUI("EventTreatmentChange") }) { fabricPrivacy.logException(it) })
        disposable.add(rxBus
            .toObservable(EventTempTargetChange::class.java)
            .observeOn(Schedulers.io())
            .subscribe({ scheduleUpdateGUI("EventTempTargetChange") }) { fabricPrivacy.logException(it) })
        disposable.add(rxBus
            .toObservable(EventAcceptOpenLoopChange::class.java)
            .observeOn(Schedulers.io())
            .subscribe({ scheduleUpdateGUI("EventAcceptOpenLoopChange") }) { fabricPrivacy.logException(it) })
        disposable.add(rxBus
            .toObservable(EventCareportalEventChange::class.java)
            .observeOn(Schedulers.io())
            .subscribe({ scheduleUpdateGUI("EventCareportalEventChange") }) { fabricPrivacy.logException(it) })
        disposable.add(rxBus
            .toObservable(EventInitializationChanged::class.java)
            .observeOn(Schedulers.io())
            .subscribe({ scheduleUpdateGUI("EventInitializationChanged") }) { fabricPrivacy.logException(it) })
        disposable.add(rxBus
            .toObservable(EventAutosensCalculationFinished::class.java)
            .observeOn(Schedulers.io())
            .subscribe({ scheduleUpdateGUI("EventAutosensCalculationFinished") }) { fabricPrivacy.logException(it) })
        disposable.add(rxBus
            .toObservable(EventProfileNeedsUpdate::class.java)
            .observeOn(Schedulers.io())
            .subscribe({ scheduleUpdateGUI("EventProfileNeedsUpdate") }) { fabricPrivacy.logException(it) })
        disposable.add(rxBus
            .toObservable(EventPreferenceChange::class.java)
            .observeOn(Schedulers.io())
            .subscribe({ scheduleUpdateGUI("EventPreferenceChange") }) { fabricPrivacy.logException(it) })
        disposable.add(rxBus
            .toObservable(EventNewOpenLoopNotification::class.java)
            .observeOn(Schedulers.io())
            .subscribe({ scheduleUpdateGUI("EventNewOpenLoopNotification") }) { fabricPrivacy.logException(it) })
        disposable.add(rxBus
            .toObservable(EventPumpStatusChanged::class.java)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ updatePumpStatus(it) }) { fabricPrivacy.logException(it) })
        disposable.add(rxBus
            .toObservable(EventIobCalculationProgress::class.java)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ overview_iobcalculationprogess?.text = it.progress }) { fabricPrivacy.logException(it) })

        refreshLoop = Runnable {
            scheduleUpdateGUI("refreshLoop")
            loopHandler.postDelayed(refreshLoop, 60 * 1000L)
        }
        loopHandler.postDelayed(refreshLoop, 60 * 1000L)

        overview_apsmode?.let { registerForContextMenu(overview_apsmode) }
        overview_activeprofile?.let { registerForContextMenu(it) }
        overview_temptarget?.let { registerForContextMenu(it) }
        updateGUI("onResume")
    }

    override fun onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenuInfo?) {
        super.onCreateContextMenu(menu, v, menuInfo)
        overviewMenus.createContextMenu(menu, v)
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        val manager = fragmentManager
        return if (manager != null && overviewMenus.onContextItemSelected(item, manager)) true else super.onContextItemSelected(item)
    }

    override fun onClick(v: View) {
        val manager = fragmentManager ?: return
        // try to fix  https://fabric.io/nightscout3/android/apps/info.nightscout.androidaps/issues/5aca7a1536c7b23527eb4be7?time=last-seven-days
        // https://stackoverflow.com/questions/14860239/checking-if-state-is-saved-before-committing-a-fragmenttransaction
        if (manager.isStateSaved) return
        activity?.let { activity ->
            when (v.id) {
                R.id.overview_treatmentbutton   -> protectionCheck.queryProtection(activity, ProtectionCheck.Protection.BOLUS, Runnable { TreatmentDialog().show(manager, "Overview") })
                R.id.overview_wizardbutton      -> protectionCheck.queryProtection(activity, ProtectionCheck.Protection.BOLUS, Runnable { WizardDialog().show(manager, "Overview") })
                R.id.overview_insulinbutton     -> protectionCheck.queryProtection(activity, ProtectionCheck.Protection.BOLUS, Runnable { InsulinDialog().show(manager, "Overview") })
                R.id.overview_quickwizardbutton -> protectionCheck.queryProtection(activity, ProtectionCheck.Protection.BOLUS, Runnable { onClickQuickWizard() })
                R.id.overview_carbsbutton       -> protectionCheck.queryProtection(activity, ProtectionCheck.Protection.BOLUS, Runnable { CarbsDialog().show(manager, "Overview") })

                R.id.overview_pumpstatus        -> {
                    if (activePlugin.activePump.isSuspended || !activePlugin.activePump.isInitialized) commandQueue.readStatus("RefreshClicked", null)
                }

                R.id.overview_cgmbutton         -> {
                    if (xdripPlugin.isEnabled(PluginType.BGSOURCE))
                        openCgmApp("com.eveningoutpost.dexdrip")
                    else if (dexcomPlugin.isEnabled(PluginType.BGSOURCE)) {
                        dexcomPlugin.findDexcomPackageName()?.let {
                            openCgmApp(it)
                        }
                            ?: ToastUtils.showToastInUiThread(activity, resourceHelper.gs(R.string.dexcom_app_not_installed))
                    }
                }

                R.id.overview_calibrationbutton -> {
                    if (xdripPlugin.isEnabled(PluginType.BGSOURCE)) {
                        CalibrationDialog().show(manager, "CalibrationDialog")
                    } else if (dexcomPlugin.isEnabled(PluginType.BGSOURCE)) {
                        try {
                            dexcomPlugin.findDexcomPackageName()?.let {
                                startActivity(Intent("com.dexcom.cgm.activities.MeterEntryActivity").setPackage(it))
                            }
                                ?: ToastUtils.showToastInUiThread(activity, resourceHelper.gs(R.string.dexcom_app_not_installed))
                        } catch (e: ActivityNotFoundException) {
                            ToastUtils.showToastInUiThread(activity, resourceHelper.gs(R.string.g5appnotdetected))
                        }
                    }
                }

                R.id.overview_accepttempbutton  -> {
                    profileFunction.getProfile() ?: return
                    if (loopPlugin.isEnabled(PluginType.LOOP)) {
                        val lastRun = loopPlugin.lastRun
                        loopPlugin.invoke("Accept temp button", false)
                        if (lastRun?.lastAPSRun != null && lastRun.constraintsProcessed.isChangeRequested) {
                            OKDialog.showConfirmation(activity, resourceHelper.gs(R.string.pump_tempbasal_label), lastRun.constraintsProcessed.toSpanned(), Runnable {
                                aapsLogger.debug("USER ENTRY: ACCEPT TEMP BASAL")
                                overview_accepttempbutton?.visibility = View.GONE
                                (context?.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(Constants.notificationID)
                                actionStringHandler.handleInitiate("cancelChangeRequest")
                                loopPlugin.acceptChangeRequest()
                            })
                        }
                    }
                }
            }
        }
    }

    private fun openCgmApp(packageName: String) {
        context?.let {
            val packageManager = it.packageManager
            try {
                val intent = packageManager.getLaunchIntentForPackage(packageName)
                    ?: throw ActivityNotFoundException()
                intent.addCategory(Intent.CATEGORY_LAUNCHER)
                it.startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                OKDialog.show(it, "", resourceHelper.gs(R.string.error_starting_cgm))
            }
        }
    }

    override fun onLongClick(v: View): Boolean {
        when (v.id) {
            R.id.overview_quickwizardbutton -> {
                startActivity(Intent(v.context, QuickWizardListActivity::class.java))
                return true
            }
        }
        return false
    }

    private fun onClickQuickWizard() {
        val actualBg = iobCobCalculatorPlugin.actualBg()
        val profile = profileFunction.getProfile()
        val profileName = profileFunction.getProfileName()
        val pump = activePlugin.activePump
        val quickWizardEntry = quickWizard.getActive()
        if (quickWizardEntry != null && actualBg != null && profile != null) {
            overview_quickwizardbutton?.visibility = View.VISIBLE
            val wizard = quickWizardEntry.doCalc(profile, profileName, actualBg, true)
            if (wizard.calculatedTotalInsulin > 0.0 && quickWizardEntry.carbs() > 0.0) {
                val carbsAfterConstraints = constraintChecker.applyCarbsConstraints(Constraint(quickWizardEntry.carbs())).value()
                activity?.let {
                    if (abs(wizard.insulinAfterConstraints - wizard.calculatedTotalInsulin) >= pump.pumpDescription.pumpType.determineCorrectBolusStepSize(wizard.insulinAfterConstraints) || carbsAfterConstraints != quickWizardEntry.carbs()) {
                        OKDialog.show(it, resourceHelper.gs(R.string.treatmentdeliveryerror), resourceHelper.gs(R.string.constraints_violation) + "\n" + resourceHelper.gs(R.string.changeyourinput))
                        return
                    }
                    wizard.confirmAndExecute(it)
                }
            }
        }
    }

    private fun updatePumpStatus(event: EventPumpStatusChanged) {
        val status = event.getStatus(resourceHelper)
        if (status != "") {
            overview_pumpstatus?.text = status
            overview_pumpstatuslayout?.visibility = View.VISIBLE
            overview_looplayout?.visibility = View.GONE
        } else {
            overview_pumpstatuslayout?.visibility = View.GONE
            overview_looplayout?.visibility = View.VISIBLE
        }
    }

    @SuppressLint("SetTextI18n")
    private fun processButtonsVisibility() {
        val lastBG = iobCobCalculatorPlugin.lastBg()
        val pump = activePlugin.activePump
        val profile = profileFunction.getProfile()
        val profileName = profileFunction.getProfileName()
        val actualBG = iobCobCalculatorPlugin.actualBg()

        // QuickWizard button
        val quickWizardEntry = quickWizard.getActive()
        if (quickWizardEntry != null && lastBG != null && profile != null && pump.isInitialized && !pump.isSuspended) {
            overview_quickwizardbutton?.visibility = View.VISIBLE
            val wizard = quickWizardEntry.doCalc(profile, profileName, lastBG, false)
            overview_quickwizardbutton?.text = quickWizardEntry.buttonText() + "\n" + resourceHelper.gs(R.string.format_carbs, quickWizardEntry.carbs()) +
                " " + resourceHelper.gs(R.string.formatinsulinunits, wizard.calculatedTotalInsulin)
            if (wizard.calculatedTotalInsulin <= 0) overview_quickwizardbutton?.visibility = View.GONE
        } else overview_quickwizardbutton?.visibility = View.GONE

        // **** Temp button ****
        val lastRun = loopPlugin.lastRun
        val closedLoopEnabled = constraintChecker.isClosedLoopAllowed()

        val showAcceptButton = !closedLoopEnabled.value() && // Open mode needed
            lastRun != null &&
            (lastRun.lastOpenModeAccept == 0L || lastRun.lastOpenModeAccept < lastRun.lastAPSRun) &&// never accepted or before last result
            lastRun.constraintsProcessed.isChangeRequested // change is requested

        if (showAcceptButton && pump.isInitialized && !pump.isSuspended && loopPlugin.isEnabled(PluginType.LOOP)) {
            overview_accepttempbutton?.visibility = View.VISIBLE
            overview_accepttempbutton?.text = "${resourceHelper.gs(R.string.setbasalquestion)}\n${lastRun!!.constraintsProcessed}"
        } else {
            overview_accepttempbutton?.visibility = View.GONE
        }

        // **** Various treatment buttons ****
        overview_carbsbutton?.visibility = ((!activePlugin.activePump.pumpDescription.storesCarbInfo || pump.isInitialized && !pump.isSuspended) && profile != null && sp.getBoolean(R.string.key_show_carbs_button, true)).toVisibility()
        overview_treatmentbutton?.visibility = (pump.isInitialized && !pump.isSuspended && profile != null && sp.getBoolean(R.string.key_show_treatment_button, false)).toVisibility()
        overview_wizardbutton?.visibility = (pump.isInitialized && !pump.isSuspended && profile != null && sp.getBoolean(R.string.key_show_wizard_button, true)).toVisibility()
        overview_insulinbutton?.visibility = (pump.isInitialized && !pump.isSuspended && profile != null && sp.getBoolean(R.string.key_show_insulin_button, true)).toVisibility()

        // **** Calibration & CGM buttons ****
        val xDripIsBgSource = xdripPlugin.isEnabled(PluginType.BGSOURCE)
        val dexcomIsSource = dexcomPlugin.isEnabled(PluginType.BGSOURCE)
        overview_calibrationbutton?.visibility = ((xDripIsBgSource || dexcomIsSource) && actualBG != null && sp.getBoolean(R.string.key_show_calibration_button, true)).toVisibility()
        overview_cgmbutton?.visibility = (sp.getBoolean(R.string.key_show_cgm_button, false) && (xDripIsBgSource || dexcomIsSource)).toVisibility()

    }

    private fun prepareGraphs() {
        val numOfGraphs = overviewMenus.setting.size

        if (numOfGraphs != secondaryGraphs.size - 1) {
            //aapsLogger.debug("New secondary graph count ${numOfGraphs-1}")
            // rebuild needed
            secondaryGraphs.clear()
            secondaryGraphsLabel.clear()
            overview_iobgraph.removeAllViews()
            for (i in 1 until numOfGraphs) {
                val label = TextView(context)
                label.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).also { it.setMargins(100, 0, 0, -50) }
                overview_iobgraph.addView(label)
                secondaryGraphsLabel.add(label)
                val graph = GraphView(context)
                graph.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, resourceHelper.dpToPx(100)).also { it.setMargins(0, 0, 0, resourceHelper.dpToPx(10)) }
                graph.gridLabelRenderer?.gridColor = resourceHelper.gc(R.color.graphgrid)
                graph.gridLabelRenderer?.reloadStyles()
                graph.gridLabelRenderer?.isHorizontalLabelsVisible = false
                graph.gridLabelRenderer?.labelVerticalWidth = axisWidth
                graph.gridLabelRenderer?.numVerticalLabels = 3
                graph.viewport.backgroundColor = Color.argb(20, 255, 255, 255) // 8% of gray
                overview_iobgraph.addView(graph)
                secondaryGraphs.add(graph)
            }
        }

    }

    private fun scheduleUpdateGUI(from: String) {
        class UpdateRunnable : Runnable {
            override fun run() {
                activity?.runOnUiThread {
                    updateGUI(from)
                    scheduledUpdate = null
                }
            }
        }
        // prepare task for execution in 500 milliseconds
        // cancel waiting task to prevent multiple updates
        scheduledUpdate?.cancel(false)
        val task: Runnable = UpdateRunnable()
        scheduledUpdate = worker.schedule(task, 500, TimeUnit.MILLISECONDS)
    }

    @SuppressLint("SetTextI18n")
    fun updateGUI(from: String) {
        aapsLogger.debug("UpdateGUI from $from")

        overview_time?.text = DateUtil.timeString(Date())

        if (!profileFunction.isProfileValid("Overview")) {
            overview_pumpstatus?.setText(R.string.noprofileset)
            overview_pumpstatuslayout?.visibility = View.VISIBLE
            overview_looplayout?.visibility = View.GONE
            return
        }
        overview_notifications?.let { notificationStore.updateNotifications(it) }
        overview_pumpstatuslayout?.visibility = View.GONE
        overview_looplayout?.visibility = View.VISIBLE

        val profile = profileFunction.getProfile() ?: return
        val actualBG = iobCobCalculatorPlugin.actualBg()
        val lastBG = iobCobCalculatorPlugin.lastBg()
        val pump = activePlugin.activePump
        val units = profileFunction.getUnits()
        val lowLine = defaultValueHelper.determineLowLine()
        val highLine = defaultValueHelper.determineHighLine()

        //Start with updating the BG as it is unaffected by loop.
        // **** BG value ****
        if (lastBG != null) {
            val color = when {
                lastBG.valueToUnits(units) < lowLine  -> resourceHelper.gc(R.color.low)
                lastBG.valueToUnits(units) > highLine -> resourceHelper.gc(R.color.high)
                else                                  -> resourceHelper.gc(R.color.inrange)
            }

            overview_bg?.text = lastBG.valueToUnitsToString(units)
            overview_bg?.setTextColor(color)
            overview_arrow?.text = lastBG.directionToSymbol()
            overview_arrow?.setTextColor(color)

            val glucoseStatus = GlucoseStatus(injector).glucoseStatusData
            if (glucoseStatus != null) {
                overview_delta?.text = "Δ ${Profile.toUnitsString(glucoseStatus.delta, glucoseStatus.delta * Constants.MGDL_TO_MMOLL, units)} $units"
                overview_deltashort?.text = Profile.toSignedUnitsString(glucoseStatus.delta, glucoseStatus.delta * Constants.MGDL_TO_MMOLL, units)
                overview_avgdelta?.text = "øΔ15m: ${Profile.toUnitsString(glucoseStatus.short_avgdelta, glucoseStatus.short_avgdelta * Constants.MGDL_TO_MMOLL, units)}\nøΔ40m: ${Profile.toUnitsString(glucoseStatus.long_avgdelta, glucoseStatus.long_avgdelta * Constants.MGDL_TO_MMOLL, units)}"
            } else {
                overview_delta?.text = "Δ " + resourceHelper.gs(R.string.notavailable)
                overview_deltashort?.text = "---"
                overview_avgdelta?.text = ""
            }

            // strike through if BG is old
            overview_bg?.let { overview_bg ->
                var flag = overview_bg.paintFlags
                flag = if (actualBG == null) {
                    flag or Paint.STRIKE_THRU_TEXT_FLAG
                } else flag and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                overview_bg.paintFlags = flag
            }
            overview_timeago?.text = DateUtil.minAgo(resourceHelper, lastBG.date)
            overview_timeagoshort?.text = "(" + DateUtil.minAgoShort(lastBG.date) + ")"

        }
        val closedLoopEnabled = constraintChecker.isClosedLoopAllowed()

        // open loop mode
        if (Config.APS && pump.pumpDescription.isTempBasalCapable) {
            overview_apsmode?.visibility = View.VISIBLE
            when {
                loopPlugin.isEnabled(PluginType.LOOP) && loopPlugin.isSuperBolus -> {
                    overview_apsmode?.text = String.format(resourceHelper.gs(R.string.loopsuperbolusfor), loopPlugin.minutesToEndOfSuspend())
                    overview_apsmode?.setBackgroundColor(resourceHelper.gc(R.color.ribbonWarning))
                    overview_apsmode?.setTextColor(resourceHelper.gc(R.color.ribbonTextWarning))
                }

                loopPlugin.isDisconnected                                        -> {
                    overview_apsmode?.text = String.format(resourceHelper.gs(R.string.loopdisconnectedfor), loopPlugin.minutesToEndOfSuspend())
                    overview_apsmode?.setBackgroundColor(resourceHelper.gc(R.color.ribbonCritical))
                    overview_apsmode?.setTextColor(resourceHelper.gc(R.color.ribbonTextCritical))
                }

                loopPlugin.isEnabled(PluginType.LOOP) && loopPlugin.isSuspended  -> {
                    overview_apsmode?.text = String.format(resourceHelper.gs(R.string.loopsuspendedfor), loopPlugin.minutesToEndOfSuspend())
                    overview_apsmode?.setBackgroundColor(resourceHelper.gc(R.color.ribbonWarning))
                    overview_apsmode?.setTextColor(resourceHelper.gc(R.color.ribbonTextWarning))
                }

                pump.isSuspended                                                 -> {
                    overview_apsmode?.text = resourceHelper.gs(R.string.pumpsuspended)
                    overview_apsmode?.setBackgroundColor(resourceHelper.gc(R.color.ribbonWarning))
                    overview_apsmode?.setTextColor(resourceHelper.gc(R.color.ribbonTextWarning))
                }

                loopPlugin.isEnabled(PluginType.LOOP)                            -> {
                    overview_apsmode?.text = if (closedLoopEnabled.value()) resourceHelper.gs(R.string.closedloop) else resourceHelper.gs(R.string.openloop)
                    overview_apsmode?.setBackgroundColor(resourceHelper.gc(R.color.ribbonDefault))
                    overview_apsmode?.setTextColor(resourceHelper.gc(R.color.ribbonTextDefault))
                }

                else                                                             -> {
                    overview_apsmode?.text = resourceHelper.gs(R.string.disabledloop)
                    overview_apsmode?.setBackgroundColor(resourceHelper.gc(R.color.ribbonCritical))
                    overview_apsmode?.setTextColor(resourceHelper.gc(R.color.ribbonTextCritical))
                }
            }
        } else {
            overview_apsmode?.visibility = View.GONE
        }

        // temp target
        val tempTarget = treatmentsPlugin.tempTargetFromHistory
        if (tempTarget != null) {
            overview_temptarget?.setTextColor(resourceHelper.gc(R.color.ribbonTextWarning))
            overview_temptarget?.setBackgroundColor(resourceHelper.gc(R.color.ribbonWarning))
            overview_temptarget?.text = Profile.toTargetRangeString(tempTarget.low, tempTarget.high, Constants.MGDL, units) + " " + DateUtil.untilString(tempTarget.end(), resourceHelper)
        } else {
            overview_temptarget?.setTextColor(resourceHelper.gc(R.color.ribbonTextDefault))
            overview_temptarget?.setBackgroundColor(resourceHelper.gc(R.color.ribbonDefault))
            overview_temptarget?.text = Profile.toTargetRangeString(profile.targetLowMgdl, profile.targetHighMgdl, Constants.MGDL, units)
        }

        // Basal, TBR
        val activeTemp = treatmentsPlugin.getTempBasalFromHistory(System.currentTimeMillis())
        overview_basebasal?.text = activeTemp?.let { if (resourceHelper.shortTextMode()) "T: " + activeTemp.toStringVeryShort() else activeTemp.toStringFull() }
            ?: resourceHelper.gs(R.string.pump_basebasalrate, profile.basal)
        overview_basebasal?.setOnClickListener {
            var fullText = "${resourceHelper.gs(R.string.pump_basebasalrate_label)}: ${resourceHelper.gs(R.string.pump_basebasalrate, profile.basal)}"
            if (activeTemp != null)
                fullText += "\n" + resourceHelper.gs(R.string.pump_tempbasal_label) + ": " + activeTemp.toStringFull()
            activity?.let {
                OKDialog.show(it, resourceHelper.gs(R.string.basal), fullText)
            }
        }
        overview_basebasal?.setTextColor(activeTemp?.let { resourceHelper.gc(R.color.basal) }
            ?: resourceHelper.gc(R.color.defaulttextcolor))

        // Extended bolus
        val extendedBolus = treatmentsPlugin.getExtendedBolusFromHistory(System.currentTimeMillis())
        overview_extendedbolus?.text = if (extendedBolus != null && !pump.isFakingTempsByExtendedBoluses) {
            if (resourceHelper.shortTextMode()) resourceHelper.gs(R.string.pump_basebasalrate, extendedBolus.absoluteRate())
            else extendedBolus.toStringMedium()
        } else ""
        overview_extendedbolus?.setOnClickListener {
            if (extendedBolus != null) activity?.let {
                OKDialog.show(it, resourceHelper.gs(R.string.extended_bolus), extendedBolus.toString())
            }
        }

        overview_activeprofile?.text = profileFunction.getProfileNameWithDuration()
        if (profile.percentage != 100 || profile.timeshift != 0) {
            overview_activeprofile?.setBackgroundColor(resourceHelper.gc(R.color.ribbonWarning))
            overview_activeprofile?.setTextColor(resourceHelper.gc(R.color.ribbonTextWarning))
        } else {
            overview_activeprofile?.setBackgroundColor(resourceHelper.gc(R.color.ribbonDefault))
            overview_activeprofile?.setTextColor(resourceHelper.gc(R.color.ribbonTextDefault))
        }

        processButtonsVisibility()

        // iob
        treatmentsPlugin.updateTotalIOBTreatments()
        treatmentsPlugin.updateTotalIOBTempBasals()
        val bolusIob = treatmentsPlugin.lastCalculationTreatments.round()
        val basalIob = treatmentsPlugin.lastCalculationTempBasals.round()
        overview_iob?.text = when {
            resourceHelper.shortTextMode()     -> {
                resourceHelper.gs(R.string.formatinsulinunits, bolusIob.iob + basalIob.basaliob)
            }

            resourceHelper.gb(R.bool.isTablet) -> {
                resourceHelper.gs(R.string.formatinsulinunits, bolusIob.iob + basalIob.basaliob) + " (" +
                    resourceHelper.gs(R.string.bolus) + ": " + resourceHelper.gs(R.string.formatinsulinunits, bolusIob.iob) +
                    resourceHelper.gs(R.string.basal) + ": " + resourceHelper.gs(R.string.formatinsulinunits, basalIob.basaliob) + ")"
            }

            else                               -> {
                resourceHelper.gs(R.string.formatinsulinunits, bolusIob.iob + basalIob.basaliob) + " (" +
                    resourceHelper.gs(R.string.formatinsulinunits, bolusIob.iob) + "/" +
                    resourceHelper.gs(R.string.formatinsulinunits, basalIob.basaliob) + ")"
            }
        }
        overview_iob?.setOnClickListener {
            activity?.let {
                OKDialog.show(it, resourceHelper.gs(R.string.iob),
                    resourceHelper.gs(R.string.formatinsulinunits, bolusIob.iob + basalIob.basaliob) + "\n" +
                        resourceHelper.gs(R.string.bolus) + ": " + resourceHelper.gs(R.string.formatinsulinunits, bolusIob.iob) + "\n" +
                        resourceHelper.gs(R.string.basal) + ": " + resourceHelper.gs(R.string.formatinsulinunits, basalIob.basaliob)
                )
            }
        }

        // Status lights
        overview_statuslights?.visibility = (sp.getBoolean(R.string.key_show_statuslights, true) || Config.NSCLIENT).toVisibility()
        statusLightHandler.updateStatusLights(careportal_canulaage, careportal_insulinage, careportal_reservoirlevel, careportal_sensorage, careportal_pbage, careportal_batterylevel)

        // cob
        var cobText: String = resourceHelper.gs(R.string.value_unavailable_short)
        val cobInfo = iobCobCalculatorPlugin.getCobInfo(false, "Overview COB")
        if (cobInfo.displayCob != null) {
            cobText = DecimalFormatter.to0Decimal(cobInfo.displayCob)
            if (cobInfo.futureCarbs > 0) cobText += "(" + DecimalFormatter.to0Decimal(cobInfo.futureCarbs) + ")"
        }
        overview_cob?.text = cobText

        val lastRun = loopPlugin.lastRun
        val predictionsAvailable = if (Config.APS) lastRun?.request?.hasPredictions == true else Config.NSCLIENT

        // pump status from ns
        overview_pump?.text = nsDeviceStatus.pumpStatus
        overview_pump?.setOnClickListener { activity?.let { OKDialog.show(it, resourceHelper.gs(R.string.pump), nsDeviceStatus.extendedPumpStatus) } }

        // OpenAPS status from ns
        overview_openaps?.text = nsDeviceStatus.openApsStatus
        overview_openaps?.setOnClickListener { activity?.let { OKDialog.show(it, resourceHelper.gs(R.string.openaps), nsDeviceStatus.extendedOpenApsStatus) } }

        // Uploader status from ns
        overview_uploader?.text = nsDeviceStatus.uploaderStatusSpanned
        overview_uploader?.setOnClickListener { activity?.let { OKDialog.show(it, resourceHelper.gs(R.string.uploader), nsDeviceStatus.extendedUploaderStatus) } }

        // Sensitivity
        iobCobCalculatorPlugin.getLastAutosensData("Overview")?.let { autosensData ->
            overview_sensitivity?.text = String.format(Locale.ENGLISH, "%.0f%%", autosensData.autosensResult.ratio * 100)
        }

        // ****** GRAPH *******
        GlobalScope.launch(Dispatchers.Main) {
            overview_bggraph ?: return@launch
            val graphData = GraphData(injector, overview_bggraph, iobCobCalculatorPlugin)
            val secondaryGraphsData: ArrayList<GraphData> = ArrayList()

            // do preparation in different thread
            withContext(Dispatchers.Default) {
                // align to hours
                val calendar = Calendar.getInstance()
                calendar.timeInMillis = System.currentTimeMillis()
                calendar[Calendar.MILLISECOND] = 0
                calendar[Calendar.SECOND] = 0
                calendar[Calendar.MINUTE] = 0
                calendar.add(Calendar.HOUR, 1)
                val hoursToFetch: Int
                val toTime: Long
                val fromTime: Long
                val endTime: Long
                val apsResult = if (Config.APS) lastRun?.constraintsProcessed else NSDeviceStatus.getAPSResult(injector)
                if (predictionsAvailable && apsResult != null && overviewMenus.setting[0][OverviewMenus.CharType.PRE.ordinal]) {
                    var predHours = (ceil(apsResult.latestPredictionsTime - System.currentTimeMillis().toDouble()) / (60 * 60 * 1000)).toInt()
                    predHours = min(2, predHours)
                    predHours = max(0, predHours)
                    hoursToFetch = rangeToDisplay - predHours
                    toTime = calendar.timeInMillis + 100000 // little bit more to avoid wrong rounding - GraphView specific
                    fromTime = toTime - T.hours(hoursToFetch.toLong()).msecs()
                    endTime = toTime + T.hours(predHours.toLong()).msecs()
                } else {
                    hoursToFetch = rangeToDisplay
                    toTime = calendar.timeInMillis + 100000 // little bit more to avoid wrong rounding - GraphView specific
                    fromTime = toTime - T.hours(hoursToFetch.toLong()).msecs()
                    endTime = toTime
                }
                val now = System.currentTimeMillis()

                //  ------------------ 1st graph

                // **** In range Area ****
                graphData.addInRangeArea(fromTime, endTime, lowLine, highLine)

                // **** BG ****
                if (predictionsAvailable && overviewMenus.setting[0][OverviewMenus.CharType.PRE.ordinal])
                    graphData.addBgReadings(fromTime, toTime, lowLine, highLine, apsResult?.predictions)
                else graphData.addBgReadings(fromTime, toTime, lowLine, highLine, null)

                // set manual x bounds to have nice steps
                graphData.formatAxis(fromTime, endTime)

                // Treatments
                graphData.addTreatments(fromTime, endTime)
                if (overviewMenus.setting[0][OverviewMenus.CharType.ACT.ordinal])
                    graphData.addActivity(fromTime, endTime, false, 0.8)

                // add basal data
                if (pump.pumpDescription.isTempBasalCapable && overviewMenus.setting[0][OverviewMenus.CharType.BAS.ordinal])
                    graphData.addBasals(fromTime, now, lowLine / graphData.maxY / 1.2)

                // add target line
                graphData.addTargetLine(fromTime, toTime, profile, loopPlugin.lastRun)

                // **** NOW line ****
                graphData.addNowLine(now)

                // ------------------ 2nd graph
                for (g in 0 until secondaryGraphs.size) {
                    val secondGraphData = GraphData(injector, secondaryGraphs[g], iobCobCalculatorPlugin)
                    var useIobForScale = false
                    var useCobForScale = false
                    var useDevForScale = false
                    var useRatioForScale = false
                    var useDSForScale = false
                    var useIAForScale = false
                    var useABSForScale = false
                    when {
                        overviewMenus.setting[g + 1][OverviewMenus.CharType.IOB.ordinal]      -> useIobForScale = true
                        overviewMenus.setting[g + 1][OverviewMenus.CharType.COB.ordinal]      -> useCobForScale = true
                        overviewMenus.setting[g + 1][OverviewMenus.CharType.DEV.ordinal]      -> useDevForScale = true
                        overviewMenus.setting[g + 1][OverviewMenus.CharType.SEN.ordinal]      -> useRatioForScale = true
                        overviewMenus.setting[g + 1][OverviewMenus.CharType.ACT.ordinal]      -> useIAForScale = true
                        overviewMenus.setting[g + 1][OverviewMenus.CharType.ABS.ordinal]      -> useABSForScale = true
                        overviewMenus.setting[g + 1][OverviewMenus.CharType.DEVSLOPE.ordinal] -> useDSForScale = true
                    }

                    if (overviewMenus.setting[g + 1][OverviewMenus.CharType.IOB.ordinal]) secondGraphData.addIob(fromTime, now, useIobForScale, 1.0, overviewMenus.setting[g + 1][OverviewMenus.CharType.PRE.ordinal])
                    if (overviewMenus.setting[g + 1][OverviewMenus.CharType.COB.ordinal]) secondGraphData.addCob(fromTime, now, useCobForScale, if (useCobForScale) 1.0 else 0.5)
                    if (overviewMenus.setting[g + 1][OverviewMenus.CharType.DEV.ordinal]) secondGraphData.addDeviations(fromTime, now, useDevForScale, 1.0)
                    if (overviewMenus.setting[g + 1][OverviewMenus.CharType.SEN.ordinal]) secondGraphData.addRatio(fromTime, now, useRatioForScale, 1.0)
                    if (overviewMenus.setting[g + 1][OverviewMenus.CharType.ACT.ordinal]) secondGraphData.addActivity(fromTime, endTime, useIAForScale, 0.8)
                    if (overviewMenus.setting[g + 1][OverviewMenus.CharType.ABS.ordinal]) secondGraphData.addAbsIob(fromTime, now, useABSForScale, 1.0)
                    if (overviewMenus.setting[g + 1][OverviewMenus.CharType.DEVSLOPE.ordinal] && buildHelper.isDev()) secondGraphData.addDeviationSlope(fromTime, now, useDSForScale, 1.0)

                    // set manual x bounds to have nice steps
                    secondGraphData.formatAxis(fromTime, endTime)
                    secondGraphData.addNowLine(now)
                    secondaryGraphsData.add(secondGraphData)
                }
            }
            // finally enforce drawing of graphs in UI thread
            graphData.performUpdate()
            for (g in 0 until secondaryGraphs.size) {
                secondaryGraphsLabel[g].text = overviewMenus.enabledTypes(g + 1)
                secondaryGraphs[g].visibility = (
                    overviewMenus.setting[g + 1][OverviewMenus.CharType.IOB.ordinal] ||
                        overviewMenus.setting[g + 1][OverviewMenus.CharType.COB.ordinal] ||
                        overviewMenus.setting[g + 1][OverviewMenus.CharType.DEV.ordinal] ||
                        overviewMenus.setting[g + 1][OverviewMenus.CharType.SEN.ordinal] ||
                        overviewMenus.setting[g + 1][OverviewMenus.CharType.ACT.ordinal] ||
                        overviewMenus.setting[g + 1][OverviewMenus.CharType.ABS.ordinal] ||
                        overviewMenus.setting[g + 1][OverviewMenus.CharType.DEVSLOPE.ordinal]
                    ).toVisibility()
                secondaryGraphsData[g].performUpdate()
            }
        }
    }
}