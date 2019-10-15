package info.nightscout.androidaps.plugins.general.smsCommunicator;


import android.os.Bundle;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.fragment.app.Fragment;

import java.util.Collections;
import java.util.Comparator;

import info.nightscout.androidaps.R;
import info.nightscout.androidaps.plugins.bus.RxBus;
import info.nightscout.androidaps.plugins.general.smsCommunicator.events.EventSmsCommunicatorUpdateGui;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.FabricPrivacy;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;

public class SmsCommunicatorFragment extends Fragment {
    private CompositeDisposable disposable = new CompositeDisposable();
    TextView logView;

    public SmsCommunicatorFragment() {
        super();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.smscommunicator_fragment, container, false);

        logView = (TextView) view.findViewById(R.id.smscommunicator_log);

        return view;
    }

    @Override
    public synchronized void onResume() {
        super.onResume();
        disposable.add(RxBus.INSTANCE
                .toObservable(EventSmsCommunicatorUpdateGui.class)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(event -> updateGui(), FabricPrivacy::logException)
        );
        updateGui();
    }

    @Override
    public synchronized void onPause() {
        super.onPause();
        disposable.clear();
    }

    protected void updateGui() {
        class CustomComparator implements Comparator<Sms> {
            public int compare(Sms object1, Sms object2) {
                return (int) (object1.date - object2.date);
            }
        }
        Collections.sort(SmsCommunicatorPlugin.getPlugin().messages, new CustomComparator());
        int messagesToShow = 40;

        int start = Math.max(0, SmsCommunicatorPlugin.getPlugin().messages.size() - messagesToShow);

        String logText = "";
        for (int x = start; x < SmsCommunicatorPlugin.getPlugin().messages.size(); x++) {
            Sms sms = SmsCommunicatorPlugin.getPlugin().messages.get(x);
            if (sms.ignored) {
                logText += DateUtil.timeString(sms.date) + " &lt;&lt;&lt; " + "░ " + sms.phoneNumber + " <b>" + sms.text + "</b><br>";
            } else if (sms.received) {
                logText += DateUtil.timeString(sms.date) + " &lt;&lt;&lt; " + (sms.processed ? "● " : "○ ") + sms.phoneNumber + " <b>" + sms.text + "</b><br>";
            } else if (sms.sent) {
                logText += DateUtil.timeString(sms.date) + " &gt;&gt;&gt; " + (sms.processed ? "● " : "○ ") + sms.phoneNumber + " <b>" + sms.text + "</b><br>";
            }
        }
        logView.setText(Html.fromHtml(logText));
    }
}
