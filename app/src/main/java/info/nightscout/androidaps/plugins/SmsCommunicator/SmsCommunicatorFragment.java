package info.nightscout.androidaps.plugins.SmsCommunicator;


import android.app.Activity;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.crashlytics.android.Crashlytics;
import com.squareup.otto.Subscribe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Comparator;

import info.nightscout.androidaps.R;
import info.nightscout.androidaps.plugins.Common.SubscriberFragment;
import info.nightscout.androidaps.plugins.SmsCommunicator.events.EventSmsCommunicatorUpdateGui;
import info.nightscout.utils.DateUtil;

/**
 * A simple {@link Fragment} subclass.
 */
public class SmsCommunicatorFragment extends SubscriberFragment {
    private static Logger log = LoggerFactory.getLogger(SmsCommunicatorFragment.class);

    TextView logView;

    public SmsCommunicatorFragment() {
        super();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        try {
            View view = inflater.inflate(R.layout.smscommunicator_fragment, container, false);

            logView = (TextView) view.findViewById(R.id.smscommunicator_log);

            updateGUI();
            return view;
        } catch (Exception e) {
            Crashlytics.logException(e);
        }

        return null;
    }

    @Subscribe
    public void onStatusEvent(final EventSmsCommunicatorUpdateGui ev) {
        updateGUI();
    }


    @Override
    protected void updateGUI() {
        Activity activity = getActivity();
        if (activity != null)
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    class CustomComparator implements Comparator<SmsCommunicatorPlugin.Sms> {
                        public int compare(SmsCommunicatorPlugin.Sms object1, SmsCommunicatorPlugin.Sms object2) {
                            return (int) (object1.date.getTime() - object2.date.getTime());
                        }
                    }
                    Collections.sort(SmsCommunicatorPlugin.getPlugin().messages, new CustomComparator());
                    int messagesToShow = 40;

                    int start = Math.max(0, SmsCommunicatorPlugin.getPlugin().messages.size() - messagesToShow);

                    String logText = "";
                    for (int x = start; x < SmsCommunicatorPlugin.getPlugin().messages.size(); x++) {
                        SmsCommunicatorPlugin.Sms sms = SmsCommunicatorPlugin.getPlugin().messages.get(x);
                        if (sms.received) {
                            logText += DateUtil.timeString(sms.date) + " &lt;&lt;&lt; " + (sms.processed ? "● " : "○ ") + sms.phoneNumber + " <b>" + sms.text + "</b><br>";
                        } else if (sms.sent) {
                            logText += DateUtil.timeString(sms.date) + " &gt;&gt;&gt; " + (sms.processed ? "● " : "○ ") + sms.phoneNumber + " <b>" + sms.text + "</b><br>";
                        }
                    }
                    logView.setText(Html.fromHtml(logText));
                }
            });
    }
}
