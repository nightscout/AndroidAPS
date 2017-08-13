package info.nightscout.androidaps.plugins.InsulinRapidActingOref;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.Iob;
import info.nightscout.androidaps.db.Treatment;
import info.nightscout.androidaps.interfaces.InsulinInterface;
import info.nightscout.androidaps.interfaces.PluginBase;

/**
 * Created by mike on 17.04.2017.
 */

public class InsulinRapidActingOrefPlugin implements PluginBase, InsulinInterface {

    private static boolean fragmentEnabled = false;
    private static boolean fragmentVisible = false;

    @Override
    public int getType() {
        return INSULIN;
    }

    @Override
    public String getFragmentClass() {
        return InsulinRapidActingOrefFragment.class.getName();
    }

    @Override
    public String getName() {
        return "Rapid-Acting Oref";
    }

    @Override
    public String getNameShort() {
        return MainApp.sResources.getString(R.string.insulin_shortname);
    }

    @Override
    public boolean isEnabled(int type) {
        return type == INSULIN && fragmentEnabled;
    }

    @Override
    public boolean isVisibleInTabs(int type) {
        return type == INSULIN && fragmentVisible;
    }

    @Override
    public boolean canBeHidden(int type) {
        return true;
    }

    @Override
    public boolean hasFragment() {
        return true;
    }

    @Override
    public boolean showInList(int type) {
        return true;
    }

    @Override
    public void setFragmentEnabled(int type, boolean fragmentEnabled) {
        if (type == INSULIN) this.fragmentEnabled = fragmentEnabled;
    }

    @Override
    public void setFragmentVisible(int type, boolean fragmentVisible) {
        if (type == INSULIN) this.fragmentVisible = fragmentVisible;
    }

    // Insulin interface
    @Override
    public int getId() {
        return FASTACTINGINSULINPROLONGED;
    }

    @Override
    public String getFriendlyName() {
        return "Rapid-Acting Oref";
    }

    @Override
    public String getComment() {
        return MainApp.sResources.getString(R.string.fastactinginsulincomment);
    }

    @Override
    public double getDia() {
        //TODO: dynamic dia fetching
        return 6d;
        //return MainApp.getConfigBuilder().getProfile() != null ? MainApp.getConfigBuilder().getProfile().getDia() : Constants.defaultDIA;
    }

    @Override
    public Iob iobCalcForTreatment(Treatment treatment, long time, Double dia) {
        Iob result = new Iob();


        //curveDefaults

        int peak= 75;


        if (treatment.insulin != 0d) {

            long bolusTime = treatment.date;
            double t = (time - bolusTime) / 1000d / 60d;

            double td = getDia()*60;

            if(getDia() < 5){
                //TODO: Check that DIA is > 5 hours for this plugin!
                td = 300;
            }

            double tp = peak;


            // force the IOB to 0 if over 5 hours have passed
            if (t < td) {
                double tau = tp * (1 - tp / td) / (1 - 2 * tp / td);
                double a = 2 * tau / td;
                double S = 1 / (1 - a + (1 + a) * Math.exp(-td / tau));
                result.activityContrib = treatment.insulin * (S / Math.pow(tau, 2)) * t * (1 - t / td) * Math.exp(-t / tau);
                result.iobContrib = treatment.insulin * (1 - S * (1 - a) * ((Math.pow(t, 2) / (tau * td * (1 - a)) - t / tau - 1) * Math.exp(-t / tau) + 1));

            }
        }
        return result;
    }
}
