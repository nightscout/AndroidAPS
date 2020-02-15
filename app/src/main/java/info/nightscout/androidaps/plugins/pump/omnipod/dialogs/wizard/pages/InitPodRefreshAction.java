package info.nightscout.androidaps.plugins.pump.omnipod.dialogs.wizard.pages;

import com.atech.android.library.wizardpager.defs.action.AbstractCancelAction;
import com.atech.android.library.wizardpager.defs.action.FinishActionInterface;

import info.nightscout.androidaps.plugins.pump.omnipod.dialogs.PodManagementActivity;
import info.nightscout.androidaps.plugins.pump.omnipod.dialogs.wizard.defs.PodActionType;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.OmnipodDriverState;
import info.nightscout.androidaps.plugins.pump.omnipod.util.OmnipodUtil;

/**
 * Created by andy on 12/11/2019
 */
public class InitPodRefreshAction extends AbstractCancelAction implements FinishActionInterface {

    private PodManagementActivity podManagementActivity;
    private PodActionType actionType;

    public InitPodRefreshAction(PodManagementActivity podManagementActivity, PodActionType actionType) {
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
        OmnipodUtil.setDriverState(actionType==PodActionType.InitPod ?
                OmnipodDriverState.Initalized_PodAvailable : OmnipodDriverState.Initalized_NoPod);

        podManagementActivity.refreshButtons();
    }

    @Override
    public String getFinishActionText() {
        return "Finish_OK";
    }
}
