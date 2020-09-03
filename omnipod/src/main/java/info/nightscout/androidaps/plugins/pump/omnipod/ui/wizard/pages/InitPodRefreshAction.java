package info.nightscout.androidaps.plugins.pump.omnipod.ui.wizard.pages;

import com.atech.android.library.wizardpager.defs.action.AbstractCancelAction;
import com.atech.android.library.wizardpager.defs.action.FinishActionInterface;

import org.json.JSONException;
import org.json.JSONObject;

import javax.inject.Inject;

import dagger.android.HasAndroidInjector;
import info.nightscout.androidaps.db.CareportalEvent;
import info.nightscout.androidaps.db.Source;
import info.nightscout.androidaps.interfaces.DatabaseHelperInterface;
import info.nightscout.androidaps.interfaces.ProfileFunction;
import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.logging.LTag;
import info.nightscout.androidaps.plugins.general.nsclient.NSUpload;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.manager.PodStateManager;
import info.nightscout.androidaps.plugins.pump.omnipod.ui.wizard.defs.PodActionType;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.sharedPreferences.SP;


/**
 * Created by andy on 12/11/2019
 */
public class InitPodRefreshAction extends AbstractCancelAction implements FinishActionInterface {

    private final PodActionType actionType;
    private final HasAndroidInjector injector;

    @Inject PodStateManager podStateManager;
    @Inject AAPSLogger aapsLogger;
    @Inject SP sp;
    @Inject NSUpload nsUpload;
    @Inject DatabaseHelperInterface databaseHelper;
    @Inject ProfileFunction profileFunction;

    public InitPodRefreshAction(HasAndroidInjector injector, PodActionType actionType) {
        this.injector = injector;
        injector.androidInjector().inject(this);
        this.actionType = actionType;
    }

    @Override
    public void execute(String cancelReason) {
        if (cancelReason != null && cancelReason.trim().length() > 0) {
            this.cancelActionText = cancelReason;
        }
    }

    @Override
    public void execute() {
        if (actionType == PodActionType.INIT_POD) {
            if (podStateManager.isPodRunning()) {
                uploadCareportalEvent(System.currentTimeMillis() - 2000, CareportalEvent.PUMPBATTERYCHANGE);
                uploadCareportalEvent(System.currentTimeMillis() - 1000, CareportalEvent.INSULINCHANGE);
                uploadCareportalEvent(System.currentTimeMillis(), CareportalEvent.SITECHANGE);
            }
        }
    }

    private void uploadCareportalEvent(long date, String event) {
        if (databaseHelper.getCareportalEventFromTimestamp(date) != null)
            return;
        try {
            JSONObject data = new JSONObject();
            String enteredBy = sp.getString("careportal_enteredby", "");
            if (enteredBy.isEmpty()) {
                data.put("enteredBy", enteredBy);
            }
            data.put("created_at", DateUtil.toISOString(date));
            data.put("mills", date);
            data.put("eventType", event);
            data.put("units", profileFunction.getUnits());
            CareportalEvent careportalEvent = new CareportalEvent(injector);
            careportalEvent.date = date;
            careportalEvent.source = Source.USER;
            careportalEvent.eventType = event;
            careportalEvent.json = data.toString();
            databaseHelper.createOrUpdate(careportalEvent);
            nsUpload.uploadCareportalEntryToNS(data);
        } catch (JSONException e) {
            aapsLogger.error(LTag.PUMPCOMM, "Unhandled exception when uploading SiteChange event.", e);
        }
    }

    @Override
    public String getFinishActionText() {
        return "Finish_OK";
    }
}
