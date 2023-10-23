package info.nightscout.androidaps.plugins.pump.insight.activities;

import android.app.KeyguardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import javax.inject.Inject;

import app.aaps.core.utils.HtmlHelper;
import dagger.android.support.DaggerAppCompatActivity;
import info.nightscout.androidaps.insight.R;
import info.nightscout.androidaps.plugins.pump.insight.InsightAlertService;
import info.nightscout.androidaps.plugins.pump.insight.descriptors.Alert;
import info.nightscout.androidaps.plugins.pump.insight.descriptors.AlertStatus;
import info.nightscout.androidaps.plugins.pump.insight.utils.AlertUtils;

public class InsightAlertActivity extends DaggerAppCompatActivity {

    @Inject AlertUtils alertUtils;

    private InsightAlertService alertService;

    private ImageView icon;
    private TextView errorCode;
    private TextView errorTitle;
    private TextView errorDescription;
    private Button mute;
    private Button confirm;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
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

        setShowWhenLocked(true);
        setTurnScreenOn(true);
        KeyguardManager keyguardManager = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        keyguardManager.requestDismissKeyguard(this, null);
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
        this.icon.setImageDrawable(ContextCompat.getDrawable(this, alertUtils.getAlertIcon(alert.getAlertCategory())));
        this.errorCode.setText(alertUtils.getAlertCode(alert.getAlertType()));
        this.errorTitle.setText(alertUtils.getAlertTitle(alert.getAlertType()));
        String description = alertUtils.getAlertDescription(alert);
        if (description == null) this.errorDescription.setVisibility(View.GONE);
        else {
            this.errorDescription.setVisibility(View.VISIBLE);
            this.errorDescription.setText(HtmlHelper.INSTANCE.fromHtml(description));
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
