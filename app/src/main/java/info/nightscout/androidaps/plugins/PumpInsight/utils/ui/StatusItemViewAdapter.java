package info.nightscout.androidaps.plugins.PumpInsight.utils.ui;

import android.app.Activity;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import info.nightscout.androidaps.R;
import info.nightscout.androidaps.plugins.PumpInsight.utils.StatusItem;

/**
 * Created by jamorham on 26/01/2018.
 *
 * Convert StatusItem to View
 */

public class StatusItemViewAdapter {

    private final Activity activity;
    private final ViewGroup holder;

    public StatusItemViewAdapter(Activity activity, ViewGroup holder) {
        this.activity = activity;
        this.holder = holder;
    }

    public View inflateStatus(StatusItem statusItem) {
        if (activity == null) return null;
        final View child = activity.getLayoutInflater().inflate(R.layout.insightpump_statuselements, null);
        final TextView name = (TextView) child.findViewById(R.id.insightstatuslabel);
        final TextView value = (TextView)child.findViewById(R.id.insightstatusvalue);
        final TextView spacer = (TextView)child.findViewById(R.id.insightstatusspacer);
        final LinearLayout layout = (LinearLayout)child.findViewById(R.id.insightstatuslayout);

        if (statusItem.name.equals("line-break")) {
            spacer.setVisibility(View.GONE);
            name.setVisibility(View.GONE);
            value.setVisibility(View.GONE);
            layout.setPadding(10, 10, 10, 10);
        } else if (statusItem.name.equals("heading-break")) {
            value.setVisibility(View.GONE);
            spacer.setVisibility(View.GONE);
            name.setText(statusItem.value);
            name.setGravity(Gravity.CENTER_HORIZONTAL);
            name.setTextColor(Color.parseColor("#fff9c4"));
        } else {
            name.setText(statusItem.name);
            value.setText(statusItem.value);
        }

        final int this_color = getHighlightColor(statusItem);
        name.setBackgroundColor(this_color);
        value.setBackgroundColor(this_color);
        spacer.setBackgroundColor(this_color);

        if (this_color != Color.TRANSPARENT) {
            name.setTextColor(Color.WHITE);
            spacer.setTextColor(Color.WHITE);
        }

        if (holder != null) {
            holder.addView(child);
        }
        return child;
    }

    private static int getHighlightColor(StatusItem row) {
        switch (row.highlight) {
            case BAD:
                return Color.parseColor("#480000");
            case NOTICE:
                return Color.parseColor("#403000");
            case GOOD:
                return Color.parseColor("#003000");
            case CRITICAL:
                return Color.parseColor("#770000");
            default:
                return Color.TRANSPARENT;
        }
    }

}
