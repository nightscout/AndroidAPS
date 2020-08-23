package info.nightscout.androidaps.plugins.pump.omnipod.ui.wizard.removepod;

import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;

import com.tech.freak.wizardpager.model.ModelCallbacks;

import info.nightscout.androidaps.plugins.pump.omnipod.definition.PodInitActionType;
import info.nightscout.androidaps.plugins.pump.omnipod.ui.wizard.initpod.InitActionPage;


/**
 * Created by andy on 12/11/2019
 */
public class RemovePodActionPage extends InitActionPage {

    public RemovePodActionPage(ModelCallbacks callbacks, String title) {
        super(callbacks, title);
    }

    public RemovePodActionPage(ModelCallbacks callbacks, @StringRes int titleId, PodInitActionType podInitActionType) {
        super(callbacks, titleId, podInitActionType);
    }

    @Override
    public Fragment createFragment() {
        return RemoveActionFragment.create(getKey(), this.podInitActionType);
    }

}
