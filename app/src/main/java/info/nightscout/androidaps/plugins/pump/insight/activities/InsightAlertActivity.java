package info.nightscout.androidaps.plugins.pump.insight.activities;

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.text.Html;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import info.nightscout.androidaps.R;
import info.nightscout.androidaps.activities.NoSplashAppCompatActivity;
import info.nightscout.androidaps.plugins.pump.insight.InsightAlertService;
import info.nightscout.androidaps.plugins.pump.insight.descriptors.Alert;
import info.nightscout.androidaps.plugins.pump.insight.descriptors.AlertStatus;
import info.nightscout.androidaps.plugins.pump.insight.utils.AlertUtilsKt;

public class InsightAlertActivity extends AppCompatActivity {

    private InsightAlertService alertService;

    private ImageView icon;
    private TextView errorCode;
    private TextView errorTitle;
    private TextView errorDescription;
    private Button mute;
    private Button confirm;

    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            alertService = ((InsightAlertService.LocalBinder) binder).getService();
            alertService.getAlertLiveData().observe(InsightAlertActivity.this, alert -> {
                if (alert == null) finish();
                else update(alert);
            });
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            alertService = null;
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_insight_alert);

        bindService(new Intent(this, InsightAlertService.class), serviceConnection, BIND_AUTO_CREATE);

        icon = findViewById(R.id.icon);
        errorCode = findViewById(R.id.error_code);
        errorTitle = findViewById(R.id.error_title);
        errorDescription = findViewById(R.id.error_description);
        mute = findViewById(R.id.mute);
        confirm = findViewById(R.id.confirm);

        setFinishOnTouchOutside(false);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
    }

    @Override
    protected void onDestroy() {
        unbindService(serviceConnection);
        super.onDestroy();
    }

    public void update(Alert alert) {
        mute.setEnabled(true);
        mute.setVisibility(alert.getAlertStatus() == AlertStatus.SNOOZED ? View.GONE : View.VISIBLE);
        confirm.setEnabled(true);
        this.icon.setImageDrawable(ContextCompat.getDrawable(this, AlertUtilsKt.getAlertIcon(alert.getAlertCategory())));
        this.errorCode.setText(AlertUtilsKt.getAlertCode(alert.getAlertType()));
        this.errorTitle.setText(AlertUtilsKt.getAlertTitle(alert.getAlertType()));
        String description = AlertUtilsKt.getAlertDescription(alert);
        if (description == null) this.errorDescription.setVisibility(View.GONE);
        else {
            this.errorDescription.setVisibility(View.VISIBLE);
            this.errorDescription.setText(Html.fromHtml(description));
        }
    }

    public void muteClicked(View view) {
        mute.setEnabled(false);
        alertService.mute();
    }

    public void confirmClicked(View view) {
        mute.setEnabled(false);
        confirm.setEnabled(false);
        alertService.confirm();
    }
}
