package info.nightscout.androidaps.plugins.pump.omnipod.dialogs.wizard.initpod;

import com.atech.android.library.wizardpager.defs.action.AbstractCancelAction;

import info.nightscout.androidaps.plugins.pump.omnipod.driver.comm.AapsOmnipodManager;

/**
 * Created by andy on 12/11/2019
 */
public class InitPodCancelAction extends AbstractCancelAction {
    @Override
    public void execute(String cancelReason) {
        if (cancelReason != null && cancelReason.trim().length() > 0) {
            this.cancelActionText = cancelReason;
        }

        if (this.cancelActionText.equals("Cancel")) {
            AapsOmnipodManager.getInstance().resetPodStatus();
        }

    }
}
