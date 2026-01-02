package app.aaps.wear.interaction.activities

import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventWearToMobile
import app.aaps.core.interfaces.rx.weardata.EventData
import app.aaps.core.interfaces.rx.weardata.LoopStatusData
import app.aaps.core.interfaces.rx.weardata.TempTargetInfo
import app.aaps.core.interfaces.rx.weardata.TargetRange
import app.aaps.core.interfaces.rx.weardata.OapsResultInfo
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.wear.R
import dagger.android.AndroidInjection
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import javax.inject.Inject

class LoopStatusActivity : AppCompatActivity() {

    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var dateUtil: DateUtil

    private val disposable = CompositeDisposable()

    // Main views
    private lateinit var loadingView: ProgressBar
    private lateinit var contentView: ScrollView
    private lateinit var errorView: TextView
    private lateinit var swipeRefresh: SwipeRefreshLayout

    // Header section
    private lateinit var loopModeText: TextView
    private lateinit var apsNameText: TextView

    // Loop Result section (merged loop info + oaps)
    private lateinit var loopResultCard: View
    private lateinit var lastRunEnactCombinedRow: View
    private lateinit var lastRunEnactCombinedValue: TextView
    private lateinit var lastRunRow: View
    private lateinit var lastRunValue: TextView
    private lateinit var lastEnactRow: View
    private lateinit var lastEnactValue: TextView

    // OpenAPS result section
    private lateinit var oapsSmbRow: View
    private lateinit var oapsSmbValue: TextView
    private lateinit var oapsStatusText: TextView
    private lateinit var oapsRateRow: View
    private lateinit var oapsRateValue: TextView
    private lateinit var oapsDurationRow: View
    private lateinit var oapsDurationValue: TextView
    private lateinit var oapsReasonContainer: View
    private lateinit var oapsReasonLabel: TextView
    private lateinit var oapsReasonToggle: TextView
    private lateinit var oapsReasonText: TextView
    private var isReasonExpanded = false

    // Targets section
    private lateinit var targetsCard: View
    private lateinit var tempTargetContainer: View
    private lateinit var tempTargetValue: TextView
    private lateinit var tempTargetDuration: TextView
    private lateinit var defaultRangeRow: View
    private lateinit var defaultRangeValue: TextView
    private lateinit var defaultTargetValue: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        AndroidInjection.inject(this)
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_loop_status)

        initViews()

        // Subscribe to responses
        disposable += rxBus
            .toObservable(EventData.LoopStatusResponse::class.java)
            .subscribe({ event ->
                           aapsLogger.debug(LTag.WEAR, "Received loop status response")
                           runOnUiThread {
                               displayStatus(event.data)
                           }
                       }, { error ->
                           aapsLogger.error(LTag.WEAR, "Error receiving loop status", error)
                           runOnUiThread {
                               showError(getString(R.string.loop_status_error)  )
                           }
                       })
    }

    override fun onResume() {
        super.onResume()
        // Always request fresh data when activity comes to foreground
        requestLoopStatus()
    }

    override fun onDestroy() {
        super.onDestroy()
        disposable.clear()
    }

    private fun requestLoopStatus() {
        // Show loading state (but not the loading view if we're refreshing)
        if (contentView.visibility != View.VISIBLE) {
            loadingView.visibility = View.VISIBLE
            swipeRefresh.visibility = View.GONE
        }
        errorView.visibility = View.GONE

        // Request detailed status from phone
        aapsLogger.debug(LTag.WEAR, "Requesting detailed loop status")
        rxBus.send(EventWearToMobile(EventData.ActionLoopStatusDetailed(System.currentTimeMillis())))
    }

    private fun initViews() {
        loadingView = findViewById(R.id.loading_progress)
        swipeRefresh = findViewById(R.id.swipe_refresh)
        contentView = findViewById(R.id.content_container)
        errorView = findViewById(R.id.error_text)

        // Setup swipe refresh
        swipeRefresh.setOnRefreshListener {
            requestLoopStatus()
        }

        // Set background to transparent/black
        swipeRefresh.setProgressBackgroundColorSchemeColor(
            ContextCompat.getColor(this, R.color.black)
        )

        // Set spinner colors (can use multiple colors for animation)
        swipeRefresh.setColorSchemeColors(
            ContextCompat.getColor(this, R.color.loopClosed),
            ContextCompat.getColor(this, R.color.loopOpen),
            ContextCompat.getColor(this, R.color.tempBasal)
        )

        // Header
        loopModeText = findViewById(R.id.loop_mode_text)
        apsNameText = findViewById(R.id.aps_name_text)

        // Loop Result card
        loopResultCard = findViewById(R.id.loop_result_card)
        lastRunEnactCombinedRow = findViewById(R.id.last_run_enact_combined_row)
        lastRunEnactCombinedValue = findViewById(R.id.last_run_enact_combined_value)
        lastRunRow = findViewById(R.id.last_run_row)
        lastRunValue = findViewById(R.id.last_run_value)
        lastEnactRow = findViewById(R.id.last_enact_row)
        lastEnactValue = findViewById(R.id.last_enact_value)

        // OpenAPS result
        oapsSmbRow = findViewById(R.id.oaps_smb_row)
        oapsSmbValue = findViewById(R.id.oaps_smb_value)
        oapsStatusText = findViewById(R.id.oaps_status_text)
        oapsRateRow = findViewById(R.id.oaps_rate_row)
        oapsRateValue = findViewById(R.id.oaps_rate_value)
        oapsDurationRow = findViewById(R.id.oaps_duration_row)
        oapsDurationValue = findViewById(R.id.oaps_duration_value)
        oapsReasonContainer = findViewById(R.id.oaps_reason_container)
        oapsReasonLabel = findViewById(R.id.oaps_reason_label)
        oapsReasonToggle = findViewById(R.id.oaps_reason_toggle)
        oapsReasonText = findViewById(R.id.oaps_reason_text)

        // Setup click listener for expandable reason
        oapsReasonContainer.setOnClickListener {
            isReasonExpanded = !isReasonExpanded
            oapsReasonText.visibility = if (isReasonExpanded) View.VISIBLE else View.GONE
            oapsReasonToggle.text = if (isReasonExpanded) "▲" else "▼"
        }

        // Targets
        targetsCard = findViewById(R.id.targets_card)
        tempTargetContainer = findViewById(R.id.temp_target_container)
        tempTargetValue = findViewById(R.id.temp_target_value)
        tempTargetDuration = findViewById(R.id.temp_target_duration)
        defaultRangeRow = findViewById(R.id.default_range_row)
        defaultRangeValue = findViewById(R.id.default_range_value)
        defaultTargetValue = findViewById(R.id.default_target_value)
    }

    private fun displayStatus(data: LoopStatusData) {
        // Hide loading, show content
        loadingView.visibility = View.GONE
        errorView.visibility = View.GONE
        swipeRefresh.visibility = View.VISIBLE
        swipeRefresh.isRefreshing = false  // Stop the refresh animation

        // Display all sections
        displayLoopMode(data.loopMode, data.apsName)
        displayLoopInfo(data.lastRun, data.lastEnact)
        data.oapsResult?.let { displayOapsResult(it) }
        displayTargets(data.tempTarget, data.defaultRange)
    }

    private fun showError(message: String) {
        loadingView.visibility = View.GONE
        swipeRefresh.visibility = View.GONE
        swipeRefresh.isRefreshing = false  // Stop the refresh animation
        errorView.visibility = View.VISIBLE
        errorView.text = message
    }

    private fun displayLoopMode(mode: LoopStatusData.LoopMode, apsName: String?) {
        val (text, colorRes) = when (mode) {
            LoopStatusData.LoopMode.CLOSED -> getString(R.string.loop_status_closed).uppercase() to R.color.loopClosed
            LoopStatusData.LoopMode.OPEN -> getString(R.string.loop_status_open).uppercase() to R.color.loopOpen
            LoopStatusData.LoopMode.LGS -> getString(R.string.loop_status_lgs).uppercase() to R.color.loopLGS
            LoopStatusData.LoopMode.DISABLED -> getString(R.string.loop_status_disabled).uppercase() to R.color.loopDisabled
            LoopStatusData.LoopMode.SUSPENDED -> getString(R.string.loop_status_suspended).uppercase() to R.color.loopSuspended
            LoopStatusData.LoopMode.DISCONNECTED -> getString(R.string.loop_status_disconnected).uppercase() to R.color.loopDisconnected
            LoopStatusData.LoopMode.SUPERBOLUS -> getString(R.string.loop_status_superbolus).uppercase() to R.color.loopSuperbolus
            LoopStatusData.LoopMode.UNKNOWN -> getString(R.string.loop_status_unknown).uppercase() to R.color.loopUnknown
        }

        loopModeText.text = text
        loopModeText.setTextColor(ContextCompat.getColor(this, colorRes))

        if (apsName != null) {
            apsNameText.text = apsName
            apsNameText.visibility = View.VISIBLE
        } else {
            apsNameText.visibility = View.GONE
        }
    }

    private fun displayLoopInfo(lastRun: Long?, lastEnact: Long?) {
        if (lastRun != null) {
            val runTimeString = dateUtil.timeString(lastRun)
            val runAgeMs = System.currentTimeMillis() - lastRun
            val runColor = ContextCompat.getColor(this, getAgeColorRes(runAgeMs))

            if (lastEnact != null) {
                val enactTimeString = dateUtil.timeString(lastEnact)
                val enactAgeMs = System.currentTimeMillis() - lastEnact

                val timeWindowMs = 30_000L // 30 seconds

                // Check if enacted is within 30 seconds of suggested (before or after)
                val timeDiff = kotlin.math.abs(lastRun - lastEnact)
                val timesAreClose = timeDiff <= timeWindowMs

                if (timesAreClose) {
                    // Show combined row - use the enacted time since it's the actual action
                    lastRunEnactCombinedRow.visibility = View.VISIBLE
                    lastRunEnactCombinedValue.text = enactTimeString
                    lastRunEnactCombinedValue.setTextColor(ContextCompat.getColor(this, getAgeColorRes(enactAgeMs)))

                    // Hide separate rows
                    lastRunRow.visibility = View.GONE
                    lastEnactRow.visibility = View.GONE
                } else {
                    // Show separate rows
                    lastRunEnactCombinedRow.visibility = View.GONE

                    lastRunRow.visibility = View.VISIBLE
                    lastRunValue.text = runTimeString
                    lastRunValue.setTextColor(runColor)

                    lastEnactRow.visibility = View.VISIBLE
                    lastEnactValue.text = enactTimeString
                    lastEnactValue.setTextColor(ContextCompat.getColor(this, getAgeColorRes(enactAgeMs)))
                }
            } else {
                // Only Last Run exists
                lastRunEnactCombinedRow.visibility = View.GONE
                lastEnactRow.visibility = View.GONE

                lastRunRow.visibility = View.VISIBLE
                lastRunValue.text = runTimeString
                lastRunValue.setTextColor(runColor)
            }
        } else {
            // No Last Run
            lastRunEnactCombinedRow.visibility = View.GONE
            lastEnactRow.visibility = View.GONE

            lastRunRow.visibility = View.VISIBLE
            lastRunValue.text = getString(R.string.loop_status_no_last_run)
            lastRunValue.setTextColor(ContextCompat.getColor(this, R.color.tempTargetDisabled))
        }
    }

    private fun displayOapsResult(result: OapsResultInfo) {
        loopResultCard.visibility = View.VISIBLE

        // Show SMB if present
        result.smbAmount?.let { smb ->
            if (smb > 0.0) {
                oapsSmbRow.visibility = View.VISIBLE
                oapsSmbValue.text = getString(R.string.loop_status_smb, smb)
            } else {
                oapsSmbRow.visibility = View.GONE
            }
        } ?: run {
            oapsSmbRow.visibility = View.GONE
        }

        when {
            // Case 1: Let current temp basal run (or no temp active)
            result.isLetTempRun -> {
                oapsStatusText.text = getString(R.string.loop_status_tbr_continues)  // "Temp basal continues"
                oapsStatusText.setTextColor(ContextCompat.getColor(this, R.color.loopClosed))
                oapsStatusText.visibility = View.VISIBLE

                // Show current TBR details if available
                if (result.rate != null) {
                    oapsRateRow.visibility = View.VISIBLE
                    oapsRateValue.text = getString(R.string.loop_status_tbr_rate, result.rate, result.ratePercent ?: 0)

                    result.duration?.let { duration ->
                        oapsDurationRow.visibility = View.VISIBLE
                        oapsDurationValue.text = getString(R.string.loop_status_tbr_duration_remaining, duration)
                    } ?: run {
                        oapsDurationRow.visibility = View.GONE
                    }
                } else {
                    // No temp basal active - just show status
                    oapsRateRow.visibility = View.GONE
                    oapsDurationRow.visibility = View.GONE
                }
            }

            // Case 2: New temp basal requested
            else -> {
                val percentValue = result.ratePercent ?: 0

                // Check if this is essentially "canceling" (setting to 100% basal)
                if (percentValue == 100) {
                    oapsStatusText.text = getString(R.string.loop_status_tbr_cancel)
                    oapsStatusText.setTextColor(ContextCompat.getColor(this, R.color.loopClosed))
                    oapsStatusText.visibility = View.VISIBLE

                    // Show rate but NOT duration for regular basal
                    result.rate?.let { rate ->
                        oapsRateRow.visibility = View.VISIBLE
                        oapsRateValue.text = getString(R.string.loop_status_tbr_rate, rate, percentValue)
                    } ?: run {
                        oapsRateRow.visibility = View.GONE
                    }

                    oapsDurationRow.visibility = View.GONE  // Never show duration for 100%

                } else {
                    // Normal temp basal change
                    oapsStatusText.visibility = View.GONE

                    result.rate?.let { rate ->
                        oapsRateRow.visibility = View.VISIBLE
                        oapsRateValue.text = getString(R.string.loop_status_tbr_rate, rate, percentValue)
                    } ?: run {
                        oapsRateRow.visibility = View.GONE
                    }

                    result.duration?.let { duration ->
                        oapsDurationRow.visibility = View.VISIBLE
                        oapsDurationValue.text = getString(R.string.loop_status_tbr_duration, duration)
                    } ?: run {
                        oapsDurationRow.visibility = View.GONE
                    }
                }
            }
        }

        // Show reason if available (collapsed by default)
        if (result.reason.isNotEmpty()) {
            oapsReasonContainer.visibility = View.VISIBLE
            oapsReasonText.text = result.reason
            isReasonExpanded = false
            oapsReasonText.visibility = View.GONE
            oapsReasonToggle.text = "▼"
        } else {
            oapsReasonContainer.visibility = View.GONE
        }
    }

    private fun displayTargets(tempTarget: TempTargetInfo?, defaultRange: TargetRange) {
        if (tempTarget != null) {
            tempTargetContainer.visibility = View.VISIBLE
            tempTargetValue.text = "${tempTarget.targetDisplay} ${tempTarget.units}"
            tempTargetDuration.text = getString(R.string.loop_status_tempt_duration, tempTarget.durationMinutes, dateUtil.timeString(tempTarget.endTime))
        } else {
            tempTargetContainer.visibility = View.GONE
        }

        // Hide Range row if low == high
        if (defaultRange.lowDisplay != defaultRange.highDisplay) {
            defaultRangeRow.visibility = View.VISIBLE
            defaultRangeValue.text = "${defaultRange.lowDisplay} - ${defaultRange.highDisplay} ${defaultRange.units}"
        } else {
            defaultRangeRow.visibility = View.GONE
        }

        defaultTargetValue.text = "${defaultRange.targetDisplay} ${defaultRange.units}"
    }

    private fun getAgeColorRes(ageMs: Long): Int {
        val ageMinutes = ageMs / 60000
        return when {
            ageMinutes < 4 -> R.color.loopClosed  // Green < 4 min
            ageMinutes < 10 -> R.color.tempBasal  // Orange < 10 min
            else -> R.color.loopDisabled          // Red >= 10 min
        }
    }
}