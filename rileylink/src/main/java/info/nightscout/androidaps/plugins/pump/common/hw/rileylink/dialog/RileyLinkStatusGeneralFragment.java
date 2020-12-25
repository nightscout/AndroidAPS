package info.nightscout.androidaps.plugins.pump.common.hw.rileylink.dialog;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.apache.commons.lang3.StringUtils;
import org.joda.time.LocalDateTime;

import java.util.Locale;

import javax.inject.Inject;

import dagger.android.support.DaggerFragment;
import info.nightscout.androidaps.interfaces.ActivePluginProvider;
import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.plugins.pump.common.R;
import info.nightscout.androidaps.plugins.pump.common.dialog.RefreshableInterface;
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

    TextView connectionStatus;
    TextView configuredAddress;
    TextView connectedRileyLinkName;
    TextView connectedDevice;
    TextView connectionError;
    TextView deviceType;
    TextView deviceModel;
    TextView serialNumber;
    TextView pumpFrequency;
    TextView lastUsedFrequency;
    TextView lastDeviceContact;
    TextView firmwareVersion;

    boolean first = false;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.rileylink_status_general, container, false);

        return rootView;
    }


    @Override
    public void onStart() {
        super.onStart();

        this.connectionStatus = getActivity().findViewById(R.id.rls_t1_connection_status);
        this.configuredAddress = getActivity().findViewById(R.id.rls_t1_configured_address);
        this.connectedRileyLinkName = getActivity().findViewById(R.id.rls_t1_connected_riley_link_name);
        this.connectedDevice = getActivity().findViewById(R.id.rls_t1_connected_device);
        this.connectionError = getActivity().findViewById(R.id.rls_t1_connection_error);
        this.deviceType = getActivity().findViewById(R.id.rls_t1_device_type);
        this.deviceModel = getActivity().findViewById(R.id.rls_t1_device_model);
        this.serialNumber = getActivity().findViewById(R.id.rls_t1_serial_number);
        this.pumpFrequency = getActivity().findViewById(R.id.rls_t1_pump_frequency);
        this.lastUsedFrequency = getActivity().findViewById(R.id.rls_t1_last_used_frequency);
        this.lastDeviceContact = getActivity().findViewById(R.id.rls_t1_last_device_contact);
        this.firmwareVersion = getActivity().findViewById(R.id.rls_t1_firmware_version);

        // BS: FIXME Remove
        if (!first) {

            // 7-14
            int[] ids = {R.id.rls_t1_tv02, R.id.rls_t1_tv14, R.id.rls_t1_tv03, R.id.rls_t1_tv04, R.id.rls_t1_tv05, R.id.rls_t1_tv07, //
                    R.id.rls_t1_tv08, R.id.rls_t1_tv09, R.id.rls_t1_tv10, R.id.rls_t1_tv11, R.id.rls_t1_tv12, R.id.rls_t1_tv13};

            for (int id : ids) {

                TextView tv = getActivity().findViewById(id);
                tv.setText(tv.getText() + ":");
            }

            first = true;
        }

        refreshData();
    }


    public void refreshData() {

        RileyLinkTargetDevice targetDevice = rileyLinkServiceData.targetDevice;

        this.connectionStatus.setText(resourceHelper.gs(rileyLinkServiceData.rileyLinkServiceState.getResourceId()));

        // BS FIXME rileyLinkServiceData is injected so I suppose it cannot be null?
        if (rileyLinkServiceData != null) {
            this.configuredAddress.setText(StringUtils.isEmpty(rileyLinkServiceData.rileylinkAddress) ? PLACEHOLDER : rileyLinkServiceData.rileylinkAddress);
            this.connectedRileyLinkName.setText(StringUtils.isEmpty(rileyLinkServiceData.rileyLinkName) ? PLACEHOLDER : rileyLinkServiceData.rileyLinkName);
            this.connectionError.setText(rileyLinkServiceData.rileyLinkError == null ? //
                    PLACEHOLDER
                    : resourceHelper.gs(rileyLinkServiceData.rileyLinkError.getResourceId(targetDevice)));

            if (firmwareVersion == null) {
                this.firmwareVersion.setText("BLE113: " + PLACEHOLDER + "\nCC110: " + PLACEHOLDER);
            } else {
                this.firmwareVersion.setText("BLE113: " + rileyLinkServiceData.versionBLE113 +
                        "\nCC110: " + rileyLinkServiceData.versionCC110);
            }
        }

        RileyLinkPumpDevice pumpPlugin = (RileyLinkPumpDevice) activePlugin.getActivePump();
        RileyLinkPumpInfo pumpInfo = pumpPlugin.getPumpInfo();
        this.deviceType.setText(rileyLinkServiceData.targetDevice.getResourceId());
        this.deviceModel.setText(pumpInfo.getPumpDescription());
        this.serialNumber.setText(pumpInfo.getConnectedDeviceSerialNumber());
        this.pumpFrequency.setText(pumpInfo.getPumpFrequency());
        this.connectedDevice.setText(pumpInfo.getConnectedDeviceModel());

        if (rileyLinkServiceData.lastGoodFrequency != null) {
            this.lastUsedFrequency.setText(String.format(Locale.ENGLISH, "%.2f MHz",
                    rileyLinkServiceData.lastGoodFrequency));
        }

        long lastConnectionTimeMillis = pumpPlugin.getLastConnectionTimeMillis();
        if (lastConnectionTimeMillis == 0) {
            this.lastDeviceContact.setText(resourceHelper.gs(R.string.common_never));
        } else {
            this.lastDeviceContact.setText(StringUtil.toDateTimeString(dateUtil, new LocalDateTime(lastConnectionTimeMillis)));
        }
    }

}
