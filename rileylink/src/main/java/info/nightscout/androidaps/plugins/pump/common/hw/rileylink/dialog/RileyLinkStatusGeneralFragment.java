package info.nightscout.androidaps.plugins.pump.common.hw.rileylink.dialog;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.joda.time.LocalDateTime;

import java.util.Optional;

import javax.inject.Inject;

import dagger.android.support.DaggerFragment;
import info.nightscout.androidaps.interfaces.ActivePluginProvider;
import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.plugins.pump.common.R;
import info.nightscout.androidaps.plugins.pump.common.dialog.RefreshableInterface;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkError;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkPumpDevice;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkPumpInfo;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkTargetDevice;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.RileyLinkServiceData;
import info.nightscout.androidaps.plugins.pump.common.utils.StringUtil;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.resources.ResourceHelper;

/**
 * Created by andy on 5/19/18.
 */

public class RileyLinkStatusGeneralFragment extends DaggerFragment implements RefreshableInterface {

    private static final String PLACEHOLDER = "-";

    @Inject ActivePluginProvider activePlugin;
    @Inject ResourceHelper resourceHelper;
    @Inject AAPSLogger aapsLogger;
    @Inject RileyLinkServiceData rileyLinkServiceData;
    @Inject DateUtil dateUtil;

    private TextView connectionStatus;
    private TextView configuredRileyLinkAddress;
    private TextView configuredRileyLinkName;
    private TextView batteryLevel;
    private TextView connectionError;
    private TextView deviceType;
    private TextView deviceModel;
    private TextView serialNumber;
    private TextView pumpFrequency;
    private TextView lastUsedFrequency;
    private TextView lastDeviceContact;
    private TextView firmwareVersion;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.rileylink_status_general, container, false);
    }

    @Override
    public void onStart() {
        super.onStart();

        this.connectionStatus = getActivity().findViewById(R.id.rls_t1_connection_status);
        this.configuredRileyLinkAddress = getActivity().findViewById(R.id.rls_t1_configured_riley_link_address);
        this.configuredRileyLinkName = getActivity().findViewById(R.id.rls_t1_configured_riley_link_name);
        this.batteryLevel = getActivity().findViewById(R.id.rls_t1_battery_level);
        this.connectionError = getActivity().findViewById(R.id.rls_t1_connection_error);
        this.deviceType = getActivity().findViewById(R.id.rls_t1_device_type);
        this.deviceModel = getActivity().findViewById(R.id.rls_t1_device_model);
        this.serialNumber = getActivity().findViewById(R.id.rls_t1_serial_number);
        this.pumpFrequency = getActivity().findViewById(R.id.rls_t1_pump_frequency);
        this.lastUsedFrequency = getActivity().findViewById(R.id.rls_t1_last_used_frequency);
        this.lastDeviceContact = getActivity().findViewById(R.id.rls_t1_last_device_contact);
        this.firmwareVersion = getActivity().findViewById(R.id.rls_t1_firmware_version);

        refreshData();
    }

    @Override public void refreshData() {
        RileyLinkTargetDevice targetDevice = rileyLinkServiceData.targetDevice;

        this.connectionStatus.setText(resourceHelper.gs(rileyLinkServiceData.rileyLinkServiceState.getResourceId()));

        // BS FIXME rileyLinkServiceData is injected so I suppose it cannot be null?
        if (rileyLinkServiceData != null) {
            this.configuredRileyLinkAddress.setText(Optional.ofNullable(rileyLinkServiceData.rileyLinkAddress).orElse(PLACEHOLDER));
            this.configuredRileyLinkName.setText(Optional.ofNullable(rileyLinkServiceData.rileyLinkName).orElse(PLACEHOLDER));

            Integer batteryLevel = rileyLinkServiceData.batteryLevel;
            this.batteryLevel.setText(batteryLevel == null ? PLACEHOLDER : resourceHelper.gs(R.string.rileylink_battery_level_value, batteryLevel));

            RileyLinkError rileyLinkError = rileyLinkServiceData.rileyLinkError;
            this.connectionError.setText(rileyLinkError == null ? PLACEHOLDER : resourceHelper.gs(rileyLinkError.getResourceId(targetDevice)));

            this.firmwareVersion.setText(resourceHelper.gs(R.string.rileylink_firmware_version_value,
                    Optional.ofNullable(rileyLinkServiceData.versionBLE113).orElse(PLACEHOLDER), Optional.ofNullable(rileyLinkServiceData.versionCC110).orElse(PLACEHOLDER)));
        }

        RileyLinkPumpDevice pumpPlugin = (RileyLinkPumpDevice) activePlugin.getActivePump();
        RileyLinkPumpInfo pumpInfo = pumpPlugin.getPumpInfo();
        this.deviceType.setText(rileyLinkServiceData.targetDevice.getResourceId());
        this.deviceModel.setText(pumpInfo.getConnectedDeviceModel());
        this.serialNumber.setText(pumpInfo.getConnectedDeviceSerialNumber());
        this.pumpFrequency.setText(pumpInfo.getPumpFrequency());

        if (rileyLinkServiceData.lastGoodFrequency != null) {
            this.lastUsedFrequency.setText(resourceHelper.gs(R.string.rileylink_pump_frequency_value, rileyLinkServiceData.lastGoodFrequency));
        }

        long lastConnectionTimeMillis = pumpPlugin.getLastConnectionTimeMillis();
        if (lastConnectionTimeMillis == 0) {
            this.lastDeviceContact.setText(resourceHelper.gs(R.string.riley_link_ble_config_connected_never));
        } else {
            this.lastDeviceContact.setText(StringUtil.toDateTimeString(dateUtil, new LocalDateTime(lastConnectionTimeMillis)));
        }
    }

}
