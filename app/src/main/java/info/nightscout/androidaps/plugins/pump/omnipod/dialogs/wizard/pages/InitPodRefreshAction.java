package info.nightscout.androidaps.plugins.pump.omnipod.dialogs.wizard.pages;

import com.atech.android.library.wizardpager.defs.action.AbstractCancelAction;
import com.atech.android.library.wizardpager.defs.action.FinishActionInterface;

import org.json.JSONException;
import org.json.JSONObject;

import javax.inject.Inject;

import dagger.android.HasAndroidInjector;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.db.CareportalEvent;
import info.nightscout.androidaps.db.Source;
import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.logging.LTag;
import info.nightscout.androidaps.plugins.general.nsclient.NSUpload;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.SetupProgress;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.state.PodStateManager;
import info.nightscout.androidaps.plugins.pump.omnipod.dialogs.PodManagementActivity;
import info.nightscout.androidaps.plugins.pump.omnipod.dialogs.wizard.defs.PodActionType;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.OmnipodDriverState;
import info.nightscout.androidaps.plugins.pump.omnipod.util.OmnipodUtil;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.sharedPreferences.SP;


/**
 * Created by andy on 12/11/2019
 */
public class InitPodRefreshAction extends AbstractCancelAction implements FinishActionInterface {

    private PodManagementActivity podManagementActivity;
    private PodActionType actionType;

    @Inject OmnipodUtil omnipodUtil;
    @Inject PodStateManager podStateManager;
    @Inject AAPSLogger aapsLogger;
    @Inject SP sp;

    public InitPodRefreshAction(HasAndroidInjector injector, PodManagementActivity podManagementActivity, PodActionType actionType) {
        injector.androidInjector().inject(this);
        this.podManagementActivity = podManagementActivity;
        this.actionType = actionType;
    }

    @Override
    public void execute(String cancelReason) {
        if (cancelReason != null && cancelReason.trim().length() > 0) {
            this.cancelActionText = cancelReason;
        }

        if (this.cancelActionText.equals("Cancel")) {
            //AapsOmnipodManager.getInstance().resetPodStatus();
        }

        podManagementActivity.refreshButtons();
    }

    @Override
    public void execute() {
        if (actionType == PodActionType.InitPod) {
            if (podStateManager.getSetupProgress().isBefore(SetupProgress.COMPLETED)) {
                omnipodUtil.setDriverState(OmnipodDriverState.Initalized_PodInitializing);
            } else {
                omnipodUtil.setDriverState(OmnipodDriverState.Initalized_PodAttached);
                uploadCareportalEvent(System.currentTimeMillis(), CareportalEvent.SITECHANGE);
            }
        } else {
            omnipodUtil.setDriverState(OmnipodDriverState.Initalized_NoPod);
        }

        podManagementActivity.refreshButtons();
    }

    private void uploadCareportalEvent(long date, String event) {
        if (MainApp.getDbHelper().getCareportalEventFromTimestamp(date) != null)
            return;
        try {
            JSONObject data = new JSONObject();
            String enteredBy = sp.getString("careportal_enteredby", "");
            if (!enteredBy.equals("")) data.put("enteredBy", enteredBy);
            data.put("created_at", DateUtil.toISOString(date));
            data.put("eventType", event);
            CareportalEvent careportalEvent = new CareportalEvent();
            careportalEvent.date = date;
            careportalEvent.source = Source.USER;
            careportalEvent.eventType = event;
            careportalEvent.json = data.toString();
            MainApp.getDbHelper().createOrUpdate(careportalEvent);
            NSUpload.uploadCareportalEntryToNS(data);
        } catch (JSONException e) {
            aapsLogger.error(LTag.PUMPCOMM, "Unhandled exception when uploading SiteChange event.", e);
        }
    }


    @Override
    public String getFinishActionText() {
        return "Finish_OK";
    }
}
