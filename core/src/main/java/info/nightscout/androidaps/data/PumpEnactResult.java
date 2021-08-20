package info.nightscout.androidaps.data;

import org.json.JSONException;
import org.json.JSONObject;

import javax.inject.Inject;

import dagger.android.HasAndroidInjector;
import info.nightscout.androidaps.core.R;
import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.utils.DecimalFormatter;
import info.nightscout.androidaps.utils.Round;
import info.nightscout.androidaps.utils.resources.ResourceHelper;

public class PumpEnactResult {
    @Inject public AAPSLogger aapsLogger;
    @Inject public ResourceHelper resourceHelper;

    public boolean success = false;    // request was processed successfully (but possible no change was needed)
    public boolean enacted = false;    // request was processed successfully and change has been made
    public String comment = "";

    // Result of basal change
    public int duration = -1;      // duration set [minutes]
    public double absolute = -1d;      // absolute rate [U/h] , isPercent = false
    public int percent = -1;       // percent of current basal [%] (100% = current basal), isPercent = true
    public boolean isPercent = false;  // if true percent is used, otherwise absolute
    public boolean isTempCancel = false; // if true we are caceling temp basal
    // Result of treatment delivery
    public double bolusDelivered = 0d; // real value of delivered insulin
    public double carbsDelivered = 0d; // real value of delivered carbs

    public boolean queued = false;

    public PumpEnactResult(HasAndroidInjector injector) {
        injector.androidInjector().inject(this);
    }

    public PumpEnactResult success(boolean success) {
        this.success = success;
        return this;
    }

    public PumpEnactResult enacted(boolean enacted) {
        this.enacted = enacted;
        return this;
    }

    public PumpEnactResult comment(String comment) {
        this.comment = comment;
        return this;
    }

    public PumpEnactResult comment(int comment) {
        this.comment = resourceHelper.gs(comment);
        return this;
    }

    public PumpEnactResult duration(int duration) {
        this.duration = duration;
        return this;
    }

    public PumpEnactResult absolute(double absolute) {
        this.absolute = absolute;
        return this;
    }

    public PumpEnactResult percent(int percent) {
        this.percent = percent;
        return this;
    }

    public PumpEnactResult isPercent(boolean isPercent) {
        this.isPercent = isPercent;
        return this;
    }

    public PumpEnactResult isTempCancel(boolean isTempCancel) {
        this.isTempCancel = isTempCancel;
        return this;
    }

    public PumpEnactResult bolusDelivered(double bolusDelivered) {
        this.bolusDelivered = bolusDelivered;
        return this;
    }

    public PumpEnactResult carbsDelivered(double carbsDelivered) {
        this.carbsDelivered = carbsDelivered;
        return this;
    }

    public PumpEnactResult queued(boolean queued) {
        this.queued = queued;
        return this;
    }

    public String log() {
        return "Success: " + success +
                " Enacted: " + enacted +
                " Comment: " + comment +
                " Duration: " + duration +
                " Absolute: " + absolute +
                " Percent: " + percent +
                " IsPercent: " + isPercent +
                " IsTempCancel: " + isTempCancel +
                " bolusDelivered: " + bolusDelivered +
                " carbsDelivered: " + carbsDelivered +
                " Queued: " + queued;
    }

    public String toString() {
        String ret = resourceHelper.gs(R.string.success) + ": " + success;
        if (enacted) {
            if (bolusDelivered > 0) {
                ret += "\n" + resourceHelper.gs(R.string.enacted) + ": " + enacted;
                ret += "\n" + resourceHelper.gs(R.string.comment) + ": " + comment;
                ret += "\n" + resourceHelper.gs(R.string.configbuilder_insulin)
                        + ": " + bolusDelivered + " " + resourceHelper.gs(R.string.insulin_unit_shortname);
            } else if (isTempCancel) {
                ret += "\n" + resourceHelper.gs(R.string.enacted) + ": " + enacted;
                if (!comment.isEmpty())
                    ret += "\n" + resourceHelper.gs(R.string.comment) + ": " + comment;
                ret += "\n" + resourceHelper.gs(R.string.canceltemp);
            } else if (isPercent) {
                ret += "\n" + resourceHelper.gs(R.string.enacted) + ": " + enacted;
                if (!comment.isEmpty())
                    ret += "\n" + resourceHelper.gs(R.string.comment) + ": " + comment;
                ret += "\n" + resourceHelper.gs(R.string.duration) + ": " + duration + " min";
                ret += "\n" + resourceHelper.gs(R.string.percent) + ": " + percent + "%";
            } else {
                ret += "\n" + resourceHelper.gs(R.string.enacted) + ": " + enacted;
                if (!comment.isEmpty())
                    ret += "\n" + resourceHelper.gs(R.string.comment) + ": " + comment;
                ret += "\n" + resourceHelper.gs(R.string.duration) + ": " + duration + " min";
                ret += "\n" + resourceHelper.gs(R.string.absolute) + ": " + absolute + " U/h";
            }
        } else {
            ret += "\n" + resourceHelper.gs(R.string.comment) + ": " + comment;
        }
        return ret;
    }

    public String toHtml() {
        String ret = "<b>" + resourceHelper.gs(R.string.success) + "</b>: " + success;
        if (queued) {
            ret = resourceHelper.gs(R.string.waitingforpumpresult);
        } else if (enacted) {
            if (bolusDelivered > 0) {
                ret += "<br><b>" + resourceHelper.gs(R.string.enacted) + "</b>: " + enacted;
                if (!comment.isEmpty())
                    ret += "<br><b>" + resourceHelper.gs(R.string.comment) + "</b>: " + comment;
                ret += "<br><b>" + resourceHelper.gs(R.string.smb_shortname) + "</b>: " + bolusDelivered + " " + resourceHelper.gs(R.string.insulin_unit_shortname);
            } else if (isTempCancel) {
                ret += "<br><b>" + resourceHelper.gs(R.string.enacted) + "</b>: " + enacted;
                ret += "<br><b>" + resourceHelper.gs(R.string.comment) + "</b>: " + comment +
                        "<br>" + resourceHelper.gs(R.string.canceltemp);
            } else if (isPercent && percent != -1) {
                ret += "<br><b>" + resourceHelper.gs(R.string.enacted) + "</b>: " + enacted;
                if (!comment.isEmpty())
                    ret += "<br><b>" + resourceHelper.gs(R.string.comment) + "</b>: " + comment;
                ret += "<br><b>" + resourceHelper.gs(R.string.duration) + "</b>: " + duration + " min";
                ret += "<br><b>" + resourceHelper.gs(R.string.percent) + "</b>: " + percent + "%";
            } else if (absolute != -1) {
                ret += "<br><b>" + resourceHelper.gs(R.string.enacted) + "</b>: " + enacted;
                if (!comment.isEmpty())
                    ret += "<br><b>" + resourceHelper.gs(R.string.comment) + "</b>: " + comment;
                ret += "<br><b>" + resourceHelper.gs(R.string.duration) + "</b>: " + duration + " min";
                ret += "<br><b>" + resourceHelper.gs(R.string.absolute) + "</b>: " + DecimalFormatter.to2Decimal(absolute) + " U/h";
            }
        } else {
            if (!comment.isEmpty())
                ret += "<br><b>" + resourceHelper.gs(R.string.comment) + "</b>: " + comment;
        }
        return ret;
    }

    public JSONObject json(Profile profile) {
        JSONObject result = new JSONObject();
        try {
            if (bolusDelivered > 0) {
                result.put("smb", bolusDelivered);
            } else if (isTempCancel) {
                result.put("rate", 0);
                result.put("duration", 0);
            } else if (isPercent) {
                // Nightscout is expecting absolute value
                Double abs = Round.roundTo(profile.getBasal() * percent / 100, 0.01);
                result.put("rate", abs);
                result.put("duration", duration);
            } else {
                result.put("rate", absolute);
                result.put("duration", duration);
            }
        } catch (JSONException e) {
            aapsLogger.error("Unhandled exception", e);
        }
        return result;
    }
}
