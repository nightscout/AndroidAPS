package info.nightscout.androidaps.plugins.pump.omnipod.dialogs.wizard.pages;

import com.atech.android.library.wizardpager.defs.action.AbstractCancelAction;
import com.atech.android.library.wizardpager.defs.action.FinishActionInterface;

import info.nightscout.androidaps.plugins.pump.omnipod.dialogs.PodManagementActivity;

/**
 * Created by andy on 12/11/2019
 */
public class InitPodRefreshAction extends AbstractCancelAction implements FinishActionInterface {

    private PodManagementActivity podManagementActivity;

    public InitPodRefreshAction(PodManagementActivity podManagementActivity) {
        this.podManagementActivity = podManagementActivity;
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
        podManagementActivity.refreshButtons();
    }

    @Override
    public String getFinishActionText() {
        return "Finish_OK";
    }
}
