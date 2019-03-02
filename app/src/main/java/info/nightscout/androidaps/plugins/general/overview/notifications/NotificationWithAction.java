package info.nightscout.androidaps.plugins.general.overview.notifications;

public class NotificationWithAction extends Notification {

    Runnable action;
    String buttonText;

    public NotificationWithAction(int id, String text, int level) {
        super(id, text, level);
    }

    public void action(String buttonText, Runnable action) {
        this.buttonText = buttonText;
        this.action = action;
    }
}
