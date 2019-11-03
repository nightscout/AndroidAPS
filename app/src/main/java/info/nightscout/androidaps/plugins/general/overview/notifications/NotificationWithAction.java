package info.nightscout.androidaps.plugins.general.overview.notifications;

import androidx.annotation.IntegerRes;

public class NotificationWithAction extends Notification {

    Runnable action;
    int buttonText;

    public NotificationWithAction(int id, String text, int level) {
        super(id, text, level);
    }

    public void action(int buttonText, Runnable action) {
        this.buttonText = buttonText;
        this.action = action;
    }
}
