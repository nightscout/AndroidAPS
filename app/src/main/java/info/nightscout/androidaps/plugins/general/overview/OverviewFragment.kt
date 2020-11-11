package info.nightscout.androidaps.plugins.general.overview

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.AnimationDrawable
import android.os.Bundle
import android.os.Handler
import android.util.DisplayMetrics
import android.view.ContextMenu
import android.view.ContextMenu.ContextMenuInfo
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.View.OnLongClickListener
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.core.text.toSpanned
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
import info.nightscout.androidaps.interfaces.DatabaseHelperInterface
import info.nightscout.androidaps.interfaces.PluginType
import info.nightscout.androidaps.interfaces.ProfileFunction
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.plugins.aps.loop.LoopPlugin
import info.nightscout.androidaps.plugins.aps.loop.events.EventNewOpenLoopNotification
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin
import info.nightscout.androidaps.plugins.configBuilder.ConstraintChecker
import info.nightscout.androidaps.plugins.general.nsclient.data.NSDeviceStatus
import info.nightscout.androidaps.plugins.general.overview.activities.QuickWizardListActivity
import info.nightscout.androidaps.plugins.general.overview.graphData.GraphData
import info.nightscout.androidaps.plugins.general.overview.notifications.NotificationStore
import info.nightscout.androidaps.plugins.general.wear.ActionStringHandler
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.GlucoseStatus
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.IobCobCalculatorPlugin
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.events.EventAutosensCalculationFinished
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.events.EventIobCalculationProgress
import info.nightscout.androidaps.plugins.pump.common.defs.PumpType
import info.nightscout.androidaps.plugins.source.DexcomPlugin
import info.nightscout.androidaps.plugins.source.XdripPlugin
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin
import info.nightscout.androidaps.queue.CommandQueue
import info.nightscout.androidaps.skins.SkinProvider
import info.nightscout.androidaps.utils.*
import info.nightscout.androidaps.utils.alertDialogs.OKDialog
import info.nightscout.androidaps.utils.buildHelper.BuildHelper
import info.nightscout.androidaps.utils.extensions.toVisibility
import info.nightscout.androidaps.utils.protection.ProtectionCheck
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.sharedPreferences.SP
import info.nightscout.androidaps.utils.ui.UIRunnable
import info.nightscout.androidaps.utils.wizard.QuickWizard
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.overview_buttons_layout.*
import kotlinx.android.synthetic.main.overview_buttons_layout.overview_carbsbutton
import kotlinx.android.synthetic.main.overview_buttons_layout.overview_insulinbutton
import kotlinx.android.synthetic.main.overview_buttons_layout.overview_quickwizardbutton
import kotlinx.android.synthetic.main.overview_buttons_layout.overview_treatmentbutton
import kotlinx.android.synthetic.main.overview_buttons_layout.overview_wizardbutton
import kotlinx.android.synthetic.main.overview_fragment.overview_notifications
import kotlinx.android.synthetic.main.overview_fragment_nsclient_tablet.*
import kotlinx.android.synthetic.main.overview_graphs_layout.overview_bggraph
import kotlinx.android.synthetic.main.overview_graphs_layout.overview_chartMenuButton
import kotlinx.android.synthetic.main.overview_graphs_layout.overview_iobcalculationprogess
import kotlinx.android.synthetic.main.overview_graphs_layout.overview_iobgraph
import kotlinx.android.synthetic.main.overview_info_layout.*
import kotlinx.android.synthetic.main.overview_info_layout.overview_arrow
import kotlinx.android.synthetic.main.overview_info_layout.overview_basebasal
import kotlinx.android.synthetic.main.overview_info_layout.overview_bg
import kotlinx.android.synthetic.main.overview_info_layout.overview_cob
import kotlinx.android.synthetic.main.overview_info_layout.overview_extendedbolus
import kotlinx.android.synthetic.main.overview_info_layout.overview_iob
import kotlinx.android.synthetic.main.overview_info_layout.overview_sensitivity
import kotlinx.android.synthetic.main.overview_loop_pumpstatus_layout.*
import kotlinx.android.synthetic.main.overview_statuslights_layout.*
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
    @Inject lateinit var skinProvider: SkinProvider
    @Inject lateinit var config: Config
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var databaseHelper: DatabaseHelperInterface

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

    private var carbAnimation: AnimationDrawable? = null

    private val graphLock = Object()

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

        return inflater.inflate(skinProvider.activeSkin().overviewLayout(landscape, resourceHelper.gb(R.bool.isTablet), smallHeight), container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        overview_pumpstatus?.setBackgroundColor(resourceHelper.gc(R.color.colorInitializingBorder))

        overview_notifications?.setHasFixedSize(false)
        overview_notifications?.layoutManager = LinearLayoutManager(view.context)
        axisWidth = if (dm.densityDpi <= 120) 3 else if (dm.densityDpi <= 160) 10 else if (dm.densityDpi <= 320) 35 else if (dm.densityDpi <= 420) 50 else if (dm.densityDpi <= 560) 70 else 80
        overview_bggraph?.gridLabelRenderer?.gridColor = resourceHelper.gc(R.color.graphgrid)
        overview_bggraph?.gridLabelRenderer?.reloadStyles()
        overview_bggraph?.gridLabelRenderer?.labelVerticalWidth = axisWidth
        overview_bggraph?.layoutParams?.height = resourceHelper.dpToPx(skinProvider.activeSkin().mainGraphHeight)

        carbAnimation = overview_carbs_icon?.background as AnimationDrawable?
        carbAnimation?.setEnterFadeDuration(1200)
        carbAnimation?.setExitFadeDuration(1200)

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
        overview_apsmode_llayout?.let { unregisterForContextMenu(it) }
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

        overview_apsmode_llayout?.let { registerForContextMenu(overview_apsmode) }
        overview_activeprofile?.let { registerForContextMenu(it) }
        overview_temptarget?.let { registerForContextMenu(it) }
        updateGUI("onResume")
    }

    override fun onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenuInfo?) {
        super.onCreateContextMenu(menu, v, menuInfo)
        overviewMenus.createContextMenu(menu, v)
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        return if (overviewMenus.onContextItemSelected(item, childFragmentManager)) true else super.onContextItemSelected(item)
    }

    override fun onClick(v: View) {
        // try to fix  https://fabric.io/nightscout3/android/apps/info.nightscout.androidaps/issues/5aca7a1536c7b23527eb4be7?time=last-seven-days
        // https://stackoverflow.com/questions/14860239/checking-if-state-is-saved-before-committing-a-fragmenttransaction
        if (childFragmentManager.isStateSaved) return
        activity?.let { activity ->
            when (v.id) {
                R.id.overview_treatmentbutton -> protectionCheck.queryProtection(activity, ProtectionCheck.Protection.BOLUS, UIRunnable(Runnable { TreatmentDialog().show(childFragmentManager, "Overview") }))
                R.id.overview_wizardbutton -> protectionCheck.queryProtection(activity, ProtectionCheck.Protection.BOLUS, UIRunnable(Runnable { WizardDialog().show(childFragmentManager, "Overview") }))
                R.id.overview_insulinbutton -> protectionCheck.queryProtection(activity, ProtectionCheck.Protection.BOLUS, UIRunnable(Runnable { InsulinDialog().show(childFragmentManager, "Overview") }))
                R.id.overview_quickwizardbutton -> protectionCheck.queryProtection(activity, ProtectionCheck.Protection.BOLUS, UIRunnable(Runnable { onClickQuickWizard() }))
                R.id.overview_carbsbutton -> protectionCheck.queryProtection(activity, ProtectionCheck.Protection.BOLUS, UIRunnable(Runnable { CarbsDialog().show(childFragmentManager, "Overview") }))

                R.id.overview_cgmbutton -> {
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
                        CalibrationDialog().show(childFragmentManager, "CalibrationDialog")
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

                R.id.overview_accepttempbutton -> {
                    profileFunction.getProfile() ?: return
                    if (loopPlugin.isEnabled(PluginType.LOOP)) {
                        val lastRun = loopPlugin.lastRun
                        loopPlugin.invoke("Accept temp button", false)
                        if (lastRun?.lastAPSRun != null && lastRun.constraintsProcessed?.isChangeRequested == true) {
                            OKDialog.showConfirmation(activity, resourceHelper.gs(R.string.tempbasal_label), lastRun.constraintsProcessed?.toSpanned()
                                ?: "".toSpanned(), Runnable {
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
            lastRun.constraintsProcessed?.isChangeRequested == true // change is requested

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
        synchronized(graphLock) {
            val numOfGraphs = overviewMenus.setting.size

            if (numOfGraphs != secondaryGraphs.size - 1) {
                //aapsLogger.debug("New secondary graph count ${numOfGraphs-1}")
                // rebuild needed
                secondaryGraphs.clear()
                secondaryGraphsLabel.clear()
                overview_iobgraph.removeAllViews()
                for (i in 1 until numOfGraphs) {
                    val relativeLayout = RelativeLayout(context)
                    relativeLayout.layoutParams = RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

                    val graph = GraphView(context)
                    graph.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, resourceHelper.dpToPx(skinProvider.activeSkin().secondaryGraphHeight)).also { it.setMargins(0, resourceHelper.dpToPx(15), 0, resourceHelper.dpToPx(10)) }
                    graph.gridLabelRenderer?.gridColor = resourceHelper.gc(R.color.graphgrid)
                    graph.gridLabelRenderer?.reloadStyles()
                    graph.gridLabelRenderer?.isHorizontalLabelsVisible = false
                    graph.gridLabelRenderer?.labelVerticalWidth = axisWidth
                    graph.gridLabelRenderer?.numVerticalLabels = 3
                    graph.viewport.backgroundColor = Color.argb(20, 255, 255, 255) // 8% of gray
                    relativeLayout.addView(graph)

                    val label = TextView(context)
                    val layoutParams = RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).also { it.setMargins(resourceHelper.dpToPx(30), resourceHelper.dpToPx(25), 0, 0) }
                    layoutParams.addRule(RelativeLayout.ALIGN_PARENT_TOP)
                    layoutParams.addRule(RelativeLayout.ALIGN_PARENT_LEFT)
                    label.layoutParams = layoutParams
                    relativeLayout.addView(label)
                    secondaryGraphsLabel.add(label)

                    overview_iobgraph.addView(relativeLayout)
                    secondaryGraphs.add(graph)
                }
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

        overview_time?.text = dateUtil.timeString(Date())

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
            overview_arrow?.setImageResource(lastBG.directionToIcon(databaseHelper))
            overview_arrow?.setColorFilter(color)

            val glucoseStatus = GlucoseStatus(injector).glucoseStatusData
            if (glucoseStatus != null) {
                overview_delta?.text = Profile.toSignedUnitsString(glucoseStatus.delta, glucoseStatus.delta * Constants.MGDL_TO_MMOLL, units)
                overview_deltashort?.text = Profile.toSignedUnitsString(glucoseStatus.delta, glucoseStatus.delta * Constants.MGDL_TO_MMOLL, units)
                overview_avgdelta?.text = "${Profile.toSignedUnitsString(glucoseStatus.short_avgdelta, glucoseStatus.short_avgdelta * Constants.MGDL_TO_MMOLL, units)}\n${Profile.toSignedUnitsString(glucoseStatus.long_avgdelta, glucoseStatus.long_avgdelta * Constants.MGDL_TO_MMOLL, units)}"
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
        if (config.APS && pump.pumpDescription.isTempBasalCapable) {
            overview_apsmode?.visibility = View.VISIBLE
            when {
                loopPlugin.isEnabled() && loopPlugin.isSuperBolus                       -> {
                    overview_apsmode?.setImageResource(R.drawable.ic_loop_superbolus)
                    overview_apsmode_text?.text = DateUtil.age(loopPlugin.minutesToEndOfSuspend() * 60000L, true, resourceHelper)
                }

                loopPlugin.isDisconnected                                               -> {
                    overview_apsmode?.setImageResource(R.drawable.ic_loop_disconnected)
                    overview_apsmode_text?.text = DateUtil.age(loopPlugin.minutesToEndOfSuspend() * 60000L, true, resourceHelper)
                }

                loopPlugin.isEnabled() && loopPlugin.isSuspended                        -> {
                    overview_apsmode?.setImageResource(R.drawable.ic_loop_paused)
                    overview_apsmode_text?.text = DateUtil.age(loopPlugin.minutesToEndOfSuspend() * 60000L, true, resourceHelper)
                }

                pump.isSuspended                                                        -> {
                    overview_apsmode?.setImageResource(if (pump.pumpDescription.pumpType == PumpType.Insulet_Omnipod) {
                        // For Omnipod, indicate the pump as disconnected when it's suspended.
                        // The only way to 'reconnect' it, is through the Omnipod tab
                        R.drawable.ic_loop_disconnected
                    } else {
                        R.drawable.ic_loop_paused
                    })
                    overview_apsmode_text?.text = ""
                }

                loopPlugin.isEnabled() && closedLoopEnabled.value() && loopPlugin.isLGS -> {
                    overview_apsmode?.setImageResource(R.drawable.ic_loop_lgs)
                    overview_apsmode_text?.text = ""
                }

                loopPlugin.isEnabled() && closedLoopEnabled.value()                     -> {
                    overview_apsmode?.setImageResource(R.drawable.ic_loop_closed)
                    overview_apsmode_text?.text = ""
                }

                loopPlugin.isEnabled() && !closedLoopEnabled.value()                    -> {
                    overview_apsmode?.setImageResource(R.drawable.ic_loop_open)
                    overview_apsmode_text?.text = ""
                }

                else                                                                    -> {
                    overview_apsmode?.setImageResource(R.drawable.ic_loop_disabled)
                    overview_apsmode_text?.text = ""
                }
            }
        } else {
            overview_apsmode?.visibility = View.GONE
            overview_apsmode_text?.visibility = View.GONE
        }
        val lastRun = loopPlugin.lastRun
        val predictionsAvailable = if (config.APS) lastRun?.request?.hasPredictions == true else config.NSCLIENT

        // temp target
        val tempTarget = treatmentsPlugin.tempTargetFromHistory
        if (tempTarget != null) {
            overview_temptarget?.setTextColor(resourceHelper.gc(R.color.ribbonTextWarning))
            overview_temptarget?.setBackgroundColor(resourceHelper.gc(R.color.ribbonWarning))
            overview_temptarget?.text = Profile.toTargetRangeString(tempTarget.low, tempTarget.high, Constants.MGDL, units) + " " + DateUtil.untilString(tempTarget.end(), resourceHelper)
        } else {
            // If the target is not the same as set in the profile then oref has overridden it
            val targetUsed = lastRun?.constraintsProcessed?.targetBG ?: 0.0

            if (targetUsed != 0.0 && abs(profile.targetMgdl - targetUsed) > 0.01) {
                aapsLogger.debug("Adjusted target. Profile: ${profile.targetMgdl} APS: $targetUsed")
                overview_temptarget?.text = Profile.toTargetRangeString(targetUsed, targetUsed, Constants.MGDL, units)
                overview_temptarget?.setTextColor(resourceHelper.gc(R.color.ribbonTextWarning))
                overview_temptarget?.setBackgroundColor(resourceHelper.gc(R.color.tempTargetBackground))
            } else {
                overview_temptarget?.setTextColor(resourceHelper.gc(R.color.ribbonTextDefault))
                overview_temptarget?.setBackgroundColor(resourceHelper.gc(R.color.ribbonDefault))
                overview_temptarget?.text = Profile.toTargetRangeString(profile.targetLowMgdl, profile.targetHighMgdl, Constants.MGDL, units)
            }
        }

        // Basal, TBR
        val activeTemp = treatmentsPlugin.getTempBasalFromHistory(System.currentTimeMillis())
        overview_basebasal?.text = activeTemp?.let { if (resourceHelper.shortTextMode()) "T:" + activeTemp.toStringVeryShort() else activeTemp.toStringFull() }
            ?: resourceHelper.gs(R.string.pump_basebasalrate, profile.basal)
        overview_basal_llayout?.setOnClickListener {
            var fullText = "${resourceHelper.gs(R.string.basebasalrate_label)}: ${resourceHelper.gs(R.string.pump_basebasalrate, profile.basal)}"
            if (activeTemp != null)
                fullText += "\n" + resourceHelper.gs(R.string.tempbasal_label) + ": " + activeTemp.toStringFull()
            activity?.let {
                OKDialog.show(it, resourceHelper.gs(R.string.basal), fullText)
            }
        }
        overview_basebasal?.setTextColor(activeTemp?.let { resourceHelper.gc(R.color.basal) }
            ?: resourceHelper.gc(R.color.defaulttextcolor))

        overview_basebasal_icon?.setImageResource(R.drawable.ic_cp_basal_no_tbr)
        val percentRate = activeTemp?.tempBasalConvertedToPercent(System.currentTimeMillis(), profile)
            ?: 100
        if (percentRate > 100) overview_basebasal_icon?.setImageResource(R.drawable.ic_cp_basal_tbr_high)
        if (percentRate < 100) overview_basebasal_icon?.setImageResource(R.drawable.ic_cp_basal_tbr_low)

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
        overview_extended_llayout?.visibility = (extendedBolus != null && !pump.isFakingTempsByExtendedBoluses).toVisibility()

        // Active profile
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
            resourceHelper.shortTextMode() ->
                resourceHelper.gs(R.string.formatinsulinunits, bolusIob.iob + basalIob.basaliob)

            else                           ->
                resourceHelper.gs(R.string.formatinsulinunits, bolusIob.iob + basalIob.basaliob) + " (" +
                    resourceHelper.gs(R.string.formatinsulinunits, bolusIob.iob) + "/" +
                    resourceHelper.gs(R.string.formatinsulinunits, basalIob.basaliob) + ")"
        }
        overview_iob_llayout?.setOnClickListener {
            activity?.let {
                OKDialog.show(it, resourceHelper.gs(R.string.iob),
                    resourceHelper.gs(R.string.formatinsulinunits, bolusIob.iob + basalIob.basaliob) + "\n" +
                        resourceHelper.gs(R.string.bolus) + ": " + resourceHelper.gs(R.string.formatinsulinunits, bolusIob.iob) + "\n" +
                        resourceHelper.gs(R.string.basal) + ": " + resourceHelper.gs(R.string.formatinsulinunits, basalIob.basaliob)
                )
            }
        }

        // Status lights
        overview_statuslights?.visibility = (sp.getBoolean(R.string.key_show_statuslights, true) || config.NSCLIENT).toVisibility()
        statusLightHandler.updateStatusLights(careportal_canulaage, careportal_insulinage, careportal_reservoirlevel, careportal_sensorage, careportal_pbage, careportal_batterylevel)

        // cob
        var cobText: String = resourceHelper.gs(R.string.value_unavailable_short)
        val cobInfo = iobCobCalculatorPlugin.getCobInfo(false, "Overview COB")
        if (cobInfo.displayCob != null) {
            cobText = resourceHelper.gs(R.string.format_carbs, cobInfo.displayCob.toInt())
            if (cobInfo.futureCarbs > 0) cobText += "(" + DecimalFormatter.to0Decimal(cobInfo.futureCarbs) + ")"
        }

        if (config.APS && lastRun?.constraintsProcessed != null) {
            if (lastRun.constraintsProcessed!!.carbsReq > 0) {
                //only display carbsreq when carbs have not been entered recently
                if (treatmentsPlugin.lastCarbTime < lastRun.lastAPSRun) {
                    cobText = cobText + " | " + lastRun.constraintsProcessed!!.carbsReq + " " + resourceHelper.gs(R.string.required)
                }
                overview_cob?.text = cobText
                if (carbAnimation?.isRunning == false)
                    carbAnimation?.start()
            } else {
                overview_cob?.text = cobText
                carbAnimation?.stop()
                carbAnimation?.selectDrawable(0)
            }
        } else overview_cob?.text = cobText

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
        if (sp.getBoolean(R.string.key_openapsama_useautosens, false) && constraintChecker.isAutosensModeEnabled().value()) {
            overview_sensitivity_icon?.setImageResource(R.drawable.ic_swap_vert_black_48dp_green)
        } else {
            overview_sensitivity_icon?.setImageResource(R.drawable.ic_x_swap_vert)
        }

        overview_sensitivity?.text =
            iobCobCalculatorPlugin.getLastAutosensData("Overview")?.let { autosensData ->
                String.format(Locale.ENGLISH, "%.0f%%", autosensData.autosensResult.ratio * 100)
            } ?: ""

        // ****** GRAPH *******
        GlobalScope.launch(Dispatchers.Main) {
            overview_bggraph ?: return@launch
            val graphData = GraphData(injector, overview_bggraph, iobCobCalculatorPlugin, treatmentsPlugin)
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
                val apsResult = if (config.APS) lastRun?.constraintsProcessed else NSDeviceStatus.getAPSResult(injector)
                if (predictionsAvailable && apsResult != null && overviewMenus.setting[0][OverviewMenus.CharType.PRE.ordinal]) {
                    var predictionHours = (ceil(apsResult.latestPredictionsTime - System.currentTimeMillis().toDouble()) / (60 * 60 * 1000)).toInt()
                    predictionHours = min(2, predictionHours)
                    predictionHours = max(0, predictionHours)
                    hoursToFetch = rangeToDisplay - predictionHours
                    toTime = calendar.timeInMillis + 100000 // little bit more to avoid wrong rounding - GraphView specific
                    fromTime = toTime - T.hours(hoursToFetch.toLong()).msecs()
                    endTime = toTime + T.hours(predictionHours.toLong()).msecs()
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
                synchronized(graphLock) {
                    for (g in 0 until min(secondaryGraphs.size, overviewMenus.setting.size + 1)) {
                        val secondGraphData = GraphData(injector, secondaryGraphs[g], iobCobCalculatorPlugin, treatmentsPlugin)
                        var useABSForScale = false
                        var useIobForScale = false
                        var useCobForScale = false
                        var useDevForScale = false
                        var useRatioForScale = false
                        var useDSForScale = false
                        var useIAForScale = false
                        when {
                            overviewMenus.setting[g + 1][OverviewMenus.CharType.ABS.ordinal]      -> useABSForScale = true
                            overviewMenus.setting[g + 1][OverviewMenus.CharType.IOB.ordinal]      -> useIobForScale = true
                            overviewMenus.setting[g + 1][OverviewMenus.CharType.COB.ordinal]      -> useCobForScale = true
                            overviewMenus.setting[g + 1][OverviewMenus.CharType.DEV.ordinal]      -> useDevForScale = true
                            overviewMenus.setting[g + 1][OverviewMenus.CharType.SEN.ordinal]      -> useRatioForScale = true
                            overviewMenus.setting[g + 1][OverviewMenus.CharType.ACT.ordinal]      -> useIAForScale = true
                            overviewMenus.setting[g + 1][OverviewMenus.CharType.DEVSLOPE.ordinal] -> useDSForScale = true
                        }

                        if (overviewMenus.setting[g + 1][OverviewMenus.CharType.ABS.ordinal]) secondGraphData.addAbsIob(fromTime, now, useABSForScale, 1.0)
                        if (overviewMenus.setting[g + 1][OverviewMenus.CharType.IOB.ordinal]) secondGraphData.addIob(fromTime, now, useIobForScale, 1.0, overviewMenus.setting[g + 1][OverviewMenus.CharType.PRE.ordinal])
                        if (overviewMenus.setting[g + 1][OverviewMenus.CharType.COB.ordinal]) secondGraphData.addCob(fromTime, now, useCobForScale, if (useCobForScale) 1.0 else 0.5)
                        if (overviewMenus.setting[g + 1][OverviewMenus.CharType.DEV.ordinal]) secondGraphData.addDeviations(fromTime, now, useDevForScale, 1.0)
                        if (overviewMenus.setting[g + 1][OverviewMenus.CharType.SEN.ordinal]) secondGraphData.addRatio(fromTime, now, useRatioForScale, 1.0)
                        if (overviewMenus.setting[g + 1][OverviewMenus.CharType.ACT.ordinal]) secondGraphData.addActivity(fromTime, endTime, useIAForScale, 0.8)
                        if (overviewMenus.setting[g + 1][OverviewMenus.CharType.DEVSLOPE.ordinal] && buildHelper.isDev()) secondGraphData.addDeviationSlope(fromTime, now, useDSForScale, 1.0)

                        // set manual x bounds to have nice steps
                        secondGraphData.formatAxis(fromTime, endTime)
                        secondGraphData.addNowLine(now)
                        secondaryGraphsData.add(secondGraphData)
                    }
                }
            }
            // finally enforce drawing of graphs in UI thread
            graphData.performUpdate()
            synchronized(graphLock) {
                for (g in 0 until min(secondaryGraphs.size, overviewMenus.setting.size + 1)) {
                    secondaryGraphsLabel[g].text = overviewMenus.enabledTypes(g + 1)
                    secondaryGraphs[g].visibility = (
                        overviewMenus.setting[g + 1][OverviewMenus.CharType.ABS.ordinal] ||
                            overviewMenus.setting[g + 1][OverviewMenus.CharType.IOB.ordinal] ||
                            overviewMenus.setting[g + 1][OverviewMenus.CharType.COB.ordinal] ||
                            overviewMenus.setting[g + 1][OverviewMenus.CharType.DEV.ordinal] ||
                            overviewMenus.setting[g + 1][OverviewMenus.CharType.SEN.ordinal] ||
                            overviewMenus.setting[g + 1][OverviewMenus.CharType.ACT.ordinal] ||
                            overviewMenus.setting[g + 1][OverviewMenus.CharType.DEVSLOPE.ordinal]
                        ).toVisibility()
                    secondaryGraphsData[g].performUpdate()
                }
            }
        }
    }
}
