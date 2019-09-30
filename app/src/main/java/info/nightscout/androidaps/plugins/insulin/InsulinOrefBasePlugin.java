package info.nightscout.androidaps.plugins.insulin;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.Iob;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.interfaces.InsulinInterface;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.interfaces.PluginDescription;
import info.nightscout.androidaps.interfaces.PluginType;
import info.nightscout.androidaps.plugins.bus.RxBus;
import info.nightscout.androidaps.plugins.configBuilder.ProfileFunctions;
import info.nightscout.androidaps.plugins.general.overview.events.EventNewNotification;
import info.nightscout.androidaps.plugins.general.overview.notifications.Notification;
import info.nightscout.androidaps.plugins.treatments.Treatment;

/**
 * Created by adrian on 13.08.2017.
 */

public abstract class InsulinOrefBasePlugin extends PluginBase implements InsulinInterface {

    public static double MIN_DIA = 5;

    long lastWarned = 0;

    public InsulinOrefBasePlugin() {
        super(new PluginDescription()
                .mainType(PluginType.INSULIN)
                .fragmentClass(InsulinFragment.class.getName())
                .pluginName(R.string.fastactinginsulin)
                .shortName(R.string.insulin_shortname)
                .visibleByDefault(false)
        );
    }

    @Override
    public double getDia() {
        double dia = getUserDefinedDia();
        if (dia >= MIN_DIA) {
            return dia;
        } else {
            sendShortDiaNotification(dia);
            return MIN_DIA;
        }
    }

    void sendShortDiaNotification(double dia) {
        if ((System.currentTimeMillis() - lastWarned) > 60 * 1000) {
            lastWarned = System.currentTimeMillis();
            Notification notification = new Notification(Notification.SHORT_DIA, String.format(this.getNotificationPattern(), dia, MIN_DIA), Notification.URGENT);
            RxBus.INSTANCE.send(new EventNewNotification(notification));
        }
    }

    public String getNotificationPattern() {
        return MainApp.gs(R.string.dia_too_short);
    }

    public double getUserDefinedDia() {
        Profile profile = ProfileFunctions.getInstance().getProfile();
        return profile != null ? profile.getDia() : MIN_DIA;
    }

    public Iob iobCalcForTreatment(Treatment treatment, long time) {
        return this.iobCalcForTreatment(treatment, time, 0d);
    }

    @Override
    public Iob iobCalcForTreatment(Treatment treatment, long time, double dia) {
        Iob result = new Iob();

        int peak = getPeak();

        if (treatment.insulin != 0d) {

            long bolusTime = treatment.date;
            double t = (time - bolusTime) / 1000d / 60d;

            double td = getDia() * 60; //getDIA() always >= MIN_DIA
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
        String comment = commentStandardText();
        double userDia = getUserDefinedDia();
        if (userDia < MIN_DIA) {
            comment += "\n" + String.format(MainApp.gs(R.string.dia_too_short), userDia, MIN_DIA);
        }
        return comment;
    }

    abstract int getPeak();

    abstract String commentStandardText();

}
