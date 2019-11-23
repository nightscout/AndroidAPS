package info.nightscout.androidaps.plugins.pump.omnipod.dialogs.wizard.initpod;

import com.atech.android.library.wizardpager.defs.action.AbstractCancelAction;

public class InitPodCancelAction extends AbstractCancelAction {
    @Override
    public void execute(String cancelReason) {
        if (cancelReason != null && cancelReason.trim().length() > 0) {
            this.cancelActionText = cancelReason;
        }

        if (this.cancelActionText.equals("Cancel")) {
            // TODO
            // remove pod from SP

            // do refresh of tab
        }


    }
}
