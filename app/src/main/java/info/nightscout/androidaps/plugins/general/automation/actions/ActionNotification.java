package info.nightscout.androidaps.plugins.general.automation.actions;

import android.widget.LinearLayout;

import com.google.common.base.Optional;

import org.json.JSONException;
import org.json.JSONObject;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.events.EventRefreshOverview;
import info.nightscout.androidaps.plugins.bus.RxBus;
import info.nightscout.androidaps.plugins.general.automation.elements.InputString;
import info.nightscout.androidaps.plugins.general.automation.elements.LabelWithElement;
import info.nightscout.androidaps.plugins.general.automation.elements.LayoutBuilder;
import info.nightscout.androidaps.plugins.general.nsclient.NSUpload;
import info.nightscout.androidaps.plugins.general.overview.events.EventNewNotification;
import info.nightscout.androidaps.plugins.general.overview.notifications.Notification;
import info.nightscout.androidaps.queue.Callback;
import info.nightscout.androidaps.utils.JsonHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ActionNotification extends Action {
    private static final Logger log = LoggerFactory.getLogger(ActionNotification.class);

    public InputString text = new InputString();

    @Override
    public int friendlyName() {
        return R.string.notification;
    }

    @Override
    public String shortDescription() {
        return MainApp.gs(R.string.notification_message, text.getValue());
    }

    @Override
    public void doAction(Callback callback) {
        Notification notification = new Notification(Notification.USERMESSAGE, text.getValue(), Notification.URGENT);
        RxBus.INSTANCE.send(new EventNewNotification(notification));
        NSUpload.uploadError(text.getValue());
        RxBus.INSTANCE.send(new EventRefreshOverview("ActionNotification"));
        if (callback != null)
            callback.result(new PumpEnactResult().success(true).comment(R.string.ok)).run();

    }

    @Override
    public Optional<Integer> icon() {
        return Optional.of(R.drawable.ic_notifications);
    }

    @Override
    public String toJSON() {
        JSONObject o = new JSONObject();
        JSONObject data = new JSONObject();
        try {
            data.put("text", text.getValue());
            o.put("type", this.getClass().getName());
            o.put("data", data);
        } catch (JSONException e) {
            log.error("Unhandled exception", e);
        }
        return o.toString();
    }

    @Override
    public Action fromJSON(String data) {
        try {
            JSONObject o = new JSONObject(data);
            text.setValue(JsonHelper.safeGetString(o, "text"));
        } catch (JSONException e) {
            log.error("Unhandled exception", e);
        }
        return this;
    }

    @Override
    public boolean hasDialog() {
        return true;
    }

    @Override
    public void generateDialog(LinearLayout root) {

        new LayoutBuilder()
                .add(new LabelWithElement(MainApp.gs(R.string.message_short), "", text))
                .build(root);
    }

}
