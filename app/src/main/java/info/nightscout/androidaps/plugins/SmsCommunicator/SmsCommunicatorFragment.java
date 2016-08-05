package info.nightscout.androidaps.plugins.SmsCommunicator;


import android.app.Activity;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.squareup.otto.Subscribe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DateFormat;
import java.util.Collections;
import java.util.Comparator;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.plugins.SmsCommunicator.events.EventSmsCommunicatorUpdateGui;

/**
 * A simple {@link Fragment} subclass.
 */
public class SmsCommunicatorFragment extends Fragment {
    private static Logger log = LoggerFactory.getLogger(SmsCommunicatorFragment.class);

    private static SmsCommunicatorPlugin smsCommunicatorPlugin = new SmsCommunicatorPlugin();

    public static SmsCommunicatorPlugin getPlugin() {
        return smsCommunicatorPlugin;
    }

    TextView logView;

    public SmsCommunicatorFragment() {
        super();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.smscommunicator_fragment, container, false);

        logView = (TextView) view.findViewById(R.id.smscommunicator_log);

        updateGUI();
        return view;
    }

    @Override
    public void onPause() {
        super.onPause();
        MainApp.bus().unregister(this);
    }

    @Override
    public void onResume() {
        super.onResume();
        MainApp.bus().register(this);
    }

    @Subscribe
    public void onStatusEvent(final EventSmsCommunicatorUpdateGui ev) {
        updateGUI();
    }


    private void updateGUI() {
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
                    Collections.sort(smsCommunicatorPlugin.messages, new CustomComparator());
                    int messagesToShow = 40;

                    int start = Math.max(0, smsCommunicatorPlugin.messages.size() - messagesToShow);
                    DateFormat df = DateFormat.getTimeInstance(DateFormat.SHORT);

                    String logText = "";
                    for (int x = start; x < smsCommunicatorPlugin.messages.size(); x++) {
                        SmsCommunicatorPlugin.Sms sms = smsCommunicatorPlugin.messages.get(x);
                        if (sms.received) {
                            logText += df.format(sms.date) + " &lt;&lt;&lt; " + (sms.processed ? "● " : "○ ") + sms.phoneNumber + " <b>" + sms.text + "</b><br>";
                        } else if (sms.sent) {
                            logText += df.format(sms.date) + " &gt;&gt;&gt; " + (sms.processed ? "● " : "○ ") + sms.phoneNumber + " <b>" + sms.text + "</b><br>";
                        }
                    }
                    logView.setText(Html.fromHtml(logText));
                }
            });
    }
}
