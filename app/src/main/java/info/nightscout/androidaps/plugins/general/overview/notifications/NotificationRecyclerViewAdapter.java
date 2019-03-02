package info.nightscout.androidaps.plugins.general.overview.notifications;

import android.support.v4.content.ContextCompat;
import android.support.v7.widget.CardView;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.general.nsclient.broadcasts.BroadcastAckAlarm;
import info.nightscout.androidaps.plugins.general.overview.OverviewPlugin;
import info.nightscout.androidaps.plugins.general.overview.events.EventDismissNotification;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.SP;

public class NotificationRecyclerViewAdapter extends RecyclerView.Adapter<NotificationRecyclerViewAdapter.NotificationsViewHolder> {
    private static Logger log = LoggerFactory.getLogger(L.NOTIFICATION);

    private List<Notification> notificationsList;

    public NotificationRecyclerViewAdapter(List<Notification> notificationsList) {
        this.notificationsList = notificationsList;
    }

    @Override
    public NotificationsViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
        View v = LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.overview_notification_item, viewGroup, false);
        return new NotificationsViewHolder(v);
    }

    @Override
    public void onBindViewHolder(NotificationsViewHolder holder, int position) {
        Notification notification = notificationsList.get(position);
        holder.dismiss.setTag(notification);
        if (notification instanceof NotificationWithAction)
            holder.dismiss.setText(((NotificationWithAction) notification).buttonText);
        else if (Objects.equals(notification.text, MainApp.gs(R.string.nsalarm_staledata)))
            holder.dismiss.setText("snooze");

        holder.text.setText(notification.text + '\n');
        holder.time.setText(DateUtil.timeString(notification.date));
        if (notification.level == Notification.URGENT)
            holder.cv.setBackgroundColor(ContextCompat.getColor(MainApp.instance(), R.color.notificationUrgent));
        else if (notification.level == Notification.NORMAL)
            holder.cv.setBackgroundColor(ContextCompat.getColor(MainApp.instance(), R.color.notificationNormal));
        else if (notification.level == Notification.LOW)
            holder.cv.setBackgroundColor(ContextCompat.getColor(MainApp.instance(), R.color.notificationLow));
        else if (notification.level == Notification.INFO)
            holder.cv.setBackgroundColor(ContextCompat.getColor(MainApp.instance(), R.color.notificationInfo));
        else if (notification.level == Notification.ANNOUNCEMENT)
            holder.cv.setBackgroundColor(ContextCompat.getColor(MainApp.instance(), R.color.notificationAnnouncement));
    }

    @Override
    public int getItemCount() {
        return notificationsList.size();
    }

    @Override
    public void onAttachedToRecyclerView(RecyclerView recyclerView) {
        super.onAttachedToRecyclerView(recyclerView);
    }

    static class NotificationsViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
        CardView cv;
        TextView time;
        TextView text;
        Button dismiss;

        NotificationsViewHolder(View itemView) {
            super(itemView);
            cv = (CardView) itemView.findViewById(R.id.notification_cardview);
            time = (TextView) itemView.findViewById(R.id.notification_time);
            text = (TextView) itemView.findViewById(R.id.notification_text);
            dismiss = (Button) itemView.findViewById(R.id.notification_dismiss);
            dismiss.setOnClickListener(this);
        }

        @Override
        public void onClick(View v) {
            Notification notification = (Notification) v.getTag();
            switch (v.getId()) {
                case R.id.notification_dismiss:
                    MainApp.bus().post(new EventDismissNotification(notification.id));
                    if (notification.nsAlarm != null) {
                        BroadcastAckAlarm.handleClearAlarm(notification.nsAlarm, MainApp.instance().getApplicationContext(), 60 * 60 * 1000L);
                    }
                    // Adding current time to snooze if we got staleData
                    if (L.isEnabled(L.NOTIFICATION))
                        log.debug("Notification text is: " + notification.text);
                    if (notification.text.equals(MainApp.gs(R.string.nsalarm_staledata))) {
                        NotificationStore nstore = OverviewPlugin.getPlugin().notificationStore;
                        long msToSnooze = SP.getInt("nsalarm_staledatavalue", 15) * 60 * 1000L;
                        if (L.isEnabled(L.NOTIFICATION))
                            log.debug("snooze nsalarm_staledatavalue in minutes is " + SP.getInt("nsalarm_staledatavalue", 15) + "\n in ms is: " + msToSnooze + " currentTimeMillis is: " + System.currentTimeMillis());
                        nstore.snoozeTo(System.currentTimeMillis() + (SP.getInt("nsalarm_staledatavalue", 15) * 60 * 1000L));
                    }
                    if (notification instanceof NotificationWithAction) {
                        ((NotificationWithAction) notification).action.run();
                    }
                    break;
            }
        }
    }
}
