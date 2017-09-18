package info.nightscout.androidaps.plugins.Insulin;

import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.Iob;
import info.nightscout.androidaps.db.Treatment;
import info.nightscout.androidaps.interfaces.InsulinInterface;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.plugins.Overview.Notification;
import info.nightscout.androidaps.plugins.Overview.events.EventNewNotification;

/**
 * Created by adrian on 13.08.2017.
 */

public abstract class InsulinOrefBasePlugin implements PluginBase, InsulinInterface {

    public static double MIN_DIA = 5;

    long lastWarned = 0;

    @Override
    public int getType() {
        return INSULIN;
    }

    @Override
    public String getNameShort() {
        return MainApp.sResources.getString(R.string.insulin_shortname);
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
    public double getDia() {
        double dia = getUserDefinedDia();
        if(dia >= MIN_DIA){
            return dia;
        } else {
            if((System.currentTimeMillis() - lastWarned) > 60*1000) {
                lastWarned = System.currentTimeMillis();
                Notification notification = new Notification(Notification.SHORT_DIA, String.format(MainApp.sResources.getString(R.string.dia_too_short), dia, MIN_DIA), Notification.URGENT);
                MainApp.bus().post(new EventNewNotification(notification));
            }
            return MIN_DIA;
        }
    }

    public double getUserDefinedDia() {
        return MainApp.getConfigBuilder().getProfile() != null ? MainApp.getConfigBuilder().getProfile().getDia() : Constants.defaultDIA;
    }

    @Override
    public Iob iobCalcForTreatment(Treatment treatment, long time, Double dia) {
        Iob result = new Iob();

        int peak = getPeak();


        if (treatment.insulin != 0d) {

            long bolusTime = treatment.date;
            double t = (time - bolusTime) / 1000d / 60d;

            double td = getDia()*60; //getDIA() always > 5
            double tp = peak;

            // force the IOB to 0 if over DIA hours have passed
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

    @Override
    public String getComment() {
        String comment =  commentStandardText();
        double userDia = getUserDefinedDia();
        if(userDia < MIN_DIA){
            comment += "\n" + String.format(MainApp.sResources.getString(R.string.dia_too_short), userDia, MIN_DIA);
        }
        return comment;
    }

    abstract int getPeak();

    abstract String commentStandardText();

}
