package info.nightscout.androidaps.plugins.pump.omnipod.dialogs.wizard.model;

import android.content.Context;

import com.atech.android.library.wizardpager.model.DisplayTextPage;
import com.tech.freak.wizardpager.model.PageList;

import info.nightscout.androidaps.plugins.pump.omnipod.R;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.PodInitActionType;
import info.nightscout.androidaps.plugins.pump.omnipod.dialogs.wizard.initpod.InitActionPage;

/**
 * Created by andy on 12/11/2019
 */
// Full init pod wizard model
// Cannot be merged with ShortInitPodWizardModel, because we can't set any instance variables
// before the onNewRootPageList method is called (which happens in the super constructor)
public class FullInitPodWizardModel extends InitPodWizardModel {

    public FullInitPodWizardModel(Context context) {
        super(context);
    }

    @Override
    protected PageList onNewRootPageList() {
        return new PageList(
                new DisplayTextPage(this,
                        R.string.omnipod_init_pod_wizard_step1_title,
                        R.string.omnipod_init_pod_wizard_step1_desc,
                        R.style.WizardPagePodContent).setRequired(true).setCancelReason("None"),

                new InitActionPage(this,
                        R.string.omnipod_init_pod_wizard_step2_title,
                        PodInitActionType.PairAndPrimeWizardStep
                ).setRequired(true).setCancelReason("Cancel"),

                new DisplayTextPage(this,
                        R.string.omnipod_init_pod_wizard_step3_title,
                        R.string.omnipod_init_pod_wizard_step3_desc,
                        R.style.WizardPagePodContent).setRequired(true).setCancelReason("Cancel"),

                new InitActionPage(this,
                        R.string.omnipod_init_pod_wizard_step4_title,
                        PodInitActionType.FillCannulaSetBasalProfileWizardStep
                ).setRequired(true).setCancelReason("Cancel")
        );
    }
}
