package info.nightscout.androidaps.plugins.pump.omnipod.dialogs.wizard.initpod;

import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;

import com.tech.freak.wizardpager.model.ModelCallbacks;
import com.tech.freak.wizardpager.model.Page;
import com.tech.freak.wizardpager.model.ReviewItem;

import java.util.ArrayList;

import info.nightscout.androidaps.plugins.pump.omnipod.defs.PodInitActionType;


/**
 * Created by andy on 12/11/2019
 * <p>
 * This page is for InitPod and RemovePod, but Fragments called for this 2 actions are different
 */
public class InitActionPage extends Page {

    protected PodInitActionType podInitActionType;

    protected boolean actionCompleted = false;
    protected boolean actionSuccess = false;

    public InitActionPage(ModelCallbacks callbacks, String title) {
        super(callbacks, title);
    }

    public InitActionPage(ModelCallbacks callbacks, @StringRes int titleId, PodInitActionType podInitActionType) {
        super(callbacks, titleId);
        this.podInitActionType = podInitActionType;
    }

    @Override
    public Fragment createFragment() {
        return InitActionFragment.create(getKey(), this.podInitActionType);
    }

    @Override
    public void getReviewItems(ArrayList<ReviewItem> dest) {
    }

    @Override
    public boolean isCompleted() {
        return actionCompleted;
    }

    public void setActionCompleted(boolean success) {
        this.actionCompleted = success;
        this.actionSuccess = success;
    }

    /**
     * This is used just if we want to override default behavior (for example when we enter Page we want prevent any action, until something happens.
     *
     * @return
     */
    public boolean isBackActionPossible() {
        return actionCompleted;
    }

    /**
     * This is used just if we want to override default behavior (for example when we enter Page we want prevent any action, until something happens.
     *
     * @return
     */
    public boolean isNextActionPossible() {
        return actionSuccess;
    }

}
