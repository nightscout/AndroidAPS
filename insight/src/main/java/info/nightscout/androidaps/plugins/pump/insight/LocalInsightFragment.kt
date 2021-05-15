package info.nightscout.androidaps.plugins.pump.insight

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import dagger.android.support.DaggerFragment
import info.nightscout.androidaps.insight.R
import info.nightscout.androidaps.insight.databinding.LocalInsightFragmentBinding
import info.nightscout.androidaps.interfaces.CommandQueueProvider
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.pump.insight.descriptors.*
import info.nightscout.androidaps.plugins.pump.insight.events.EventLocalInsightUpdateGUI
import info.nightscout.androidaps.queue.Callback
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.DecimalFormatter.to2Decimal
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.rx.AapsSchedulers
import io.reactivex.disposables.CompositeDisposable
import java.util.*
import javax.inject.Inject

class LocalInsightFragment : DaggerFragment(), View.OnClickListener {

    @Inject lateinit var localInsightPlugin: LocalInsightPlugin
    @Inject lateinit var  commandQueue: CommandQueueProvider
    @Inject lateinit var  rxBus: RxBusWrapper
    @Inject lateinit var resourceHelper: ResourceHelper
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var dateUtil: DateUtil
    private var _binding: LocalInsightFragmentBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

        @JvmField @Inject
    var aapsSchedulers: AapsSchedulers? = null
    private val disposable = CompositeDisposable()
    private var operatingModeCallback: Callback? = null
    private var tbrOverNotificationCallback: Callback? = null
    private var refreshCallback: Callback? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = LocalInsightFragmentBinding.inflate(inflater, container, false)
        binding.tbrOverNotification.setOnClickListener(this)
        binding.operatingMode.setOnClickListener(this)
        binding.refresh.setOnClickListener(this)
        return binding.root
    }

    @Synchronized override fun onResume() {
        super.onResume()
        disposable.add(rxBus
            .toObservable(EventLocalInsightUpdateGUI::class.java)
            .observeOn(aapsSchedulers!!.main)
            .subscribe({ event: EventLocalInsightUpdateGUI? -> updateGUI() }) { throwable: Throwable? -> fabricPrivacy.logException(throwable!!) }
        )
        updateGUI()
    }

    @Synchronized override fun onPause() {
        super.onPause()
        disposable.clear()
    }

    @Synchronized override fun onDestroyView() {
        super.onDestroyView()
    }

    override fun onClick(v: View) {
        when (v.id) {
            R.id.operating_mode -> {
                    if (localInsightPlugin.operatingMode != null) {
                        binding.operatingMode.isEnabled = false
                        operatingModeCallback = object : Callback() {
                            override fun run() {
                                Handler(Looper.getMainLooper()).post {
                                    operatingModeCallback = null
                                    updateGUI()
                                }
                            }
                        }
                        when (localInsightPlugin.operatingMode) {
                            OperatingMode.PAUSED, OperatingMode.STOPPED -> commandQueue.startPump(operatingModeCallback)
                            OperatingMode.STARTED                       -> commandQueue.stopPump(operatingModeCallback)
                        }
                    }
                }
            R.id.tbr_over_notification -> {
                    val notificationBlock = localInsightPlugin.tBROverNotificationBlock
                    if (notificationBlock != null) {
                        binding.tbrOverNotification.isEnabled = false
                        tbrOverNotificationCallback = object : Callback() {
                            override fun run() {
                                Handler(Looper.getMainLooper()).post {
                                    tbrOverNotificationCallback = null
                                    updateGUI()
                                }
                            }
                        }
                        commandQueue.setTBROverNotification(tbrOverNotificationCallback, !notificationBlock.isEnabled)
                    }
                }
            R.id.refresh    -> {
                binding.refresh.isEnabled = false
                refreshCallback = object : Callback() {
                    override fun run() {
                        Handler(Looper.getMainLooper()).post {
                            refreshCallback = null
                            updateGUI()
                        }
                    }
                }
                commandQueue.readStatus("InsightRefreshButton", refreshCallback)
            }
        }
    }

    protected fun updateGUI() {
        binding.statusItemContainer.removeAllViews()
        if (!localInsightPlugin.isInitialized()) {
            binding.operatingMode.visibility = View.GONE
            binding.tbrOverNotification.visibility = View.GONE
            binding.refresh.visibility = View.GONE
            return
        }
        binding.refresh.visibility = View.VISIBLE
        binding.refresh.isEnabled = refreshCallback == null
        val notificationBlock = localInsightPlugin.tBROverNotificationBlock
        binding.tbrOverNotification.visibility = if (notificationBlock == null) View.GONE else View.VISIBLE
        if (notificationBlock != null) binding.tbrOverNotification.setText(if (notificationBlock.isEnabled) R.string.disable_tbr_over_notification else R.string.enable_tbr_over_notification)
        binding.tbrOverNotification.isEnabled = tbrOverNotificationCallback == null
        val statusItems: MutableList<View> = ArrayList()
        getConnectionStatusItem(statusItems)
        getLastConnectedItem(statusItems)
        getOperatingModeItem(statusItems)
        getBatteryStatusItem(statusItems)
        getCartridgeStatusItem(statusItems)
        getTDDItems(statusItems)
        getBaseBasalRateItem(statusItems)
        getTBRItem(statusItems)
        getBolusItems(statusItems)
        for (i in statusItems.indices) {
            binding.statusItemContainer.addView(statusItems[i])
            if (i != statusItems.size - 1) layoutInflater.inflate(R.layout.local_insight_status_delimitter, binding.statusItemContainer)
        }
    }

    private fun getStatusItem(label: String, value: String): View {
        @SuppressLint("InflateParams") val statusItem = layoutInflater.inflate(R.layout.local_insight_status_item, null)
        (statusItem.findViewById<View>(R.id.label) as TextView).text = label
        (statusItem.findViewById<View>(R.id.value) as TextView).text = value
        return statusItem
    }

    private fun getConnectionStatusItem(statusItems: MutableList<View>) {
        var string = 0
        val state = localInsightPlugin.connectionService!!.state
        string = when (state) {
            InsightState.NOT_PAIRED                                                                                                                                                                                                                                                                                                                                                                                                                                                                      -> R.string.not_paired
            InsightState.DISCONNECTED                                                                                                                                                                                                                                                                                                                                                                                                                                                                    -> R.string.disconnected
            InsightState.CONNECTING, InsightState.SATL_CONNECTION_REQUEST, InsightState.SATL_KEY_REQUEST, InsightState.SATL_SYN_REQUEST, InsightState.SATL_VERIFY_CONFIRM_REQUEST, InsightState.SATL_VERIFY_DISPLAY_REQUEST, InsightState.APP_ACTIVATE_PARAMETER_SERVICE, InsightState.APP_ACTIVATE_STATUS_SERVICE, InsightState.APP_BIND_MESSAGE, InsightState.APP_CONNECT_MESSAGE, InsightState.APP_FIRMWARE_VERSIONS, InsightState.APP_SYSTEM_IDENTIFICATION, InsightState.AWAITING_CODE_CONFIRMATION -> R.string.connecting
            InsightState.CONNECTED                                                                                                                                                                                                                                                                                                                                                                                                                                                                       -> R.string.connected
            InsightState.RECOVERING                                                                                                                                                                                                                                                                                                                                                                                                                                                                      -> R.string.recovering
        }
        statusItems.add(getStatusItem(resourceHelper.gs(R.string.insight_status), resourceHelper.gs(string)))
        if (state == InsightState.RECOVERING) {
            statusItems.add(getStatusItem(resourceHelper.gs(R.string.recovery_duration), (localInsightPlugin.connectionService!!.recoveryDuration / 1000).toString() + "s"))
        }
    }

    private fun getLastConnectedItem(statusItems: MutableList<View>) {
        when (localInsightPlugin.connectionService!!.state) {
            InsightState.CONNECTED, InsightState.NOT_PAIRED -> return

            else                                            -> {
                val lastConnection = localInsightPlugin.connectionService!!.lastConnected
                if (lastConnection == 0L) return
                statusItems.add(getStatusItem(resourceHelper.gs(R.string.last_connected), dateUtil.timeString(lastConnection)))
            }
        }
    }

    private fun getOperatingModeItem(statusItems: MutableList<View>) {
        if (localInsightPlugin.operatingMode == null) {
            binding.operatingMode.visibility = View.GONE
            return
        }
        var string = 0
        if (ENABLE_OPERATING_MODE_BUTTON) binding.operatingMode.visibility = View.VISIBLE
        binding.operatingMode.isEnabled = operatingModeCallback == null
        when (localInsightPlugin.operatingMode) {
            OperatingMode.STARTED -> {
                    binding.operatingMode.setText(R.string.stop_pump)
                    string = R.string.started
                }
            OperatingMode.STOPPED -> {
                    binding.operatingMode.setText(R.string.start_pump)
                    string = R.string.stopped
                }
            OperatingMode.PAUSED  -> {
                    binding.operatingMode.setText(R.string.start_pump)
                    string = R.string.paused
                }
        }
        statusItems.add(getStatusItem(resourceHelper.gs(R.string.operating_mode), resourceHelper.gs(string)))
    }

    private fun getBatteryStatusItem(statusItems: MutableList<View>) {
        if (localInsightPlugin.batteryStatus == null) return
        statusItems.add(getStatusItem(resourceHelper.gs(R.string.battery_label), localInsightPlugin.batteryStatus!!.batteryAmount.toString() + "%"))
    }

    private fun getCartridgeStatusItem(statusItems: MutableList<View>) {
        val cartridgeStatus = localInsightPlugin.cartridgeStatus ?: return
        val status: String
        status = if (cartridgeStatus.isInserted) to2Decimal(localInsightPlugin.cartridgeStatus!!.remainingAmount) + "U" else resourceHelper.gs(R.string.not_inserted)
        statusItems.add(getStatusItem(resourceHelper.gs(R.string.reservoir_label), status))
    }

    private fun getTDDItems(statusItems: MutableList<View>) {
        if (localInsightPlugin.totalDailyDose == null) return
        val tdd = localInsightPlugin.totalDailyDose
        statusItems.add(getStatusItem(resourceHelper.gs(R.string.tdd_bolus), to2Decimal(tdd!!.bolus)))
        statusItems.add(getStatusItem(resourceHelper.gs(R.string.tdd_basal), to2Decimal(tdd.basal)))
        statusItems.add(getStatusItem(resourceHelper.gs(R.string.tdd_total), to2Decimal(tdd.bolusAndBasal)))
    }

    private fun getBaseBasalRateItem(statusItems: MutableList<View>) {
        if (localInsightPlugin.activeBasalRate == null) return
        val activeBasalRate = localInsightPlugin.activeBasalRate
        statusItems.add(getStatusItem(resourceHelper.gs(R.string.basebasalrate_label),
            to2Decimal(activeBasalRate!!.activeBasalRate) + " U/h (" + activeBasalRate.activeBasalProfileName + ")"))
    }

    private fun getTBRItem(statusItems: MutableList<View>) {
        if (localInsightPlugin.activeTBR == null) return
        val activeTBR = localInsightPlugin.activeTBR
        statusItems.add(getStatusItem(resourceHelper.gs(R.string.tempbasal_label),
            resourceHelper.gs(R.string.tbr_formatter, activeTBR!!.percentage, activeTBR.initialDuration - activeTBR.remainingDuration, activeTBR.initialDuration)))
    }

    private fun getBolusItems(statusItems: MutableList<View>) {
        if (localInsightPlugin.activeBoluses == null) return
        for (activeBolus in localInsightPlugin.activeBoluses!!) {
            var label: String
            label = when (activeBolus.bolusType) {
                BolusType.MULTIWAVE -> resourceHelper.gs(R.string.multiwave_bolus)
                BolusType.EXTENDED  -> resourceHelper.gs(R.string.extended_bolus)
                else                -> continue
            }
            statusItems.add(getStatusItem(label, resourceHelper.gs(R.string.eb_formatter, activeBolus.remainingAmount, activeBolus.initialAmount, activeBolus.remainingDuration)))
        }
    }

    companion object {

        private const val ENABLE_OPERATING_MODE_BUTTON = false
    }
}