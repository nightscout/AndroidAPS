package info.nightscout.androidaps.plugins.pump.omnipod.ui.wizard.model;

import android.content.Context;

import androidx.fragment.app.Fragment;

import com.tech.freak.wizardpager.model.AbstractWizardModel;

import info.nightscout.androidaps.plugins.pump.omnipod.ui.wizard.pages.PodInfoFragment;

abstract class InitPodWizardModel extends AbstractWizardModel {
    InitPodWizardModel(Context context) {
        super(context);
    }

    @Override
    public Fragment getReviewFragment() {
        return PodInfoFragment.create(true);
    }
}
