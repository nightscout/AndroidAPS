package info.nightscout.androidaps.plugins.pump.insight;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Binder;
import android.os.IBinder;
import android.os.Vibrator;
import android.text.Html;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.lifecycle.MutableLiveData;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.pump.insight.activities.InsightAlertActivity;
import info.nightscout.androidaps.plugins.pump.insight.app_layer.remote_control.ConfirmAlertMessage;
import info.nightscout.androidaps.plugins.pump.insight.app_layer.remote_control.SnoozeAlertMessage;
import info.nightscout.androidaps.plugins.pump.insight.app_layer.status.GetActiveAlertMessage;
import info.nightscout.androidaps.plugins.pump.insight.connection_service.InsightConnectionService;
import info.nightscout.androidaps.plugins.pump.insight.descriptors.Alert;
import info.nightscout.androidaps.plugins.pump.insight.descriptors.AlertStatus;
import info.nightscout.androidaps.plugins.pump.insight.descriptors.AlertType;
import info.nightscout.androidaps.plugins.pump.insight.descriptors.InsightState;
import info.nightscout.androidaps.plugins.pump.insight.exceptions.InsightException;
import info.nightscout.androidaps.plugins.pump.insight.exceptions.app_layer_errors.AppLayerErrorException;
import info.nightscout.androidaps.plugins.pump.insight.utils.AlertUtilsKt;
import info.nightscout.androidaps.plugins.pump.insight.utils.ExceptionTranslator;

public class InsightAlertService extends Service implements InsightConnectionService.StateCallback {

    private static final int NOTIFICATION_ID = 31345;

    private static Logger log = LoggerFactory.getLogger(L.PUMPCOMM);

    private LocalBinder localBinder = new LocalBinder();
    private boolean connectionRequested;
    private final Object $alertLock = new Object[0];
    private Alert alert = null;
    private MutableLiveData<Alert> alertLiveData = new MutableLiveData<>();
    private Thread thread;
    private Vibrator vibrator;
    private boolean vibrating;
    private InsightConnectionService connectionService;
    private long ignoreTimestamp;
    private AlertType ignoreType;

    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            connectionService = ((InsightConnectionService.LocalBinder) binder).getService();
            connectionService.registerStateCallback(InsightAlertService.this);
            onStateChanged(connectionService.getState());
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            connectionService = null;
        }
    };

    public void ignore(AlertType alertType) {
        synchronized ($alertLock) {
            if (alertType == null) {
                ignoreTimestamp = 0;
                ignoreType = null;
            } else {
                ignoreTimestamp = System.currentTimeMillis();
                ignoreType = alertType;
            }
        }
    }

    public MutableLiveData<Alert> getAlertLiveData() {
        return alertLiveData;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return localBinder;
    }

    @Override
    public void onCreate() {
        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        bindService(new Intent(this, InsightConnectionService.class), serviceConnection, BIND_AUTO_CREATE);
        alertLiveData.setValue(null);
    }

    @Override
    public void onDestroy() {
        if (thread != null) thread.interrupt();
        unbindService(serviceConnection);
    }

    @Override
    public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
        if (intent == null) {
            // service is being restarted
        } else if ("mute".equals(intent.getStringExtra("command"))) {
            mute();
        } else if ("confirm".equals(intent.getStringExtra("command"))) {
            dismissNotification();
            confirm();
        }
        return START_STICKY;
    }

    @Override
    public void onStateChanged(InsightState state) {
        if (state == InsightState.CONNECTED) {
            thread = new Thread(this::queryActiveAlert);
            thread.start();

        } else {
            dismissNotification();
            if (thread != null) thread.interrupt();
        }
    }

    private void queryActiveAlert() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                synchronized ($alertLock) {
                    Alert alert = connectionService.requestMessage(new GetActiveAlertMessage()).await().getAlert();
                    if (alert == null || (alert.getAlertType() == ignoreType && System.currentTimeMillis() - ignoreTimestamp < 10000)) {
                        if (connectionRequested) {
                            connectionService.withdrawConnectionRequest(this);
                            connectionRequested = false;
                        }
                        this.alertLiveData.postValue(null);
                        this.alert = null;
                        dismissNotification();
                        stopAlerting();
                    } else if (!alert.equals(this.alert)) {
                        if (!connectionRequested) {
                            connectionService.requestConnection(this);
                            connectionRequested = true;
                        }
                        showNotification(alert);
                        this.alertLiveData.postValue(alert);
                        this.alert = alert;
                        if (alert.getAlertStatus() == AlertStatus.SNOOZED) stopAlerting();
                        else alert();
                    }
                    /*if ((this.alert == null && alert != null)
                            || (this.alert != null && alert == null)
                            || (this.alert != null && alert != null && !this.alert.equals(alert))) {
                        if (this.alert != null && (alert == null || this.alert.getAlertId() != alert.getAlertId())) stopAlerting();
                        this.alert = alert;
                        if (alert != null)
                            new Handler(Looper.getMainLooper()).post(() -> {
                                //showNotification(alert);
                                //alertActivity.update(alert);
                            });
                    }
                    if (alert == null) {
                        stopAlerting();
                        if (connectionRequested) {
                            connectionService.withdrawConnectionRequest(this);
                            connectionRequested = false;
                        }
                        new Handler(Looper.getMainLooper()).post(() -> {
                            //dismissNotification();
                            //alertActivity.finish();
                        });
                    } else if (!(alert.getAlertType() == ignoreType && System.currentTimeMillis() - ignoreTimestamp < 10000))  {
                        if (alert.getAlertStatus() == AlertStatus.ACTIVE) alert();
                        else stopAlerting();
                        if (!connectionRequested) {
                            connectionService.requestConnection(this);
                            connectionRequested = true;
                        }
                        /*if (alertActivity == null) {
                            Intent intent = new Intent(InsightAlertService.this, InsightAlertActivity.class);
                            intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                            new Handler(Looper.getMainLooper()).post(() -> startActivity(intent));
                        }*/
                    //}
                }
            } catch (InterruptedException ignored) {
                connectionService.withdrawConnectionRequest(thread);
                break;
            } catch (AppLayerErrorException e) {
                log.info("Exception while fetching alert: " + e.getClass().getCanonicalName() + " (" + e.getErrorCode() + ")");
            } catch (InsightException e) {
                log.info("Exception while fetching alert: " + e.getClass().getSimpleName());
            } catch (Exception e) {
                log.error("Exception while fetching alert", e);
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                break;
            }
        }
        if (connectionRequested) {
            connectionService.withdrawConnectionRequest(thread);
            connectionRequested = false;
        }
        stopAlerting();
        alertLiveData.postValue(null);
        this.alert = null;
        dismissNotification();
        thread = null;
    }

    private void alert() {
        if (!vibrating) {
            vibrator.vibrate(new long[]{0, 1000, 1000}, 0);
            vibrating = true;
        }
    }

    private void stopAlerting() {
        if (vibrating) {
            vibrator.cancel();
            vibrating = false;
        }
    }

    public void mute() {
        new Thread(() -> {
            try {
                synchronized ($alertLock) {
                    if (alert == null) return;
                    alert.setAlertStatus(AlertStatus.SNOOZED);
                    alertLiveData.postValue(alert);
                    stopAlerting();
                    showNotification(alert);
                    SnoozeAlertMessage snoozeAlertMessage = new SnoozeAlertMessage();
                    snoozeAlertMessage.setAlertID(alert.getAlertId());
                    connectionService.requestMessage(snoozeAlertMessage).await();
                }
            } catch (AppLayerErrorException e) {
                log.info("Exception while muting alert: " + e.getClass().getCanonicalName() + " (" + e.getErrorCode() + ")");
                ExceptionTranslator.makeToast(InsightAlertService.this, e);
            } catch (InsightException e) {
                log.info("Exception while muting alert: " + e.getClass().getSimpleName());
                ExceptionTranslator.makeToast(InsightAlertService.this, e);
            } catch (Exception e) {
                log.error("Exception while muting alert", e);
                ExceptionTranslator.makeToast(InsightAlertService.this, e);
            }
        }).start();
    }

    public void confirm() {
        new Thread(() -> {
            try {
                synchronized ($alertLock) {
                    if (alert == null) return;
                    stopAlerting();
                    alertLiveData.postValue(null);
                    dismissNotification();
                    ConfirmAlertMessage confirmAlertMessage = new ConfirmAlertMessage();
                    confirmAlertMessage.setAlertID(alert.getAlertId());
                    connectionService.requestMessage(confirmAlertMessage).await();
                    this.alert = null;
                }
            } catch (AppLayerErrorException e) {
                log.info("Exception while confirming alert: " + e.getClass().getCanonicalName() + " (" + e.getErrorCode() + ")");
                ExceptionTranslator.makeToast(InsightAlertService.this, e);
            } catch (InsightException e) {
                log.info("Exception while confirming alert: " + e.getClass().getSimpleName());
                ExceptionTranslator.makeToast(InsightAlertService.this, e);
            } catch (Exception e) {
                log.error("Exception while confirming alert", e);
                ExceptionTranslator.makeToast(InsightAlertService.this, e);
            }
        }).start();
    }

    private void showNotification(Alert alert) {
        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this, LocalInsightPlugin.ALERT_CHANNEL_ID);

        notificationBuilder.setPriority(NotificationCompat.PRIORITY_MAX);
        notificationBuilder.setCategory(NotificationCompat.CATEGORY_ALARM);
        notificationBuilder.setVibrate(new long[0]);
        notificationBuilder.setShowWhen(false);
        notificationBuilder.setOngoing(true);
        notificationBuilder.setOnlyAlertOnce(true);
        notificationBuilder.setAutoCancel(false);
        notificationBuilder.setSmallIcon(AlertUtilsKt.getAlertIcon(alert.getAlertCategory()));

        notificationBuilder.setContentTitle(AlertUtilsKt.getAlertCode(alert.getAlertType()) + " – " + AlertUtilsKt.getAlertTitle(alert.getAlertType()));
        String description = AlertUtilsKt.getAlertDescription(alert);
        if (description != null)
            notificationBuilder.setContentText(Html.fromHtml(description).toString());

        Intent fullScreenIntent = new Intent(this, InsightAlertActivity.class);
        PendingIntent fullScreenPendingIntent = PendingIntent.getActivity(this, 0, fullScreenIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        notificationBuilder.setFullScreenIntent(fullScreenPendingIntent, true);

        switch (alert.getAlertStatus()) {
            case ACTIVE:
                Intent muteIntent = new Intent(this, InsightAlertService.class).putExtra("command", "mute");
                PendingIntent mutePendingIntent = PendingIntent.getService(this, 1, muteIntent, PendingIntent.FLAG_UPDATE_CURRENT);
                notificationBuilder.addAction(0, MainApp.gs(R.string.mute_alert), mutePendingIntent);
            case SNOOZED:
                Intent confirmIntent = new Intent(this, InsightAlertService.class).putExtra("command", "confirm");
                PendingIntent confirmPendingIntent = PendingIntent.getService(this, 2, confirmIntent, PendingIntent.FLAG_UPDATE_CURRENT);
                notificationBuilder.addAction(0, MainApp.gs(R.string.confirm), confirmPendingIntent);
        }

        Notification notification = notificationBuilder.build();
        NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification);
        startForeground(NOTIFICATION_ID, notification);
    }

    private void dismissNotification() {
        NotificationManagerCompat.from(this).cancel(NOTIFICATION_ID);
        stopForeground(true);
    }

    public class LocalBinder extends Binder {
        public InsightAlertService getService() {
            return InsightAlertService.this;
        }
    }
}
