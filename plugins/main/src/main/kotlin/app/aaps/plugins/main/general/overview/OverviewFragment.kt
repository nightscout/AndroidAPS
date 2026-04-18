@file:OptIn(kotlinx.coroutines.FlowPreview::class)

package app.aaps.plugins.main.general.overview

import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.AnimationDrawable
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import app.aaps.core.data.configuration.Constants
import app.aaps.core.data.model.EB
import app.aaps.core.data.model.EPS
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.RM
import app.aaps.core.data.model.TB
import app.aaps.core.data.model.TT
import app.aaps.core.interfaces.aps.IobTotal
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.automation.Automation
import app.aaps.core.interfaces.bgQualityCheck.BgQualityCheck
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.configuration.ExternalOptions
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.iob.GlucoseStatusProvider
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.notifications.AapsNotification
import app.aaps.core.interfaces.notifications.NotificationLevel
import app.aaps.core.interfaces.nsclient.NSSettingsStatus
import app.aaps.core.interfaces.nsclient.ProcessedDeviceStatusData
import app.aaps.core.interfaces.overview.LastBgData
import app.aaps.core.interfaces.overview.Overview
import app.aaps.core.interfaces.overview.OverviewData
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.protection.ProtectionCheck
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventBucketedDataCreated
import app.aaps.core.interfaces.rx.events.EventInitializationChanged
import app.aaps.core.interfaces.rx.events.EventPumpStatusChanged
import app.aaps.core.interfaces.rx.events.EventRefreshOverview
import app.aaps.core.interfaces.rx.events.EventUpdateOverviewCalcProgress
import app.aaps.core.interfaces.rx.events.EventUpdateOverviewIobCob
import app.aaps.core.interfaces.rx.events.EventUpdateOverviewSensitivity
import app.aaps.core.interfaces.rx.events.EventWearUpdateTiles
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.interfaces.utils.TrendCalculator
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.BooleanNonKey
import app.aaps.core.keys.DoubleKey
import app.aaps.core.keys.UnitDoubleKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.extensions.directionToIcon
import app.aaps.core.objects.extensions.displayText
import app.aaps.core.objects.extensions.round
import app.aaps.core.objects.profile.ProfileSealed
import app.aaps.core.ui.elements.SingleClickButton
import app.aaps.core.ui.extensions.runOnUiThread
import app.aaps.core.ui.extensions.toVisibility
import app.aaps.core.ui.extensions.toVisibilityKeepSpace
import app.aaps.plugins.main.R
import app.aaps.plugins.main.databinding.OverviewFragmentBinding
import app.aaps.plugins.main.databinding.OverviewNotificationItemBinding
import dagger.android.support.DaggerFragment
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.math.abs
import app.aaps.core.interfaces.notifications.NotificationManager as AapsNotificationManager

class OverviewFragment : DaggerFragment() {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var preferences: Preferences
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var profileUtil: ProfileUtil
    @Inject lateinit var constraintChecker: ConstraintsChecker
    @Inject lateinit var processedDeviceStatusData: ProcessedDeviceStatusData
    @Inject lateinit var nsSettingsStatus: NSSettingsStatus
    @Inject lateinit var loop: Loop
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var iobCobCalculator: IobCobCalculator
    @Inject lateinit var notificationManager: AapsNotificationManager
    @Inject lateinit var config: Config
    @Inject lateinit var protectionCheck: ProtectionCheck
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var trendCalculator: TrendCalculator
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var uel: UserEntryLogger
    @Inject lateinit var persistenceLayer: PersistenceLayer
    @Inject lateinit var glucoseStatusProvider: GlucoseStatusProvider
    @Inject lateinit var overviewData: OverviewData
    @Inject lateinit var overview: Overview
    @Inject lateinit var lastBgData: LastBgData
    @Inject lateinit var automation: Automation
    @Inject lateinit var bgQualityCheck: BgQualityCheck
    @Inject lateinit var uiInteraction: UiInteraction
    @Inject lateinit var decimalFormatter: DecimalFormatter
    @Inject lateinit var commandQueue: CommandQueue

    private val disposable = CompositeDisposable()
    private var scope: CoroutineScope? = null

    private var smallWidth = false
    private var smallHeight = false
    private lateinit var refreshLoop: Runnable
    private var handler = Handler(HandlerThread(this::class.simpleName + "Handler").also { it.start() }.looper)

    private var carbAnimation: AnimationDrawable? = null
    private var lastUserAction = ""

    private var _binding: OverviewFragmentBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    //@SuppressLint("NewApi")
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        OverviewFragmentBinding.inflate(inflater, container, false).also {
            _binding = it
        }.root

    @SuppressLint("SetTextI18n")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // pre-process landscape mode
        //check screen width
        val wm = requireActivity().windowManager.currentWindowMetrics
        val screenWidth = wm.bounds.width()
        val screenHeight = wm.bounds.height()
        smallWidth = screenWidth <= Constants.SMALL_WIDTH
        smallHeight = screenHeight <= Constants.SMALL_HEIGHT

        if (config.AAPSCLIENT1)
            binding.nsclientCard.setBackgroundColor(Color.argb(80, 0xE8, 0xC5, 0x0C))
        if (config.AAPSCLIENT2)
            binding.nsclientCard.setBackgroundColor(Color.argb(80, 0x0F, 0xBB, 0xE0))
        if (config.AAPSCLIENT3)
            binding.nsclientCard.setBackgroundColor(Color.argb(80, 0x4C, 0xAF, 0x50))

        overview.setVersionView(binding.infoLayout.version)

        binding.nsclientCard.visibility = config.AAPSCLIENT.toVisibility()

        binding.notifications.setHasFixedSize(false)
        binding.notifications.layoutManager = LinearLayoutManager(view.context)

        carbAnimation = binding.infoLayout.carbsIcon.background as AnimationDrawable?
        carbAnimation?.setEnterFadeDuration(1200)
        carbAnimation?.setExitFadeDuration(1200)

    }

    override fun onPause() {
        super.onPause()
        scope?.cancel()
        scope = null
        disposable.clear()
        handler.removeCallbacksAndMessages(null)
    }

    override fun onResume() {
        super.onResume()
        val newScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        scope = newScope
        persistenceLayer.observeChanges(EPS::class.java)
            .onEach { scheduleUpdateGUI() }
            .launchIn(newScope)
        persistenceLayer.observeChanges(RM::class.java)
            .onEach { processAps() }
            .launchIn(newScope)
        disposable += activePlugin.activeOverview.overviewBus
            .toObservable(EventUpdateOverviewCalcProgress::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({ updateCalcProgress() }, fabricPrivacy::logException)
        disposable += activePlugin.activeOverview.overviewBus
            .toObservable(EventUpdateOverviewIobCob::class.java)
            .debounce(1L, TimeUnit.SECONDS)
            .observeOn(aapsSchedulers.io)
            .subscribe({ updateIobCob() }, fabricPrivacy::logException)
        disposable += activePlugin.activeOverview.overviewBus
            .toObservable(EventUpdateOverviewSensitivity::class.java)
            .debounce(1L, TimeUnit.SECONDS)
            .observeOn(aapsSchedulers.main)
            .subscribe({ updateSensitivity() }, fabricPrivacy::logException)
        viewLifecycleOwner.lifecycleScope.launch {
            notificationManager.notifications.collectLatest { updateNotification() }
        }
        disposable += rxBus
            .toObservable(EventBucketedDataCreated::class.java)
            .debounce(1L, TimeUnit.SECONDS)
            .observeOn(aapsSchedulers.io)
            .subscribe({ updateBg() }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventRefreshOverview::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({
                           if (it.now) refreshAll()
                           else scheduleUpdateGUI()
                       }, fabricPrivacy::logException)
        merge(
            preferences.observe(BooleanKey.OverviewShowWizardButton).drop(1).map {},
            preferences.observe(UnitDoubleKey.OverviewLowMark).drop(1).map {},
            preferences.observe(UnitDoubleKey.OverviewHighMark).drop(1).map {},
            preferences.observe(BooleanNonKey.AutosensUsedOnMainPhone).drop(1).map {},
            preferences.observe(DoubleKey.AutosensMax).drop(1).map {},
            preferences.observe(DoubleKey.AutosensMin).drop(1).map {},
        ).onEach { scheduleUpdateGUI() }.launchIn(newScope)
        disposable += rxBus
            .toObservable(EventPumpStatusChanged::class.java)
            .observeOn(aapsSchedulers.main)
            .delay(30, TimeUnit.MILLISECONDS, aapsSchedulers.main)
            .subscribe({
                           overviewData.pumpStatus = it.getStatus(requireContext())
                           updatePumpStatus()
                       }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventInitializationChanged::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({ processButtonsVisibility() }, fabricPrivacy::logException)
        persistenceLayer.observeChanges(TT::class.java)
            .onEach { updateTemporaryTarget() }
            .launchIn(newScope)
        persistenceLayer.observeChanges(EB::class.java)
            .onEach { updateExtendedBolus() }
            .launchIn(newScope)
        persistenceLayer.observeChanges(TB::class.java)
            .onEach { updateTemporaryBasal() }
            .launchIn(newScope)
        refreshLoop = Runnable {
            refreshAll()
            handler.postDelayed(refreshLoop, 60 * 1000L)
        }
        handler.postDelayed(refreshLoop, 60 * 1000L)

        handler.post { refreshAll() }
        updatePumpStatus()
        updateCalcProgress()
    }

    fun refreshAll() {
        if (!config.appInitialized) return
        if (_binding == null) return  // View destroyed, skip refresh
        runOnUiThread {
            _binding ?: return@runOnUiThread
            updateTime()
            updateSensitivity()
            updateNotification()
        }
        updateBg()
        updateTemporaryBasal()
        updateExtendedBolus()
        updateIobCob()
        processButtonsVisibility()
        processAps()
        updateProfile()
        updateTemporaryTarget()
    }

    @Synchronized
    override fun onDestroyView() {
        super.onDestroyView()
        // Remove handler callbacks before nulling view to prevent crashes
        handler.removeCallbacksAndMessages(null)
        _binding = null
        carbAnimation?.stop()
        carbAnimation = null
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.looper.quitSafely()
    }

    @SuppressLint("SetTextI18n")
    private fun processButtonsVisibility() {
        viewLifecycleOwner.lifecycleScope.launch {
            iobCobCalculator.ads.lastBg()
            val pump = activePlugin.activePump
            val profile = profileFunction.getProfile()
            profileFunction.getProfileName()
            iobCobCalculator.ads.actualBg()
            var list = ""

            runOnUiThread {
                _binding ?: return@runOnUiThread
                // Automation buttons
                binding.buttonsLayout.userButtonsLayout.removeAllViews()
                val events = automation.userEvents()
                if (!loop.runningMode.isSuspended() && pump.isInitialized() && profile != null && !config.isEnabled(ExternalOptions.SHOW_USER_ACTIONS_ON_WATCH_ONLY))
                    for (event in events)
                        if (event.isEnabled && runBlocking { event.canRun() }) {
                            context?.let { context ->
                                SingleClickButton(context, null, app.aaps.core.ui.R.attr.customBtnStyle).also {
                                    it.setTextColor(rh.gac(context, app.aaps.core.ui.R.attr.userOptionColor))
                                    it.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                                    it.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 0.5f).also { l ->
                                        l.setMargins(rh.dpToPx(1), 0, rh.dpToPx(1), 0)
                                    }
                                    it.setPadding(rh.dpToPx(1), it.paddingTop, rh.dpToPx(1), it.paddingBottom)
                                    it.compoundDrawablePadding = rh.dpToPx(-4)
                                    it.setCompoundDrawablesWithIntrinsicBounds(
                                        null,
                                        rh.gd(app.aaps.core.ui.R.drawable.ic_user_options_24dp).also { icon ->
                                            icon?.setBounds(rh.dpToPx(20), rh.dpToPx(20), rh.dpToPx(20), rh.dpToPx(20))
                                        }, null, null
                                    )
                                    it.text = event.title
                                    it.setOnClickListener {
                                        uiInteraction.showOkCancelDialog(context = context, message = rh.gs(R.string.run_question, event.title), ok = { scope?.launch { automation.processEvent(event) } })
                                    }
                                    binding.buttonsLayout.userButtonsLayout.addView(it)
                                    for (drawable in it.compoundDrawables) {
                                        drawable?.mutate()
                                        drawable?.colorFilter = PorterDuffColorFilter(rh.gac(context, app.aaps.core.ui.R.attr.userOptionColor), PorterDuff.Mode.SRC_IN)
                                    }
                                }
                            }
                            list += event.hashCode()
                        }
                binding.buttonsLayout.userButtonsLayout.visibility = events.isNotEmpty().toVisibility()
            }
            if (list != lastUserAction) {
                // Synchronize Watch Tiles with overview
                lastUserAction = list
                rxBus.send(EventWearUpdateTiles())
            }
        }
    }

    private fun processAps() {
        val pump = activePlugin.activePump

        // aps mode
        fun apsModeSetA11yLabel(stringRes: Int) {
            binding.infoLayout.apsMode.stateDescription = rh.gs(stringRes)
        }

        runOnUiThread {
            _binding ?: return@runOnUiThread
            if (pump.pumpDescription.isTempBasalCapable) {
                binding.infoLayout.apsMode.visibility = View.VISIBLE
                binding.infoLayout.apsModeText.visibility = View.VISIBLE
                when (loop.runningMode) {
                    RM.Mode.SUPER_BOLUS       -> {
                        binding.infoLayout.apsMode.setImageResource(app.aaps.core.ui.R.drawable.ic_loop_superbolus)
                        apsModeSetA11yLabel(app.aaps.core.ui.R.string.superbolus)
                        binding.infoLayout.apsModeText.text = dateUtil.age(loop.minutesToEndOfSuspend() * 60000L, true, rh)
                        binding.infoLayout.apsModeText.visibility = View.VISIBLE
                    }

                    RM.Mode.DISCONNECTED_PUMP -> {
                        binding.infoLayout.apsMode.setImageResource(app.aaps.core.ui.R.drawable.ic_loop_disconnected)
                        apsModeSetA11yLabel(app.aaps.core.ui.R.string.disconnected)
                        binding.infoLayout.apsModeText.text = dateUtil.age(loop.minutesToEndOfSuspend() * 60000L, true, rh)
                        binding.infoLayout.apsModeText.visibility = View.VISIBLE
                    }

                    RM.Mode.SUSPENDED_BY_PUMP -> {
                        binding.infoLayout.apsMode.setImageResource(app.aaps.core.ui.R.drawable.ic_loop_paused)
                        apsModeSetA11yLabel(app.aaps.core.ui.R.string.pumpsuspended)
                        binding.infoLayout.apsModeText.text = rh.gs(app.aaps.core.ui.R.string.pumpsuspended)
                        binding.infoLayout.apsModeText.visibility = View.GONE
                    }

                    RM.Mode.SUSPENDED_BY_USER -> {
                        binding.infoLayout.apsMode.setImageResource(app.aaps.core.ui.R.drawable.ic_loop_paused)
                        apsModeSetA11yLabel(app.aaps.core.ui.R.string.loopsuspended)
                        binding.infoLayout.apsModeText.text = dateUtil.age(loop.minutesToEndOfSuspend() * 60000L, true, rh)
                        binding.infoLayout.apsModeText.visibility = View.VISIBLE
                    }

                    RM.Mode.SUSPENDED_BY_DST  -> {
                        binding.infoLayout.apsMode.setImageResource(app.aaps.core.ui.R.drawable.ic_loop_paused)
                        apsModeSetA11yLabel(app.aaps.core.ui.R.string.loop_suspended_by_dst)
                        binding.infoLayout.apsModeText.text = dateUtil.age(loop.minutesToEndOfSuspend() * 60000L, true, rh)
                        binding.infoLayout.apsModeText.visibility = View.VISIBLE
                    }

                    RM.Mode.CLOSED_LOOP_LGS   -> {
                        binding.infoLayout.apsMode.setImageResource(app.aaps.core.ui.R.drawable.ic_loop_lgs)
                        apsModeSetA11yLabel(app.aaps.core.ui.R.string.uel_lgs_loop_mode)
                        binding.infoLayout.apsModeText.visibility = View.GONE
                    }

                    RM.Mode.CLOSED_LOOP       -> {
                        binding.infoLayout.apsMode.setImageResource(app.aaps.core.objects.R.drawable.ic_loop_closed)
                        apsModeSetA11yLabel(app.aaps.core.ui.R.string.closedloop)
                        binding.infoLayout.apsModeText.visibility = View.GONE
                    }

                    RM.Mode.OPEN_LOOP         -> {
                        binding.infoLayout.apsMode.setImageResource(app.aaps.core.ui.R.drawable.ic_loop_open)
                        apsModeSetA11yLabel(app.aaps.core.ui.R.string.openloop)
                        binding.infoLayout.apsModeText.visibility = View.GONE
                    }

                    RM.Mode.DISABLED_LOOP     -> {
                        binding.infoLayout.apsMode.setImageResource(app.aaps.core.ui.R.drawable.ic_loop_disabled)
                        apsModeSetA11yLabel(R.string.disabled_loop)
                        binding.infoLayout.apsModeText.visibility = View.GONE
                    }

                    RM.Mode.RESUME            -> error("Invalid mode")
                }
            } else {
                // loop not supported by pump, hide aps mode
                binding.infoLayout.apsMode.visibility = View.GONE
                binding.infoLayout.apsModeText.visibility = View.GONE
            }

            // pump status from ns
            binding.pump.text = processedDeviceStatusData.pumpStatus(nsSettingsStatus)
            binding.pump.setOnClickListener { activity?.let { uiInteraction.showOkDialog(context = it, title = rh.gs(app.aaps.core.ui.R.string.pump), message = processedDeviceStatusData.extendedPumpStatusHtml) } }

            // OpenAPS status from ns
            binding.openaps.text = processedDeviceStatusData.openApsStatus
            binding.openaps.setOnClickListener { activity?.let { uiInteraction.showOkDialog(context = it, title = rh.gs(app.aaps.core.ui.R.string.openaps), message = processedDeviceStatusData.extendedOpenApsStatusHtml) } }

            // Uploader status from ns
            binding.uploader.text = processedDeviceStatusData.uploaderStatusSpanned
            binding.uploader.setOnClickListener { activity?.let { uiInteraction.showOkDialog(context = it, title = rh.gs(app.aaps.core.ui.R.string.uploader), message = processedDeviceStatusData.extendedUploaderStatusHtml) } }
        }
    }

    var task: Runnable? = null

    private fun scheduleUpdateGUI() {
        class UpdateRunnable : Runnable {

            override fun run() {
                refreshAll()
                task = null
            }
        }
        task?.let { handler.removeCallbacks(it) }
        task = UpdateRunnable()
        task?.let { handler.postDelayed(it, 500) }
    }

    @SuppressLint("SetTextI18n")
    fun updateBg() {
        val lastBg = lastBgData.lastBg()
        val lastBgColor = lastBgData.lastBgColor(context)
        val isActualBg = lastBgData.isActualBg()
        val glucoseStatus = glucoseStatusProvider.glucoseStatusData
        val trendDescription = trendCalculator.getTrendDescription(iobCobCalculator.ads)
        val trendArrow = trendCalculator.getTrendArrow(iobCobCalculator.ads)
        val lastBgDescription = lastBgData.lastBgDescription()
        runOnUiThread {
            _binding ?: return@runOnUiThread
            binding.infoLayout.bg.text = profileUtil.fromMgdlToStringInUnits(lastBg?.recalculated)
            binding.infoLayout.bg.setTextColor(lastBgColor)
            trendArrow?.let { binding.infoLayout.arrow.setImageResource(it.directionToIcon()) }
            binding.infoLayout.arrow.visibility = (trendArrow != null).toVisibilityKeepSpace()
            binding.infoLayout.arrow.setColorFilter(lastBgColor)
            binding.infoLayout.arrow.contentDescription = lastBgDescription + " " + rh.gs(app.aaps.core.ui.R.string.and) + " " + trendDescription

            if (glucoseStatus != null) {
                binding.infoLayout.deltaLarge.text = profileUtil.fromMgdlToSignedStringInUnits(glucoseStatus.delta)
                binding.infoLayout.deltaLarge.setTextColor(lastBgColor)
                binding.infoLayout.delta.text = profileUtil.fromMgdlToSignedStringInUnits(glucoseStatus.delta)
                binding.infoLayout.avgDelta.text = profileUtil.fromMgdlToSignedStringInUnits(glucoseStatus.shortAvgDelta)
                binding.infoLayout.longAvgDelta.text = profileUtil.fromMgdlToSignedStringInUnits(glucoseStatus.longAvgDelta)
            } else {
                binding.infoLayout.deltaLarge.text = ""
                binding.infoLayout.delta.text = "Δ " + rh.gs(app.aaps.core.ui.R.string.value_unavailable_short)
                binding.infoLayout.avgDelta.text = ""
                binding.infoLayout.longAvgDelta.text = ""
            }

            // strike through if BG is old
            binding.infoLayout.bg.paintFlags =
                if (!isActualBg) binding.infoLayout.bg.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                else binding.infoLayout.bg.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()

            val outDate = (if (!isActualBg) rh.gs(R.string.a11y_bg_outdated) else "")
            binding.infoLayout.bg.contentDescription = rh.gs(R.string.a11y_blood_glucose) + " " + binding.infoLayout.bg.text.toString() + " " + lastBgDescription + " " + outDate

            binding.infoLayout.timeAgo.text = dateUtil.minOrSecAgo(rh, lastBg?.timestamp)
            binding.infoLayout.timeAgo.contentDescription = dateUtil.minAgoLong(rh, lastBg?.timestamp)
            binding.infoLayout.timeAgoShort.text = dateUtil.minAgoShort(lastBg?.timestamp)

            val qualityIcon = bgQualityCheck.icon()
            if (qualityIcon != 0) {
                binding.infoLayout.bgQuality.visibility = View.VISIBLE
                binding.infoLayout.bgQuality.setImageResource(qualityIcon)
                binding.infoLayout.bgQuality.contentDescription = rh.gs(R.string.a11y_bg_quality) + " " + bgQualityCheck.stateDescription()
                binding.infoLayout.bgQuality.setOnClickListener {
                    context?.let { context -> uiInteraction.showOkDialog(context = context, title = rh.gs(R.string.data_status), message = bgQualityCheck.message) }
                }
            } else {
                binding.infoLayout.bgQuality.visibility = View.GONE
            }
            binding.infoLayout.simpleMode.visibility = preferences.simpleMode.toVisibility()
        }
    }

    private fun updateProfile() {
        viewLifecycleOwner.lifecycleScope.launch {
            val profile = profileFunction.getProfile()
            val profileNameWithTime = profileFunction.getProfileNameWithRemainingTime()
            runOnUiThread {
                _binding ?: return@runOnUiThread
                val profileBackgroundColor = profile?.let {
                    if (it is ProfileSealed.EPS) {
                        if (it.value.originalPercentage != 100 || it.value.originalTimeshift != 0L || it.value.originalDuration != 0L)
                            app.aaps.core.ui.R.attr.ribbonWarningColor
                        else app.aaps.core.ui.R.attr.ribbonDefaultColor
                    } else app.aaps.core.ui.R.attr.ribbonDefaultColor
                } ?: app.aaps.core.ui.R.attr.ribbonCriticalColor

                val profileTextColor = profile?.let {
                    if (it is ProfileSealed.EPS) {
                        if (it.value.originalPercentage != 100 || it.value.originalTimeshift != 0L || it.value.originalDuration != 0L)
                            app.aaps.core.ui.R.attr.ribbonTextWarningColor
                        else app.aaps.core.ui.R.attr.ribbonTextDefaultColor
                    } else app.aaps.core.ui.R.attr.ribbonTextDefaultColor
                } ?: app.aaps.core.ui.R.attr.ribbonTextDefaultColor
                setRibbon(binding.activeProfile, profileTextColor, profileBackgroundColor, profileNameWithTime)
            }
        }
    }

    private fun updateTemporaryBasal() {
        val temporaryBasalText = overviewData.temporaryBasalText()
        val temporaryBasalColor = overviewData.temporaryBasalColor(context)
        val temporaryBasalIcon = overviewData.temporaryBasalIcon()
        val temporaryBasalDialogText = overviewData.temporaryBasalDialogText()
        runOnUiThread {
            _binding ?: return@runOnUiThread
            binding.infoLayout.baseBasal.text = temporaryBasalText
            binding.infoLayout.baseBasal.setTextColor(temporaryBasalColor)
            binding.infoLayout.baseBasalIcon.setImageResource(temporaryBasalIcon)
            binding.infoLayout.basalLayout.setOnClickListener { activity?.let { uiInteraction.showOkDialog(context = it, title = rh.gs(app.aaps.core.ui.R.string.basal), message = temporaryBasalDialogText) } }
        }
    }

    private fun updateExtendedBolus() {
        val pump = activePlugin.activePump
        val extendedBolus = runBlocking { persistenceLayer.getExtendedBolusActiveAt(dateUtil.now()) }
        val extendedBolusText = overviewData.extendedBolusText()
        val extendedBolusDialogText = overviewData.extendedBolusDialogText()
        runOnUiThread {
            _binding ?: return@runOnUiThread
            binding.infoLayout.extendedBolus.text = extendedBolusText
            binding.infoLayout.extendedLayout.setOnClickListener { activity?.let { uiInteraction.showOkDialog(context = it, title = rh.gs(app.aaps.core.ui.R.string.extended_bolus), message = extendedBolusDialogText) } }
            binding.infoLayout.extendedLayout.visibility = (extendedBolus != null && !pump.isFakingTempsByExtendedBoluses).toVisibility()
        }
    }

    private fun updateTime() {
        _binding ?: return
        binding.infoLayout.time.text = dateUtil.timeString(dateUtil.now())
    }

    private fun bolusIob(): IobTotal = runBlocking { iobCobCalculator.calculateIobFromBolus() }.round()
    private fun basalIob(): IobTotal = runBlocking { iobCobCalculator.calculateIobFromTempBasalsIncludingConvertedExtended() }.round()
    private fun iobText(): String =
        rh.gs(app.aaps.core.ui.R.string.format_insulin_units, bolusIob().iob + basalIob().basaliob)

    private fun iobDialogText(): String =
        rh.gs(app.aaps.core.ui.R.string.format_insulin_units, bolusIob().iob + basalIob().basaliob) + "\n" +
            rh.gs(app.aaps.core.ui.R.string.bolus) + ": " + rh.gs(app.aaps.core.ui.R.string.format_insulin_units, bolusIob().iob) + "\n" +
            rh.gs(app.aaps.core.ui.R.string.basal) + ": " + rh.gs(app.aaps.core.ui.R.string.format_insulin_units, basalIob().basaliob)

    private fun updateIobCob() {
        val iobText = iobText()
        val iobDialogText = iobDialogText()
        val displayText = runBlocking { iobCobCalculator.getCobInfo("Overview COB") }.displayText(rh, decimalFormatter)
        val lastCarbsTime = runBlocking { persistenceLayer.getNewestCarbs() }?.timestamp ?: 0L
        runOnUiThread {
            _binding ?: return@runOnUiThread
            binding.infoLayout.iob.text = iobText
            binding.infoLayout.iobLayout.setOnClickListener { activity?.let { uiInteraction.showOkDialog(context = it, title = rh.gs(app.aaps.core.ui.R.string.iob), message = iobDialogText) } }
            // cob
            var cobText = displayText ?: rh.gs(app.aaps.core.ui.R.string.value_unavailable_short)

            val constraintsProcessed = loop.lastRun?.constraintsProcessed
            val lastRun = loop.lastRun
            if (config.APS && constraintsProcessed != null && lastRun != null) {
                if (constraintsProcessed.carbsReq > 0) {
                    //only display carbsreq when carbs have not been entered recently
                    if (lastCarbsTime < lastRun.lastAPSRun) {
                        cobText += "\n" + constraintsProcessed.carbsReq + " " + rh.gs(app.aaps.core.ui.R.string.required)
                    }
                    if (carbAnimation?.isRunning == false)
                        carbAnimation?.start()
                } else {
                    carbAnimation?.stop()
                    carbAnimation?.selectDrawable(0)
                }
            }
            binding.infoLayout.cob.text = cobText
        }
    }

    @SuppressLint("SetTextI18n")
    fun updateTemporaryTarget() {
        if (_binding == null) return  // View destroyed, skip update
        viewLifecycleOwner.lifecycleScope.launch {
            val units = profileFunction.getUnits()
            val tempTarget = persistenceLayer.getTemporaryTargetActiveAt(dateUtil.now())
            _binding ?: return@launch
            if (tempTarget != null) {
                setRibbon(
                    binding.tempTarget,
                    app.aaps.core.ui.R.attr.ribbonTextWarningColor,
                    app.aaps.core.ui.R.attr.ribbonWarningColor,
                    profileUtil.toTargetRangeString(tempTarget.lowTarget, tempTarget.highTarget, GlucoseUnit.MGDL, units) + " " + dateUtil.untilString(tempTarget.end, rh)
                )
            } else {
                profileFunction.getProfile()?.let { profile ->
                    // If the target is not the same as set in the profile then oref has overridden it
                    val targetUsed =
                        if (config.APS) loop.lastRun?.constraintsProcessed?.targetBG ?: 0.0
                        else if (config.AAPSCLIENT) processedDeviceStatusData.getAPSResult()?.targetBG ?: 0.0
                        else 0.0

                    if (targetUsed != 0.0 && abs(profile.getTargetMgdl() - targetUsed) > 0.01) {
                        aapsLogger.debug("Adjusted target. Profile: ${profile.getTargetMgdl()} APS: $targetUsed")
                        setRibbon(
                            binding.tempTarget,
                            app.aaps.core.ui.R.attr.ribbonTextWarningColor,
                            app.aaps.core.ui.R.attr.tempTargetBackgroundColor,
                            profileUtil.toTargetRangeString(targetUsed, targetUsed, GlucoseUnit.MGDL, units)
                        )
                    } else {
                        setRibbon(
                            binding.tempTarget,
                            app.aaps.core.ui.R.attr.ribbonTextDefaultColor,
                            app.aaps.core.ui.R.attr.ribbonDefaultColor,
                            profileUtil.toTargetRangeString(profile.getTargetLowMgdl(), profile.getTargetHighMgdl(), GlucoseUnit.MGDL, units)
                        )
                    }
                }
            }
        }
    }

    private fun setRibbon(view: TextView, attrResText: Int, attrResBack: Int, text: String) {
        with(view) {
            setText(text)
            setBackgroundColor(rh.gac(context, attrResBack))
            setTextColor(rh.gac(context, attrResText))
            compoundDrawables[0]?.setTint(rh.gac(context, attrResText))
        }
    }

    private fun updateCalcProgress() {
        _binding ?: return
        binding.progressBar.visibility = (overviewData.calcProgressPct != 100).toVisibility()
        binding.progressBar.progress = overviewData.calcProgressPct
    }

    private fun updateSensitivity() {
        _binding ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            val lastAutosensData = iobCobCalculator.ads.getLastAutosensData("Overview", aapsLogger, dateUtil)
            val lastAutosensRatio = lastAutosensData?.let { it.autosensResult.ratio * 100 }
            if (config.AAPSCLIENT && preferences.get(BooleanNonKey.AutosensUsedOnMainPhone) ||
                !config.AAPSCLIENT && constraintChecker.isAutosensModeEnabled().value()
            ) {
                binding.infoLayout.sensitivityIcon.setImageResource(
                    lastAutosensRatio?.let {
                        when {
                            it > 100.0 -> app.aaps.core.objects.R.drawable.ic_as_above
                            it < 100.0 -> app.aaps.core.objects.R.drawable.ic_as_below
                            else       -> app.aaps.core.objects.R.drawable.ic_swap_vert_black_48dp_green
                        }
                    }
                        ?: app.aaps.core.objects.R.drawable.ic_swap_vert_black_48dp_green
                )
            } else {
                binding.infoLayout.sensitivityIcon.setImageResource(
                    lastAutosensRatio?.let {
                        when {
                            it > 100.0 -> app.aaps.core.objects.R.drawable.ic_x_as_above
                            it < 100.0 -> app.aaps.core.objects.R.drawable.ic_x_as_below
                            else       -> app.aaps.core.objects.R.drawable.ic_x_swap_vert
                        }
                    }
                        ?: app.aaps.core.objects.R.drawable.ic_x_swap_vert
                )
            }

            // Show variable sensitivity
            val profile = profileFunction.getProfile()
            val request = loop.lastRun?.request
            val isfMgdl = profile?.getProfileIsfMgdl()
            val isfForCarbs = profile?.getIsfMgdlForCarbs(dateUtil.now(), "Overview", config, processedDeviceStatusData)
            val variableSens =
                if (config.APS) request?.variableSens ?: 0.0
                else if (config.AAPSCLIENT) processedDeviceStatusData.getAPSResult()?.variableSens ?: 0.0
                else 0.0
            val ratioUsed = request?.autosensResult?.ratio ?: 1.0

            if (variableSens != isfMgdl && variableSens != 0.0 && isfMgdl != null) {
                val okDialogText: ArrayList<String> = ArrayList()
                val overViewText: ArrayList<String> = ArrayList()
                val autoSensHiddenRange = 0.0             //Hide Autosens value if equals 100%
                val autoSensMax = 100.0 + (preferences.get(DoubleKey.AutosensMax) - 1.0) * autoSensHiddenRange * 100.0
                val autoSensMin = 100.0 + (preferences.get(DoubleKey.AutosensMin) - 1.0) * autoSensHiddenRange * 100.0
                lastAutosensRatio?.let {
                    if (it !in autoSensMin..autoSensMax)
                        overViewText.add(rh.gs(app.aaps.core.ui.R.string.autosens_short, it))
                    okDialogText.add(rh.gs(app.aaps.core.ui.R.string.autosens_long, it))
                }
                overViewText.add(
                    String.format(
                        Locale.getDefault(), "%1$.1f→%2$.1f",
                        profileUtil.fromMgdlToUnits(isfMgdl, profileFunction.getUnits()),
                        profileUtil.fromMgdlToUnits(variableSens, profileFunction.getUnits())
                    )
                )
                binding.infoLayout.sensitivity.text = overViewText.joinToString("\n")
                binding.infoLayout.sensitivity.visibility = View.VISIBLE
                binding.infoLayout.variableSensitivity.visibility = View.GONE
                if (ratioUsed != 1.0 && ratioUsed != lastAutosensData?.autosensResult?.ratio)
                    okDialogText.add(rh.gs(app.aaps.core.ui.R.string.algorithm_long, ratioUsed * 100))
                okDialogText.add(rh.gs(app.aaps.core.ui.R.string.isf_for_carbs, profileUtil.fromMgdlToUnits(isfForCarbs ?: 0.0, profileFunction.getUnits())))
                if (config.APS) {
                    val aps = activePlugin.activeAPS
                    aps?.getSensitivityOverviewString()?.let {
                        okDialogText.add(it)
                    }
                }
                binding.infoLayout.asLayout.setOnClickListener { activity?.let { uiInteraction.showOkDialog(context = it, title = rh.gs(app.aaps.core.ui.R.string.sensitivity), message = okDialogText.joinToString("\n")) } }

            } else {
                binding.infoLayout.sensitivity.text =
                    lastAutosensData?.let {
                        rh.gs(app.aaps.core.ui.R.string.autosens_short, it.autosensResult.ratio * 100)
                    } ?: ""
                binding.infoLayout.variableSensitivity.visibility = View.GONE
                binding.infoLayout.sensitivity.visibility = View.VISIBLE
            }
        }
    }

    private fun updatePumpStatus() {
        _binding ?: return
        val status = overviewData.pumpStatus
        binding.pumpStatus.text = status
        binding.pumpStatusLayout.visibility = (status != "").toVisibility()
    }

    private fun updateNotification() {
        _binding ?: return
        notificationManager.cleanUp()
        val notifications = notificationManager.notifications.value
        if (notifications.isNotEmpty()) {
            binding.notifications.adapter = NotificationRecyclerViewAdapter(notifications)
            binding.notifications.visibility = View.VISIBLE
        } else {
            binding.notifications.visibility = View.GONE
        }
    }

    private inner class NotificationRecyclerViewAdapter(
        private val notificationsList: List<AapsNotification>
    ) : androidx.recyclerview.widget.RecyclerView.Adapter<NotificationRecyclerViewAdapter.NotificationsViewHolder>() {

        override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): NotificationsViewHolder =
            NotificationsViewHolder(LayoutInflater.from(viewGroup.context).inflate(R.layout.overview_notification_item, viewGroup, false))

        override fun onBindViewHolder(holder: NotificationsViewHolder, position: Int) {
            val notification = notificationsList[position]
            holder.binding.dismiss.tag = notification
            val buttonTextRes = notification.actions.firstOrNull()?.buttonTextRes
            if (buttonTextRes != null && buttonTextRes != 0) holder.binding.dismiss.setText(buttonTextRes)
            else holder.binding.dismiss.setText(app.aaps.core.ui.R.string.snooze)
            @Suppress("SetTextI18n")
            holder.binding.text.text = dateUtil.timeString(notification.date) + " " + notification.text
            when (notification.level) {
                NotificationLevel.URGENT       -> holder.binding.cv.setBackgroundColor(rh.gac(app.aaps.core.ui.R.attr.notificationUrgent))
                NotificationLevel.NORMAL       -> holder.binding.cv.setBackgroundColor(rh.gac(app.aaps.core.ui.R.attr.notificationNormal))
                NotificationLevel.LOW          -> holder.binding.cv.setBackgroundColor(rh.gac(app.aaps.core.ui.R.attr.notificationLow))
                NotificationLevel.INFO         -> holder.binding.cv.setBackgroundColor(rh.gac(app.aaps.core.ui.R.attr.notificationInfo))
                NotificationLevel.ANNOUNCEMENT -> holder.binding.cv.setBackgroundColor(rh.gac(app.aaps.core.ui.R.attr.notificationAnnouncement))
            }
        }

        override fun getItemCount(): Int = notificationsList.size

        inner class NotificationsViewHolder(itemView: View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(itemView) {

            val binding = OverviewNotificationItemBinding.bind(itemView)

            init {
                binding.dismiss.setOnClickListener {
                    val notification = it.tag as AapsNotification
                    notification.actions.firstOrNull()?.action?.invoke()
                    notificationManager.dismiss(notification.id)
                }
            }
        }
    }

}
