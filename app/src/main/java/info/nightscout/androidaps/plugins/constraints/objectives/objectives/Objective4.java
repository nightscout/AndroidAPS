package info.nightscout.androidaps.plugins.constraints.objectives.objectives;

import dagger.android.HasAndroidInjector;
import info.nightscout.androidaps.R;

public class Objective4 extends Objective {

    public Objective4(HasAndroidInjector injector) {
        super(injector, "maxbasal", R.string.objectives_maxbasal_objective, R.string.objectives_maxbasal_gate);
    }
}
