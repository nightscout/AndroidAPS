package info.nightscout.androidaps.plugins.pump.omnipod.dialogs.wizard.model;

import android.content.Context;

import androidx.fragment.app.Fragment;

import com.tech.freak.wizardpager.model.AbstractWizardModel;

import info.nightscout.androidaps.plugins.pump.omnipod.dialogs.wizard.pages.PodInfoFragment;

public abstract class InitPodWizardModel extends AbstractWizardModel {
    public InitPodWizardModel(Context context) {
        super(context);
    }

    @Override
    public Fragment getReviewFragment() {
        return PodInfoFragment.create("initPodInfoFragment", true);
    }
}
