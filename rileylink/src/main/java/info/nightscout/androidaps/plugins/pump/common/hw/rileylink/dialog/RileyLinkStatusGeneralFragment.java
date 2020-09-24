package info.nightscout.androidaps.plugins.pump.common.hw.rileylink.dialog;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.joda.time.LocalDateTime;

import java.util.Locale;

import javax.inject.Inject;

import dagger.android.support.DaggerFragment;
import info.nightscout.androidaps.interfaces.ActivePluginProvider;
import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.plugins.pump.common.R;
import info.nightscout.androidaps.plugins.pump.common.dialog.RefreshableInterface;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.defs.RileyLinkFirmwareVersion;
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

    @Inject ActivePluginProvider activePlugin;
    @Inject ResourceHelper resourceHelper;
    @Inject AAPSLogger aapsLogger;
    @Inject RileyLinkServiceData rileyLinkServiceData;
    @Inject DateUtil dateUtil;

    TextView connectionStatus;
    TextView configuredAddress;
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
        this.connectedDevice = getActivity().findViewById(R.id.rls_t1_connected_device);
        this.connectionError = getActivity().findViewById(R.id.rls_t1_connection_error);
        this.deviceType = getActivity().findViewById(R.id.rls_t1_device_type);
        this.deviceModel = getActivity().findViewById(R.id.rls_t1_device_model);
        this.serialNumber = getActivity().findViewById(R.id.rls_t1_serial_number);
        this.pumpFrequency = getActivity().findViewById(R.id.rls_t1_pump_frequency);
        this.lastUsedFrequency = getActivity().findViewById(R.id.rls_t1_last_used_frequency);
        this.lastDeviceContact = getActivity().findViewById(R.id.rls_t1_last_device_contact);
        this.firmwareVersion = getActivity().findViewById(R.id.rls_t1_firmware_version);

        if (!first) {

            // 7-12
            int[] ids = {R.id.rls_t1_tv02, R.id.rls_t1_tv03, R.id.rls_t1_tv04, R.id.rls_t1_tv05, R.id.rls_t1_tv07, //
                    R.id.rls_t1_tv08, R.id.rls_t1_tv09, R.id.rls_t1_tv10, R.id.rls_t1_tv11, R.id.rls_t1_tv12, R.id.rls_t1_tv13};

            for (int id : ids) {

                TextView tv = (TextView) getActivity().findViewById(id);
                tv.setText(tv.getText() + ":");
            }

            first = true;
        }

        refreshData();
    }


    public void refreshData() {

        RileyLinkTargetDevice targetDevice = rileyLinkServiceData.targetDevice;

        this.connectionStatus.setText(resourceHelper.gs(rileyLinkServiceData.rileyLinkServiceState.getResourceId()));

        if (rileyLinkServiceData != null) {
            this.configuredAddress.setText(rileyLinkServiceData.rileylinkAddress);
            this.connectionError.setText(rileyLinkServiceData.rileyLinkError == null ? //
                    "-"
                    : resourceHelper.gs(rileyLinkServiceData.rileyLinkError.getResourceId(targetDevice)));

            RileyLinkFirmwareVersion firmwareVersion = rileyLinkServiceData.versionCC110;

            if (firmwareVersion == null) {
                this.firmwareVersion.setText("BLE113: -\nCC110: -");
            } else {
                this.firmwareVersion.setText("BLE113: " + rileyLinkServiceData.versionBLE113 + //
                        "\nCC110: " + firmwareVersion.toString());
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
