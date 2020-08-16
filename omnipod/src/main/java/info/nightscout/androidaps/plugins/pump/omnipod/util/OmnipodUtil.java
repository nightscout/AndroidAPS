package info.nightscout.androidaps.plugins.pump.omnipod.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializer;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.ISODateTimeFormat;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import info.nightscout.androidaps.R;
import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.AlertSet;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.AlertSlot;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.AlertType;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.state.PodStateManager;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.OmnipodDriverState;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.OmnipodPumpStatus;
import info.nightscout.androidaps.utils.resources.ResourceHelper;
import info.nightscout.androidaps.utils.sharedPreferences.SP;

/**
 * Created by andy on 4/8/19.
 */
@Singleton
public class OmnipodUtil {

    private final AAPSLogger aapsLogger;
    private final OmnipodPumpStatus omnipodPumpStatus;
    private final ResourceHelper resourceHelper;
    private final SP sp;

    private Gson gsonInstance = createGson();
    private OmnipodDriverState driverState = OmnipodDriverState.NotInitalized;

    @Inject
    public OmnipodUtil(
            AAPSLogger aapsLogger,
            OmnipodPumpStatus omnipodPumpStatus,
            SP sp,
            ResourceHelper resourceHelper
    ) {
        this.aapsLogger = aapsLogger;
        this.omnipodPumpStatus = omnipodPumpStatus;
        this.sp = sp;
        this.resourceHelper = resourceHelper;
    }

    public OmnipodDriverState getDriverState() {
        return driverState;
    }

    public void setDriverState(OmnipodDriverState state) {
        if (driverState == state)
            return;

        driverState = state;
        omnipodPumpStatus.driverState = state;
    }

    private Gson createGson() {
        GsonBuilder gsonBuilder = new GsonBuilder()
                .registerTypeAdapter(DateTime.class, (JsonSerializer<DateTime>) (dateTime, typeOfSrc, context) ->
                        new JsonPrimitive(ISODateTimeFormat.dateTime().print(dateTime)))
                .registerTypeAdapter(DateTime.class, (JsonDeserializer<DateTime>) (json, typeOfT, context) ->
                        ISODateTimeFormat.dateTime().parseDateTime(json.getAsString()))
                .registerTypeAdapter(DateTimeZone.class, (JsonSerializer<DateTimeZone>) (timeZone, typeOfSrc, context) ->
                        new JsonPrimitive(timeZone.getID()))
                .registerTypeAdapter(DateTimeZone.class, (JsonDeserializer<DateTimeZone>) (json, typeOfT, context) ->
                        DateTimeZone.forID(json.getAsString()));

        return gsonBuilder.create();
    }

    public Gson getGsonInstance() {
        return this.gsonInstance;
    }

    public AAPSLogger getAapsLogger() {
        return this.aapsLogger;
    }

    public SP getSp() {
        return this.sp;
    }

    public List<String> getTranslatedActiveAlerts(PodStateManager podStateManager) {
        List<String> translatedAlerts = new ArrayList<>();
        AlertSet activeAlerts = podStateManager.getActiveAlerts();

        for (AlertSlot alertSlot : activeAlerts.getAlertSlots()) {
            translatedAlerts.add(translateAlertType(podStateManager.getConfiguredAlertType(alertSlot)));
        }
        return translatedAlerts;
    }

    private String translateAlertType(AlertType alertType) {
        if (alertType == null) {
            return resourceHelper.gs(R.string.omnipod_alert_unknown_alert);
        }
        switch (alertType) {
            case FINISH_PAIRING_REMINDER:
                return resourceHelper.gs(R.string.omnipod_alert_finish_pairing_reminder);
            case FINISH_SETUP_REMINDER:
                return resourceHelper.gs(R.string.omnipod_alert_finish_setup_reminder_reminder);
            case EXPIRATION_ALERT:
                return resourceHelper.gs(R.string.omnipod_alert_expiration);
            case EXPIRATION_ADVISORY_ALERT:
                return resourceHelper.gs(R.string.omnipod_alert_expiration_advisory);
            case SHUTDOWN_IMMINENT_ALARM:
                return resourceHelper.gs(R.string.omnipod_alert_shutdown_imminent);
            case LOW_RESERVOIR_ALERT:
                return resourceHelper.gs(R.string.omnipod_alert_low_reservoir);
            default:
                return alertType.name();
        }
    }
}
