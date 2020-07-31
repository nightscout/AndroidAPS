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
import info.nightscout.androidaps.plugins.common.ManufacturerType;
import info.nightscout.androidaps.plugins.pump.common.PumpPluginAbstract;
import info.nightscout.androidaps.plugins.pump.common.R;
import info.nightscout.androidaps.plugins.pump.common.data.PumpStatus;
import info.nightscout.androidaps.plugins.pump.common.defs.PumpType;
import info.nightscout.androidaps.plugins.pump.common.dialog.RefreshableInterface;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.defs.RileyLinkFirmwareVersion;
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
    //@Inject MedtronicUtil medtronicUtil;
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

        this.connectionStatus.setText(resourceHelper.gs(rileyLinkServiceData.rileyLinkServiceState.getResourceId(targetDevice)));

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

        PumpPluginAbstract pumpPlugin = (PumpPluginAbstract)activePlugin.getActivePump();

        if (pumpPlugin.manufacturer()== ManufacturerType.Medtronic) {
            //MedtronicPumpStatus medtronicPumpStatus = (MedtronicPumpStatus)pumpPlugin.getPumpStatusData();

            PumpStatus pumpStatusData = pumpPlugin.getPumpStatusData();

            this.deviceType.setText(resourceHelper.gs(RileyLinkTargetDevice.MedtronicPump.getResourceId()));
            this.deviceModel.setText(pumpPlugin.getPumpType().getDescription());
            this.serialNumber.setText(pumpStatusData.getCustomDataAsString("SERIAL_NUMBER")); // medtronicPumpStatus.serialNumber);
            this.pumpFrequency.setText(resourceHelper.gs(pumpStatusData.getCustomDataAsString("PUMP_FREQUENCY").equals("medtronic_pump_frequency_us_ca") ? R.string.medtronic_pump_frequency_us_ca : R.string.medtronic_pump_frequency_worldwide));

            if (pumpStatusData.getCustomDataAsString("PUMP_MODEL") != null)
                this.connectedDevice.setText("Medtronic " + pumpStatusData.getCustomDataAsString("PUMP_MODEL"));
            else
                this.connectedDevice.setText("???");

            if (rileyLinkServiceData.lastGoodFrequency != null)
                this.lastUsedFrequency.setText(String.format(Locale.ENGLISH, "%.2f MHz",
                        rileyLinkServiceData.lastGoodFrequency));

            if (pumpStatusData.lastConnection != 0)
                this.lastDeviceContact.setText(StringUtil.toDateTimeString(dateUtil, new LocalDateTime(
                        pumpStatusData.lastDataTime)));
            else
                this.lastDeviceContact.setText(resourceHelper.gs(R.string.common_never));
        } else {

            //OmnipodPumpStatus omnipodPumpStatus = (OmnipodPumpStatus)pumpPlugin.getPumpStatusData();
            PumpStatus pumpStatusData = pumpPlugin.getPumpStatusData();

            this.deviceType.setText(resourceHelper.gs(RileyLinkTargetDevice.Omnipod.getResourceId()));
            this.deviceModel.setText(pumpPlugin.getPumpType() == PumpType.Insulet_Omnipod ? "Eros" : "Dash");

            if (pumpPlugin.getPumpType()== PumpType.Insulet_Omnipod_Dash) {
                aapsLogger.error("Omnipod Dash not yet supported !!!");

                this.pumpFrequency.setText("-");
            } else {

                this.pumpFrequency.setText(resourceHelper.gs(R.string.omnipod_frequency));

                if (pumpStatusData != null) {

                    if (pumpStatusData.getCustomData("POD_AVAILABLE", Boolean.class)) {
                        this.serialNumber.setText(pumpStatusData.getCustomDataAsString("POD_LOT_NUMBER"));
                        this.connectedDevice.setText(pumpStatusData.pumpType == PumpType.Insulet_Omnipod ? "Eros Pod" : "Dash Pod");
                    } else {
                        this.serialNumber.setText("??");
                        this.connectedDevice.setText("-");
                    }

                    if (rileyLinkServiceData.lastGoodFrequency != null)
                        this.lastUsedFrequency.setText(String.format(Locale.ENGLISH, "%.2f MHz",
                                rileyLinkServiceData.lastGoodFrequency));

                    if (pumpStatusData.lastConnection != 0)
                        this.lastDeviceContact.setText(StringUtil.toDateTimeString(dateUtil, new LocalDateTime(
                                pumpStatusData.lastDataTime)));
                    else
                        this.lastDeviceContact.setText(resourceHelper.gs(R.string.common_never));
                }
            }
        }
    }

}
