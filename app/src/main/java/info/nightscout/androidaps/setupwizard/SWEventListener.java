package info.nightscout.androidaps.setupwizard;

import android.content.Context;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.events.EventStatus;
import info.nightscout.androidaps.plugins.bus.RxBus;
import info.nightscout.androidaps.setupwizard.elements.SWItem;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;


public class SWEventListener extends SWItem {
    private static Logger log = LoggerFactory.getLogger(SWEventListener.class);
    private CompositeDisposable disposable = new CompositeDisposable();

    private int textLabel = 0;
    private String status = "";
    TextView textView;
    SWDefinition definition;

    SWEventListener(SWDefinition definition, Class clazz) {
        super(Type.LISTENER);
        this.definition = definition;
        disposable.add(RxBus.INSTANCE
                .toObservable(clazz)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(event -> {
                    status = ((EventStatus) event).getStatus();
                    if (textView != null)
                        textView.setText((textLabel != 0 ? MainApp.gs(textLabel) : "") + " " + status);
                })
        );

    }

    public SWEventListener label(int newLabel) {
        this.textLabel = newLabel;
        return this;
    }

    SWEventListener initialStatus(String status) {
        this.status = status;
        return this;
    }

    @Override
    public void generateDialog(LinearLayout layout) {
        Context context = layout.getContext();

        textView = new TextView(context);
        textView.setId(layout.generateViewId());
        textView.setText((textLabel != 0 ? MainApp.gs(textLabel) : "") + " " + status);
        layout.addView(textView);
    }
}
