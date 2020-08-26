package info.nightscout.androidaps.plugins.pump.omnipod.ui.wizard.initpod;

import android.os.AsyncTask;
import android.view.View;

import javax.inject.Inject;

import dagger.android.HasAndroidInjector;
import info.nightscout.androidaps.interfaces.ProfileFunction;
import info.nightscout.androidaps.plugins.bus.RxBusWrapper;
import info.nightscout.androidaps.plugins.pump.omnipod.definition.PodInitActionType;
import info.nightscout.androidaps.plugins.pump.omnipod.event.EventOmnipodPumpValuesChanged;
import info.nightscout.androidaps.plugins.pump.omnipod.manager.AapsOmnipodManager;

/**
 * Created by andy on 11/12/2019
 */
public class InitPodTask extends AsyncTask<Void, Void, String> {

    @Inject ProfileFunction profileFunction;
    @Inject AapsOmnipodManager aapsOmnipodManager;
    @Inject RxBusWrapper rxBus;
    private final InitActionFragment initActionFragment;

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
        if (initActionFragment.podInitActionType == PodInitActionType.PAIR_AND_PRIME_WIZARD_STEP) {
            initActionFragment.callResult = aapsOmnipodManager.pairAndPrime(
                    initActionFragment.podInitActionType,
                    initActionFragment
            );
        } else if (initActionFragment.podInitActionType == PodInitActionType.FILL_CANNULA_SET_BASAL_PROFILE_WIZARD_STEP) {
            initActionFragment.callResult = aapsOmnipodManager.setInitialBasalScheduleAndInsertCannula(
                    initActionFragment.podInitActionType,
                    initActionFragment,
                    profileFunction.getProfile()
            );
        } else if (initActionFragment.podInitActionType == PodInitActionType.DEACTIVATE_POD_WIZARD_STEP) {
            initActionFragment.callResult = aapsOmnipodManager.deactivatePod(initActionFragment);
        }

        rxBus.send(new EventOmnipodPumpValuesChanged());

        return "OK";
    }

    @Override
    protected void onPostExecute(String result) {
        super.onPostExecute(result);

        initActionFragment.actionOnReceiveResponse(result);
    }

}
