package app.aaps.pump.common.hw.rileylink.dialog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.common.hw.rileylink.R
import app.aaps.pump.common.hw.rileylink.databinding.RileylinkStatusGeneralBinding
import app.aaps.pump.common.hw.rileylink.defs.RileyLinkPumpDevice
import app.aaps.pump.common.hw.rileylink.defs.RileyLinkTargetDevice
import app.aaps.pump.common.hw.rileylink.keys.RileylinkBooleanPreferenceKey
import app.aaps.pump.common.hw.rileylink.service.RileyLinkServiceData
import dagger.android.support.DaggerFragment
import javax.inject.Inject

class RileyLinkStatusGeneralFragment : DaggerFragment() {

    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var rileyLinkServiceData: RileyLinkServiceData
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var preferences: Preferences

    private var _binding: RileylinkStatusGeneralBinding? = null

    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        RileylinkStatusGeneralBinding.inflate(inflater, container, false).also { _binding = it }.root

    override fun onResume() {
        super.onResume()
        refreshData()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.refresh.setOnClickListener { refreshData() }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun refreshData() {
        val targetDevice = rileyLinkServiceData.targetDevice
        binding.connectionStatus.text = rh.gs(rileyLinkServiceData.rileyLinkServiceState.resourceId)
        binding.configuredRileyLinkAddress.text = rileyLinkServiceData.rileyLinkAddress ?: EMPTY
        binding.configuredRileyLinkName.text = rileyLinkServiceData.rileyLinkName ?: EMPTY
        if (preferences.get(RileylinkBooleanPreferenceKey.ShowReportedBatteryLevel)) {
            binding.batteryLevelRow.visibility = View.VISIBLE
            val batteryLevel = rileyLinkServiceData.batteryLevel
            binding.batteryLevel.text = batteryLevel?.let { rh.gs(R.string.rileylink_battery_level_value, it) } ?: EMPTY
        } else binding.batteryLevelRow.visibility = View.GONE
        binding.connectionError.text = rileyLinkServiceData.rileyLinkError?.let { rh.gs(it.getResourceId(targetDevice)) } ?: EMPTY
        if (rileyLinkServiceData.isOrange && rileyLinkServiceData.versionOrangeFirmware != null) {
            binding.firmwareVersion.text = rh.gs(
                R.string.rileylink_firmware_version_value_orange,
                rileyLinkServiceData.versionOrangeFirmware,
                rileyLinkServiceData.versionOrangeHardware ?: EMPTY
            )
        } else {
            binding.firmwareVersion.text = rh.gs(
                R.string.rileylink_firmware_version_value,
                rileyLinkServiceData.versionBLE113 ?: EMPTY,
                rileyLinkServiceData.versionCC110 ?: EMPTY
            )
        }
        val rileyLinkPumpDevice = activePlugin.activePump as RileyLinkPumpDevice
        val rileyLinkPumpInfo = rileyLinkPumpDevice.pumpInfo
        binding.deviceType.setText(targetDevice.resourceId)
        if (targetDevice == RileyLinkTargetDevice.MedtronicPump) {
            binding.connectedDeviceDetails.visibility = View.VISIBLE
            binding.configuredDeviceModel.text = activePlugin.activePump.pumpDescription.pumpType.description
            binding.connectedDeviceModel.text = rileyLinkPumpInfo.connectedDeviceModel
        } else binding.connectedDeviceDetails.visibility = View.GONE
        binding.serialNumber.text = rileyLinkPumpInfo.connectedDeviceSerialNumber
        binding.pumpFrequency.text = rileyLinkPumpInfo.pumpFrequency
        if (rileyLinkServiceData.lastGoodFrequency != null) {
            binding.lastUsedFrequency.text = rh.gs(R.string.rileylink_pump_frequency_value, rileyLinkServiceData.lastGoodFrequency)
        }
        val lastConnectionTimeMillis = rileyLinkPumpDevice.lastConnectionTimeMillis
        if (lastConnectionTimeMillis == 0L) binding.lastDeviceContact.text = rh.gs(R.string.riley_link_ble_config_connected_never)
        else binding.lastDeviceContact.text = dateUtil.dateAndTimeAndSecondsString(lastConnectionTimeMillis)
    }

    companion object {

        private const val EMPTY = "-"
    }
}