package info.nightscout.androidaps.plugins.general.automation.triggers;

import android.app.Activity;
import android.graphics.Typeface;
import android.text.format.DateFormat;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.fragment.app.FragmentManager;

import com.google.common.base.Optional;
import com.wdullaer.materialdatetimepicker.date.DatePickerDialog;
import com.wdullaer.materialdatetimepicker.time.TimePickerDialog;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Calendar;
import java.util.GregorianCalendar;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.JsonHelper;
import info.nightscout.androidaps.utils.T;

public class TriggerTime extends Trigger {
    private static Logger log = LoggerFactory.getLogger(L.AUTOMATION);

    private long runAt;

    public TriggerTime() {
        runAt = DateUtil.now();
    }

    private TriggerTime(TriggerTime triggerTime) {
        super();
        lastRun = triggerTime.lastRun;
        runAt = triggerTime.runAt;
    }

    @Override
    public boolean shouldRun() {
        long now = DateUtil.now();
        if (now >= runAt && now - runAt < T.mins(5).msecs())
            if (lastRun < runAt) {
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
        try {
            data.put("runAt", runAt);
            data.put("lastRun", lastRun);
            object.put("type", TriggerTime.class.getName());
            object.put("data", data);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return object.toString();
    }

    @Override
    Trigger fromJSON(String data) {
        JSONObject o;
        try {
            o = new JSONObject(data);
            lastRun = JsonHelper.safeGetLong(o, "lastRun");
            runAt = JsonHelper.safeGetLong(o, "runAt");
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return this;
    }

    @Override
    public int friendlyName() {
        return R.string.time;
    }

    @Override
    public String friendlyDescription() {
        return MainApp.gs(R.string.atspecifiedtime, DateUtil.dateAndTimeString(runAt));
    }

    @Override
    public Optional<Integer> icon() {
        return Optional.of(R.drawable.ic_access_alarm_24dp);
    }

    TriggerTime runAt(long runAt) {
        this.runAt = runAt;
        return this;
    }

    TriggerTime lastRun(long lastRun) {
        this.lastRun = lastRun;
        return this;
    }

    @Override
    public Trigger duplicate() {
        return new TriggerTime(this);
    }

    long getRunAt() {
        return runAt;
    }

    @Override
    public void generateDialog(LinearLayout root, FragmentManager fragmentManager) {
        TextView label = new TextView(root.getContext());
        TextView dateButton = new TextView(root.getContext());
        TextView timeButton = new TextView(root.getContext());

        dateButton.setText(DateUtil.dateString(runAt));
        timeButton.setText(DateUtil.timeString(runAt));
        dateButton.setOnClickListener(view -> {
            GregorianCalendar calendar = new GregorianCalendar();
            calendar.setTimeInMillis(runAt);
            DatePickerDialog dpd = DatePickerDialog.newInstance(
                    (view1, year, monthOfYear, dayOfMonth) -> {
                        calendar.set(Calendar.YEAR, year);
                        calendar.set(Calendar.MONTH, monthOfYear);
                        calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                        runAt = calendar.getTimeInMillis();
                        dateButton.setText(DateUtil.dateString(runAt));
                    },
                    calendar.get(Calendar.YEAR),
                    calendar.get(Calendar.MONTH),
                    calendar.get(Calendar.DAY_OF_MONTH)
            );
            dpd.setThemeDark(true);
            dpd.dismissOnPause(true);
            Activity a = scanForActivity(root.getContext());
            if (a != null)
                dpd.show(a.getFragmentManager(), "DatePickerDialog");
        });
        timeButton.setOnClickListener(view -> {
            GregorianCalendar calendar = new GregorianCalendar();
            calendar.setTimeInMillis(runAt);
            TimePickerDialog tpd = TimePickerDialog.newInstance(
                    (view12, hourOfDay, minute, second) -> {
                        calendar.set(Calendar.HOUR_OF_DAY, hourOfDay);
                        calendar.set(Calendar.MINUTE, minute);
                        runAt = calendar.getTimeInMillis();
                        timeButton.setText(DateUtil.timeString(runAt));
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
        label.setText(MainApp.gs(R.string.atspecifiedtime, ""));
        label.setTypeface(label.getTypeface(), Typeface.BOLD);
        label.setPadding(px, px, px, px);
        dateButton.setPadding(px, px, px, px);
        timeButton.setPadding(px, px, px, px);

        LinearLayout l = new LinearLayout(root.getContext());
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        l.addView(label);
        l.addView(dateButton);
        l.addView(timeButton);
        root.addView(l);
    }
}
