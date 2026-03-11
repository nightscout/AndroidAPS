package app.aaps.pump.insight

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import app.aaps.core.interfaces.queue.Callback
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.ui.extensions.runOnUiThread
import app.aaps.core.ui.extensions.toVisibility
import app.aaps.pump.insight.databinding.LocalInsightFragmentBinding
import app.aaps.pump.insight.descriptors.ActiveBolus
import app.aaps.pump.insight.descriptors.BolusType
import app.aaps.pump.insight.descriptors.InsightState
import app.aaps.pump.insight.descriptors.OperatingMode
import app.aaps.pump.insight.events.EventLocalInsightUpdateGUI
import dagger.android.support.DaggerFragment
import io.reactivex.rxjava3.disposables.CompositeDisposable
import javax.inject.Inject

@SuppressLint("SetTextI18n")
class InsightFragment : DaggerFragment(), View.OnClickListener {

    @Inject lateinit var insightPlugin: InsightPlugin
    @Inject lateinit var commandQueue: CommandQueue
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var aapsSchedulers: AapsSchedulers

    private var _binding: LocalInsightFragmentBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    private val disposable = CompositeDisposable()
    private var operatingModeCallback: Callback? = null
    private var tbrOverNotificationCallback: Callback? = null
    private var refreshCallback: Callback? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = LocalInsightFragmentBinding.inflate(inflater, container, false)
        binding.tbrOverNotification.setOnClickListener(this)
        binding.operatingModeButton.setOnClickListener(this)
        binding.refresh.setOnClickListener(this)
        return binding.root
    }

    @Synchronized override fun onResume() {
        super.onResume()
        disposable.add(rxBus
                           .toObservable(EventLocalInsightUpdateGUI::class.java)
                           .observeOn(aapsSchedulers.main)
                           .subscribe({ updateGUI() }) { throwable: Throwable? -> fabricPrivacy.logException(throwable!!) }
        )
        updateGUI()
    }

    @Synchronized override fun onPause() {
        super.onPause()
        disposable.clear()
    }

    @Synchronized override fun onDestroy() {
        super.onDestroy()
        disposable.clear()
    }

    @Synchronized override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onClick(v: View) {
        when (v.id) {
            R.id.operating_mode_button -> {
                if (insightPlugin.operatingMode != null) {
                    binding.operatingModeButton.isEnabled = false
                    operatingModeCallback = object : Callback() {
                        override fun run() {
                            runOnUiThread {
                                operatingModeCallback = null
                                updateGUI()
                            }
                        }
                    }
                    when (insightPlugin.operatingMode) {
                        OperatingMode.PAUSED, OperatingMode.STOPPED -> commandQueue.startPump(operatingModeCallback)
                        OperatingMode.STARTED                       -> commandQueue.stopPump(operatingModeCallback)
                        else                                        -> Unit
                    }
                }
            }

            R.id.tbr_over_notification -> {
                val notificationBlock = insightPlugin.tBROverNotificationBlock
                if (notificationBlock != null) {
                    binding.tbrOverNotification.isEnabled = false
                    tbrOverNotificationCallback = object : Callback() {
                        override fun run() {
                            runOnUiThread {
                                tbrOverNotificationCallback = null
                                updateGUI()
                            }
                        }
                    }
                    commandQueue.setTBROverNotification(tbrOverNotificationCallback, !notificationBlock.isEnabled)
                }
            }

            R.id.refresh               -> {
                binding.refresh.isEnabled = false
                refreshCallback = object : Callback() {
                    override fun run() {
                        runOnUiThread {
                            refreshCallback = null
                            updateGUI()
                        }
                    }
                }
                commandQueue.readStatus("InsightRefreshButton", refreshCallback)
            }
        }
    }

    fun updateGUI() {
        _binding ?: return
        binding.statusItemContainer.removeAllViews()
        if (!insightPlugin.isInitialized()) {
            binding.operatingModeButton.visibility = View.GONE
            binding.tbrOverNotification.visibility = View.GONE
            binding.refresh.visibility = View.GONE
            return
        }
        binding.refresh.visibility = View.VISIBLE
        binding.refresh.isEnabled = refreshCallback == null
        val notificationBlock = insightPlugin.tBROverNotificationBlock
        binding.tbrOverNotification.visibility = if (notificationBlock == null) View.GONE else View.VISIBLE
        if (notificationBlock != null) binding.tbrOverNotification.setText(if (notificationBlock.isEnabled) R.string.disable_tbr_over_notification else R.string.enable_tbr_over_notification)
        binding.tbrOverNotification.isEnabled = tbrOverNotificationCallback == null
        getConnectionStatusItem()
        getLastConnectedItem()
        getOperatingModeItem()
        getBatteryStatusItem()
        getCartridgeStatusItem()
        getTDDItems()
        getBaseBasalRateItem()
        getTBRItem()
        getLastBolusItem()
        getBolusItems()
    }

    private fun getConnectionStatusItem() {
        insightPlugin.connectionService?.let {
            val string = when (it.state) {
                InsightState.NOT_PAIRED                 -> R.string.not_paired
                InsightState.DISCONNECTED               -> app.aaps.core.ui.R.string.disconnected
                InsightState.CONNECTING,
                InsightState.SATL_CONNECTION_REQUEST,
                InsightState.SATL_KEY_REQUEST,
                InsightState.SATL_SYN_REQUEST,
                InsightState.SATL_VERIFY_CONFIRM_REQUEST,
                InsightState.SATL_VERIFY_DISPLAY_REQUEST,
                InsightState.APP_ACTIVATE_PARAMETER_SERVICE,
                InsightState.APP_ACTIVATE_STATUS_SERVICE,
                InsightState.APP_BIND_MESSAGE,
                InsightState.APP_CONNECT_MESSAGE,
                InsightState.APP_FIRMWARE_VERSIONS,
                InsightState.APP_SYSTEM_IDENTIFICATION,
                InsightState.AWAITING_CODE_CONFIRMATION -> app.aaps.core.ui.R.string.connecting

                InsightState.CONNECTED                  -> app.aaps.core.interfaces.R.string.connected
                InsightState.RECOVERING                 -> R.string.recovering
            }
            binding.statusLine.visibility = View.VISIBLE
            binding.status.text = rh.gs(string)
            binding.recoveryDurationLine.visibility = (it.state == InsightState.RECOVERING).toVisibility()
            if (it.state == InsightState.RECOVERING) {
                binding.recoveryDuration.text = rh.gs(app.aaps.core.ui.R.string.secs, it.recoveryDuration / 1000)
            }
        }
    }

    private fun getLastConnectedItem() {
        insightPlugin.connectionService?.let {
            when (it.state) {
                InsightState.CONNECTED, InsightState.NOT_PAIRED -> {
                    binding.lastConnectedLine.visibility = View.GONE
                    return
                }

                else                                            -> {
                    val lastConnection = it.lastConnected
                    if (lastConnection == 0L) return
                    val agoMsc = System.currentTimeMillis() - lastConnection
                    val lastConnectionMinAgo = agoMsc / 60.0 / 1000.0
                    val ago: String = if (lastConnectionMinAgo < 60) {
                        dateUtil.minAgo(rh, lastConnection)
                    } else {
                        dateUtil.hourAgo(lastConnection, rh)
                    }
                    binding.lastConnectedLine.visibility = View.VISIBLE
                    binding.lastConnected.text = rh.gs(R.string.last_connection, dateUtil.timeString(lastConnection), ago)
                }
            }

        }
    }

    private fun getOperatingModeItem() {
        if (insightPlugin.operatingMode == null) {
            binding.operatingModeButton.visibility = View.GONE
            binding.operatingModeLine.visibility = View.GONE
            return
        }
        var string = 0
        if (ENABLE_OPERATING_MODE_BUTTON) binding.operatingModeButton.visibility = View.VISIBLE
        binding.operatingModeButton.isEnabled = operatingModeCallback == null
        when (insightPlugin.operatingMode) {
            OperatingMode.STARTED -> {
                binding.operatingModeButton.setText(R.string.stop_pump)
                string = R.string.started
            }

            OperatingMode.STOPPED -> {
                binding.operatingModeButton.setText(R.string.start_pump)
                string = R.string.stopped
            }

            OperatingMode.PAUSED  -> {
                binding.operatingModeButton.setText(R.string.start_pump)
                string = app.aaps.core.ui.R.string.paused
            }

            else                  -> Unit
        }
        binding.operatingModeLine.visibility = View.VISIBLE
        binding.operatingMode.text = rh.gs(string)
    }

    private fun getBatteryStatusItem() {
        insightPlugin.batteryStatus?.let { batteryStatus ->
            binding.batteryLevelLine.visibility = View.VISIBLE
            binding.batteryLevel.text = rh.gs(app.aaps.core.ui.R.string.format_percent, batteryStatus.batteryAmount)
        } ?: apply {
            binding.batteryLevelLine.visibility = View.GONE
        }
    }

    private fun getCartridgeStatusItem() {
        insightPlugin.cartridgeStatus?.let { cartridgeStatus ->
            binding.reservoirLevelLine.visibility = View.VISIBLE
            val status: String = if (cartridgeStatus.isInserted) rh.gs(app.aaps.core.ui.R.string.format_insulin_units, cartridgeStatus.remainingAmount) else rh.gs(R.string.not_inserted)
            binding.reservoirLevel.text = status
        } ?: apply {
            binding.reservoirLevelLine.visibility = View.GONE
        }
    }

    private fun getTDDItems() {
        insightPlugin.totalDailyDose?.let { tdd ->
            binding.tddBolusLine.visibility = View.VISIBLE
            binding.tddBolus.text = rh.gs(app.aaps.core.ui.R.string.format_insulin_units, tdd.bolus)
            binding.tddBasalLine.visibility = View.VISIBLE
            binding.tddBasal.text = rh.gs(app.aaps.core.ui.R.string.format_insulin_units, tdd.basal)
            binding.tddTotalLine.visibility = View.VISIBLE
            binding.tddTotal.text = rh.gs(app.aaps.core.ui.R.string.format_insulin_units, tdd.bolusAndBasal)
        } ?: apply {
            binding.tddBolusLine.visibility = View.GONE
            binding.tddBasalLine.visibility = View.GONE
            binding.tddTotalLine.visibility = View.GONE
        }
    }

    private fun getBaseBasalRateItem() {
        insightPlugin.activeBasalRate?.let { activeBasalRate ->
            binding.activeBasalRateLine.visibility = View.VISIBLE
            binding.activeBasalRate.text = rh.gs(app.aaps.core.ui.R.string.pump_base_basal_rate, activeBasalRate.activeBasalRate) + " (${activeBasalRate.activeBasalProfileName})"
        } ?: apply {
            binding.activeBasalRateLine.visibility = View.GONE
        }
    }

    private fun getTBRItem() {
        insightPlugin.activeTBR?.let { activeTBR ->
            binding.activeTbrLine.visibility = View.VISIBLE
            binding.activeTbr.text = rh.gs(R.string.tbr_formatter, activeTBR.percentage, activeTBR.initialDuration - activeTBR.remainingDuration, activeTBR.initialDuration)
        } ?: apply {
            binding.activeTbrLine.visibility = View.GONE
        }
    }

    private fun getLastBolusItem() {
        if (insightPlugin.lastBolusAmount.cU.equals(0.0) || insightPlugin.lastBolusTimestamp == 0L) {
            binding.lastBolusLine.visibility = View.GONE
            return
        }
        binding.lastBolusLine.visibility = View.VISIBLE
        val agoMsc = System.currentTimeMillis() - insightPlugin.lastBolusTimestamp
        val bolusMinAgo = agoMsc / 60.0 / 1000.0
        val unit = rh.gs(app.aaps.core.ui.R.string.insulin_unit_shortname)
        val ago: String = if (bolusMinAgo < 60) {
            dateUtil.minAgo(rh, insightPlugin.lastBolusTimestamp)
        } else {
            dateUtil.hourAgo(insightPlugin.lastBolusTimestamp, rh)
        }
        binding.lastBolus.text = rh.gs(R.string.insight_last_bolus_formater, insightPlugin.lastBolusAmount.cU, unit, ago) // Todo: ConcentratedUnits to be formated with IU + CU in Fragment
    }

    private fun getBolusItems() {
        insightPlugin.activeBoluses?.let { activeBoluses ->
            binding.extendedBolus1Line.visibility = activeBoluses.isNotEmpty().toVisibility()
            if (activeBoluses.isNotEmpty())
                updateBolusItemView(binding.extendedBolus1, binding.extendedBolus1Label, activeBoluses[0])
            binding.extendedBolus2Line.visibility = (activeBoluses.size > 1).toVisibility()
            if (activeBoluses.size > 1)
                updateBolusItemView(binding.extendedBolus2, binding.extendedBolus2Label, activeBoluses[1])
        } ?: apply {
            binding.extendedBolus1Line.visibility = View.GONE
            binding.extendedBolus2Line.visibility = View.GONE
        }
    }

    private fun updateBolusItemView(view: TextView, labelView: TextView, activeBolus: ActiveBolus) {
        when (activeBolus.bolusType) {
            BolusType.MULTIWAVE -> rh.gs(R.string.multiwave_bolus)
            BolusType.EXTENDED  -> rh.gs(app.aaps.core.ui.R.string.extended_bolus)
            else                -> null
        }?.let { label ->
            labelView.text = label
            view.text = rh.gs(R.string.eb_formatter, activeBolus.remainingAmount, activeBolus.initialAmount, activeBolus.remainingDuration)
        }
    }

    companion object {

        private const val ENABLE_OPERATING_MODE_BUTTON = false
    }
}