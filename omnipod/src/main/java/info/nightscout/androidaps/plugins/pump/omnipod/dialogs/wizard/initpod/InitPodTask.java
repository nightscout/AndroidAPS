package info.nightscout.androidaps.plugins.pump.omnipod.dialogs.wizard.initpod;

import android.os.AsyncTask;
import android.view.View;

import javax.inject.Inject;

import dagger.android.HasAndroidInjector;
import info.nightscout.androidaps.interfaces.ProfileFunction;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.PodInitActionType;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.comm.AapsOmnipodManager;

/**
 * Created by andy on 11/12/2019
 */
public class InitPodTask extends AsyncTask<Void, Void, String> {

    @Inject ProfileFunction profileFunction;
    @Inject AapsOmnipodManager aapsOmnipodManager;
    private InitActionFragment initActionFragment;

    public InitPodTask(HasAndroidInjector injector, InitActionFragment initActionFragment) {
        injector.androidInjector().inject(this);
        this.initActionFragment = initActionFragment;
    }

    @Override
    protected void onPreExecute() {
        super.onPreExecute();
        initActionFragment.progressBar.setVisibility(View.VISIBLE);
        initActionFragment.errorView.setVisibility(View.GONE);
        initActionFragment.retryButton.setVisibility(View.GONE);
    }

    @Override
    protected String doInBackground(Void... params) {
        if (initActionFragment.podInitActionType == PodInitActionType.PairAndPrimeWizardStep) {
            initActionFragment.callResult = aapsOmnipodManager.initPod(
                    initActionFragment.podInitActionType,
                    initActionFragment,
                    null
            );
        } else if (initActionFragment.podInitActionType == PodInitActionType.FillCannulaSetBasalProfileWizardStep) {
            initActionFragment.callResult = aapsOmnipodManager.initPod(
                    initActionFragment.podInitActionType,
                    initActionFragment,
                    profileFunction.getProfile()
            );
        } else if (initActionFragment.podInitActionType == PodInitActionType.DeactivatePodWizardStep) {
            initActionFragment.callResult = aapsOmnipodManager.deactivatePod(initActionFragment);
        }

        return "OK";
    }

    @Override
    protected void onPostExecute(String result) {
        super.onPostExecute(result);

        initActionFragment.actionOnReceiveResponse(result);
    }

}
