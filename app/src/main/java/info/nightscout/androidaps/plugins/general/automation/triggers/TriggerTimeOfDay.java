package info.nightscout.androidaps.plugins.general.automation.triggers;

import android.app.Activity;
import android.graphics.Typeface;
import android.text.format.DateFormat;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.fragment.app.FragmentManager;

import com.google.common.base.Optional;
import com.wdullaer.materialdatetimepicker.time.TimePickerDialog;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.general.automation.elements.Comparator;
import info.nightscout.androidaps.plugins.general.automation.elements.LayoutBuilder;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.JsonHelper;
import info.nightscout.androidaps.utils.T;


// Trigger for time range ( Time of day actually )

public class TriggerTimeOfDay extends Trigger {
    private static Logger log = LoggerFactory.getLogger(L.AUTOMATION);

    private int minSinceMidnight;
    private Comparator comparator = new Comparator();


    public TriggerTimeOfDay() {
        minSinceMidnight = getMinSinceMidnight(DateUtil.now());
    }

    private TriggerTimeOfDay(TriggerTimeOfDay triggerTimeOfDay) {
        super();
        lastRun = triggerTimeOfDay.lastRun;
        minSinceMidnight = triggerTimeOfDay.minSinceMidnight;
    }

    @Override
    public boolean shouldRun() {
        int currentMinSinceMidnight = getMinSinceMidnight(System.currentTimeMillis());

        if (lastRun > DateUtil.now() - T.mins(5).msecs())
            return false;

        boolean doRun = comparator.getValue().check(currentMinSinceMidnight, minSinceMidnight);
        if (doRun) {
            if (L.isEnabled(L.AUTOMATION))
                log.debug("Ready for execution: " + friendlyDescription());
            return true;
        }
        return false;
    }

    @Override
    public String toJSON() {
        JSONObject object = new JSONObject();
        JSONObject data = new JSONObject();

        // check for too big values
        if (minSinceMidnight > 1440)
            minSinceMidnight = getMinSinceMidnight(minSinceMidnight);

        try {
            data.put("minSinceMidnight", getMinSinceMidnight(minSinceMidnight));
            data.put("lastRun", lastRun);
            object.put("type", TriggerTimeOfDay.class.getName());
            data.put("comparator", comparator.getValue().toString());
            object.put("data", data);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        log.debug(object.toString());
        return object.toString();
    }

    @Override
    Trigger fromJSON(String data) {
        JSONObject o;
        try {
            o = new JSONObject(data);
            lastRun = JsonHelper.safeGetLong(o, "lastRun");
            minSinceMidnight = JsonHelper.safeGetInt(o, "minSinceMidnight");
            comparator.setValue(Comparator.Compare.valueOf(JsonHelper.safeGetString(o, "comparator")));
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return this;
    }

    @Override
    public int friendlyName() {
        return R.string.time_of_day;
    }

    @Override
    public String friendlyDescription() {
        return MainApp.gs(R.string.tod_value, MainApp.gs(comparator.getValue().getStringRes()), DateUtil.timeString(toMilis(minSinceMidnight)));
    }

    @Override
    public Optional<Integer> icon() {
        return Optional.of(R.drawable.ic_access_alarm_24dp);
    }

    TriggerTimeOfDay minSinceMidnight(int minSinceMidnight) {
        this.minSinceMidnight = minSinceMidnight;
        return this;
    }

    TriggerTimeOfDay lastRun(long lastRun) {
        this.lastRun = lastRun;
        return this;
    }

    @Override
    public Trigger duplicate() {
        return new TriggerTimeOfDay(this);
    }

    long toMilis(long minutesSinceMidnight) {
        long hours =  minSinceMidnight / 60;//hours
        long minutes = minSinceMidnight % 60;//hours
        return (hours*60*60*1000)+(minutes*60*1000);
    }

    int getMinSinceMidnight(long time) {
        // if passed argument is smaller than 1440 ( 24 h * 60 min ) that value is already converted
        if (time < 1441)
            return (int) time;
        Date date = new Date(time);
        Calendar calendar = GregorianCalendar.getInstance();
        calendar.setTime(date);
        return (calendar.get(Calendar.HOUR_OF_DAY) * 60) + calendar.get(Calendar.MINUTE);
    }

    @Override
    public void generateDialog(LinearLayout root, FragmentManager fragmentManager) {
        TextView label = new TextView(root.getContext());
        TextView timeButton = new TextView(root.getContext());

        timeButton.setText(DateUtil.timeString(toMilis(minSinceMidnight)));

        timeButton.setOnClickListener(view -> {
            GregorianCalendar calendar = new GregorianCalendar();
            calendar.setTimeInMillis(toMilis(minSinceMidnight));
            TimePickerDialog tpd = TimePickerDialog.newInstance(
                    (view12, hourOfDay, minute, second) -> {
                        calendar.set(Calendar.HOUR_OF_DAY, hourOfDay);
                        calendar.set(Calendar.MINUTE, minute);
                        minSinceMidnight = getMinSinceMidnight(calendar.getTimeInMillis());
                        timeButton.setText(DateUtil.timeString(minSinceMidnight));
                    },
                    calendar.get(Calendar.HOUR_OF_DAY),
                    calendar.get(Calendar.MINUTE),
                    DateFormat.is24HourFormat(root.getContext())
            );
            tpd.setThemeDark(true);
            tpd.dismissOnPause(true);
            Activity a = scanForActivity(root.getContext());
            if (a != null)
                tpd.show(a.getFragmentManager(), "TimePickerDialog");
        });

        int px = MainApp.dpToPx(10);
        label.setText(MainApp.gs(R.string.thanspecifiedtime, ""));
        label.setTypeface(label.getTypeface(), Typeface.BOLD);
        label.setPadding(px, px, px, px);
        timeButton.setPadding(px, px, px, px);
        new LayoutBuilder()
                .add(comparator)
                .build(root);
        LinearLayout l = new LinearLayout(root.getContext());
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        l.addView(label);
        l.addView(timeButton);
        root.addView(l);
    }
}
