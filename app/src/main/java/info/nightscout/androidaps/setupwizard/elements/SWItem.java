package info.nightscout.androidaps.setupwizard.elements;

import android.view.View;
import android.widget.LinearLayout;

import androidx.annotation.StringRes;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.events.EventPreferenceChange;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.bus.RxBus;
import info.nightscout.androidaps.setupwizard.events.EventSWUpdate;
import info.nightscout.androidaps.utils.SP;

public class SWItem {
    private static Logger log = LoggerFactory.getLogger(SWItem.class);

    private static final ScheduledExecutorService eventWorker = Executors.newSingleThreadScheduledExecutor();
    private static ScheduledFuture<?> scheduledEventPost = null;

    public enum Type {
        NONE,
        TEXT,
        HTMLLINK,
        BREAK,
        LISTENER,
        URL,
        STRING,
        NUMBER,
        DECIMALNUMBER,
        CHECKBOX,
        RADIOBUTTON,
        PLUGIN,
        BUTTON,
        FRAGMENT,
        UNITNUMBER
    }

    Type type;
    Integer label;
    Integer comment;
    int preferenceId;


    public SWItem(Type type) {
        this.type = type;
    }

    String getLabel() {
        return MainApp.gs(label);
    }

    String getComment() {
        if (comment != null)
            return MainApp.gs(comment);
        else
            return "";
    }

    Type getType() {
        return type;
    }

    public SWItem label(@StringRes int label) {
        this.label = label;
        return this;
    }

    public SWItem comment(@StringRes int comment) {
        this.comment = comment;
        return this;
    }

    public void save(String value, int updateDelay) {
        SP.putString(preferenceId, value);
        scheduleChange(updateDelay);
    }

    public static LinearLayout generateLayout(View view) {
        LinearLayout layout = (LinearLayout) view;
        layout.removeAllViews();
        return layout;
    }

    public void generateDialog(LinearLayout layout) {
    }

    public void processVisibility() {
    }

    private void scheduleChange(int updateDelay) {
        class PostRunnable implements Runnable {
            public void run() {
                if (L.isEnabled(L.CORE))
                    log.debug("Firing EventPreferenceChange");
                RxBus.INSTANCE.send(new EventPreferenceChange(preferenceId));
                RxBus.INSTANCE.send(new EventSWUpdate(false));
                scheduledEventPost = null;
            }
        }
        // cancel waiting task to prevent sending multiple posts
        if (scheduledEventPost != null)
            scheduledEventPost.cancel(false);
        Runnable task = new PostRunnable();
        final int sec = updateDelay;
        scheduledEventPost = eventWorker.schedule(task, sec, TimeUnit.SECONDS);
    }
}
