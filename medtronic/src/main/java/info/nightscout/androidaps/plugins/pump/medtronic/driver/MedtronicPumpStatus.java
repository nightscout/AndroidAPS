package info.nightscout.androidaps.plugins.pump.medtronic.driver;

import androidx.annotation.NonNull;

import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Singleton;

import info.nightscout.androidaps.plugins.bus.RxBusWrapper;
import info.nightscout.androidaps.plugins.pump.common.defs.PumpDeviceState;
import info.nightscout.androidaps.plugins.pump.common.defs.PumpType;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.RileyLinkUtil;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.data.RLHistoryItem;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkTargetDevice;
import info.nightscout.androidaps.plugins.pump.medtronic.defs.BasalProfileStatus;
import info.nightscout.androidaps.plugins.pump.medtronic.defs.BatteryType;
import info.nightscout.androidaps.plugins.pump.medtronic.defs.MedtronicDeviceType;
import info.nightscout.androidaps.plugins.pump.common.events.EventRileyLinkDeviceStatusChange;
import info.nightscout.androidaps.plugins.pump.medtronic.util.MedtronicConst;
import info.nightscout.androidaps.utils.resources.ResourceHelper;
import info.nightscout.androidaps.utils.sharedPreferences.SP;



/**
 * Created by andy on 4/28/18.
 */

@Singleton
public class MedtronicPumpStatus extends info.nightscout.androidaps.plugins.pump.common.data.PumpStatus  {

    private final ResourceHelper resourceHelper;
    private final SP sp;
    private final RileyLinkUtil rileyLinkUtil;
    private final RxBusWrapper rxBus;

    public String errorDescription = null;
    public String serialNumber;
    public String pumpFrequency = null;
    public Double maxBolus;
    public Double maxBasal;

    // statuses
    private PumpDeviceState pumpDeviceState = PumpDeviceState.NeverContacted;
    public MedtronicDeviceType medtronicDeviceType = null;
    public Date tempBasalStart;
    public Double tempBasalAmount = 0.0d;

    // fixme
    public Integer tempBasalLength = 0;

    private Map<String, PumpType> medtronicPumpMap = null;
    private Map<String, MedtronicDeviceType> medtronicDeviceTypeMap = null;
    public BasalProfileStatus basalProfileStatus = BasalProfileStatus.NotInitialized;
    public BatteryType batteryType = BatteryType.None;


    @Inject
    public MedtronicPumpStatus(ResourceHelper resourceHelper,
            SP sp,
            RxBusWrapper rxBus,
            RileyLinkUtil rileyLinkUtil
    ) {
        super(PumpType.Medtronic_522_722);
        this.resourceHelper = resourceHelper;
        this.sp = sp;
        this.rxBus = rxBus;
        this.rileyLinkUtil = rileyLinkUtil;
        initSettings();
    }


    public void initSettings() {

        this.activeProfileName = "STD";
        this.reservoirRemainingUnits = 75d;
        this.batteryRemaining = 75;

        if (this.medtronicPumpMap == null)
            createMedtronicPumpMap();

        if (this.medtronicDeviceTypeMap == null)
            createMedtronicDeviceTypeMap();

        this.lastConnection = sp.getLong(MedtronicConst.Statistics.LastGoodPumpCommunicationTime, 0L);
        this.lastDataTime = this.lastConnection;
    }


    private void createMedtronicDeviceTypeMap() {
        medtronicDeviceTypeMap = new HashMap<>();
        medtronicDeviceTypeMap.put("512", MedtronicDeviceType.Medtronic_512);
        medtronicDeviceTypeMap.put("712", MedtronicDeviceType.Medtronic_712);
        medtronicDeviceTypeMap.put("515", MedtronicDeviceType.Medtronic_515);
        medtronicDeviceTypeMap.put("715", MedtronicDeviceType.Medtronic_715);

        medtronicDeviceTypeMap.put("522", MedtronicDeviceType.Medtronic_522);
        medtronicDeviceTypeMap.put("722", MedtronicDeviceType.Medtronic_722);
        medtronicDeviceTypeMap.put("523", MedtronicDeviceType.Medtronic_523_Revel);
        medtronicDeviceTypeMap.put("723", MedtronicDeviceType.Medtronic_723_Revel);
        medtronicDeviceTypeMap.put("554", MedtronicDeviceType.Medtronic_554_Veo);
        medtronicDeviceTypeMap.put("754", MedtronicDeviceType.Medtronic_754_Veo);
    }


    private void createMedtronicPumpMap() {

        medtronicPumpMap = new HashMap<>();
        medtronicPumpMap.put("512", PumpType.Medtronic_512_712);
        medtronicPumpMap.put("712", PumpType.Medtronic_512_712);
        medtronicPumpMap.put("515", PumpType.Medtronic_515_715);
        medtronicPumpMap.put("715", PumpType.Medtronic_515_715);

        medtronicPumpMap.put("522", PumpType.Medtronic_522_722);
        medtronicPumpMap.put("722", PumpType.Medtronic_522_722);
        medtronicPumpMap.put("523", PumpType.Medtronic_523_723_Revel);
        medtronicPumpMap.put("723", PumpType.Medtronic_523_723_Revel);
        medtronicPumpMap.put("554", PumpType.Medtronic_554_754_Veo);
        medtronicPumpMap.put("754", PumpType.Medtronic_554_754_Veo);

    }

    public Map<String, PumpType> getMedtronicPumpMap() {
        return medtronicPumpMap;
    }

    public Map<String, MedtronicDeviceType> getMedtronicDeviceTypeMap() {
        return medtronicDeviceTypeMap;
    }

    public double getBasalProfileForHour() {
        if (basalsByHour != null) {
            GregorianCalendar c = new GregorianCalendar();
            int hour = c.get(Calendar.HOUR_OF_DAY);

            return basalsByHour[hour];
        }

        return 0;
    }

    // Battery type
    private Map<String, BatteryType> mapByDescription;

    public BatteryType getBatteryTypeByDescription(String batteryTypeStr) {
        if (mapByDescription == null) {
            mapByDescription = new HashMap<>();
            for (BatteryType value : BatteryType.values()) {
                mapByDescription.put(resourceHelper.gs(value.description), value);
            }
        }
        if (mapByDescription.containsKey(batteryTypeStr)) {
            return mapByDescription.get(batteryTypeStr);
        }
        return BatteryType.None;
    }

    @NonNull
    public String getErrorInfo() {
        return (errorDescription == null) ? "-" : errorDescription;
    }


    public PumpDeviceState getPumpDeviceState() {
        return pumpDeviceState;
    }


    public void setPumpDeviceState(PumpDeviceState pumpDeviceState) {
        this.pumpDeviceState = pumpDeviceState;

        rileyLinkUtil.getRileyLinkHistory().add(new RLHistoryItem(pumpDeviceState, RileyLinkTargetDevice.MedtronicPump));

        rxBus.send(new EventRileyLinkDeviceStatusChange(pumpDeviceState));
    }
}
