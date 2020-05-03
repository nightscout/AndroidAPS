package info.nightscout.androidaps.plugins.pump.omnipod.dialogs.wizard.pages;

import androidx.fragment.app.Fragment;

import com.tech.freak.wizardpager.model.ModelCallbacks;
import com.tech.freak.wizardpager.model.Page;
import com.tech.freak.wizardpager.model.ReviewItem;

import java.util.ArrayList;


/**
 * Created by andy on 12/11/2019
 */
public class PodInfoPage extends Page {

    public PodInfoPage(ModelCallbacks callbacks, String title) {
        super(callbacks, title);
    }

    @Override
    public Fragment createFragment() {
        return PodInfoFragment.create(getKey(), true);
    }

    @Override
    public void getReviewItems(ArrayList<ReviewItem> dest) {
    }

    @Override
    public boolean isCompleted() {
        return true;
    }
}
