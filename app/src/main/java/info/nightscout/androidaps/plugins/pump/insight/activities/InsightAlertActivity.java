package info.nightscout.androidaps.plugins.pump.insight.activities;

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.text.Html;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import java.text.DecimalFormat;

import info.nightscout.androidaps.R;
import info.nightscout.androidaps.plugins.pump.insight.InsightAlertService;
import info.nightscout.androidaps.plugins.pump.insight.descriptors.Alert;
import info.nightscout.androidaps.plugins.pump.insight.descriptors.AlertStatus;

public class InsightAlertActivity extends AppCompatActivity {

    private Alert alert;
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
            alertService.setAlertActivity(InsightAlertActivity.this);
            alert = alertService.getAlert();
            if (alert == null) finish();
            update(alert);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            alertService = null;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
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
        WindowManager.LayoutParams layoutParams = getWindow().getAttributes();
        layoutParams.screenBrightness = 1.0F;
        getWindow().setAttributes(layoutParams);
    }

    @Override
    protected void onDestroy() {
        alertService.setAlertActivity(null);
        unbindService(serviceConnection);
        super.onDestroy();
    }

    public void update(Alert alert) {
        this.alert = alert;
        mute.setEnabled(true);
        mute.setVisibility(alert.getAlertStatus() == AlertStatus.SNOOZED ? View.GONE : View.VISIBLE);
        confirm.setEnabled(true);
        int icon = 0;
        int code = 0;
        int title = 0;
        String description = null;
        switch (alert.getAlertCategory()) {
            case ERROR:
                icon = R.drawable.ic_error;
                break;
            case MAINTENANCE:
                icon = R.drawable.ic_maintenance;
                break;
            case WARNING:
                icon = R.drawable.ic_warning;
                break;
            case REMINDER:
                icon = R.drawable.ic_reminder;
                break;
        }
        DecimalFormat decimalFormat = new DecimalFormat("##0.00");
        int hours = alert.getTBRDuration() / 60;
        int minutes = alert.getTBRDuration() - hours * 60;
        switch (alert.getAlertType()) {
            case REMINDER_01:
                code = R.string.alert_r1_code;
                title = R.string.alert_r1_title;
                break;
            case REMINDER_02:
                code = R.string.alert_r2_code;
                title = R.string.alert_r2_title;
                break;
            case REMINDER_03:
                code = R.string.alert_r3_code;
                title = R.string.alert_r3_title;
                break;
            case REMINDER_04:
                code = R.string.alert_r4_code;
                title = R.string.alert_r4_title;
                break;
            case REMINDER_07:
                code = R.string.alert_r7_code;
                title = R.string.alert_r7_title;
                description = getString(R.string.alert_r7_description, alert.getTBRAmount(), new DecimalFormat("#0").format(hours) + ":" + new DecimalFormat("00").format(minutes));
                break;
            case WARNING_31:
                code = R.string.alert_w31_code;
                title = R.string.alert_w31_title;
                description = getString(R.string.alert_w31_description, decimalFormat.format(alert.getCartridgeAmount()));
                break;
            case WARNING_32:
                code = R.string.alert_w32_code;
                title = R.string.alert_w32_title;
                description = getString(R.string.alert_w32_description);
                break;
            case WARNING_33:
                code = R.string.alert_w33_code;
                title = R.string.alert_w33_title;
                description = getString(R.string.alert_w33_description);
                break;
            case WARNING_34:
                code = R.string.alert_w34_code;
                title = R.string.alert_w34_title;
                description = getString(R.string.alert_w34_description);
                break;
            case WARNING_36:
                code = R.string.alert_w36_code;
                title = R.string.alert_w36_title;
                description = getString(R.string.alert_w36_description, alert.getTBRAmount(), new DecimalFormat("#0").format(hours) + ":" + new DecimalFormat("00").format(minutes));
                break;
            case WARNING_38:
                code = R.string.alert_w38_code;
                title = R.string.alert_w38_title;
                description = getString(R.string.alert_w38_description, decimalFormat.format(alert.getProgrammedBolusAmount()), decimalFormat.format(alert.getDeliveredBolusAmount()));
                break;
            case WARNING_39:
                code = R.string.alert_w39_code;
                title = R.string.alert_w39_title;
                break;
            case MAINTENANCE_20:
                code = R.string.alert_m20_code;
                title = R.string.alert_m20_title;
                description = getString(R.string.alert_m20_description);
                break;
            case MAINTENANCE_21:
                code = R.string.alert_m21_code;
                title = R.string.alert_m21_title;
                description = getString(R.string.alert_m21_description);
                break;
            case MAINTENANCE_22:
                code = R.string.alert_m22_code;
                title = R.string.alert_m22_title;
                description = getString(R.string.alert_m22_description);
                break;
            case MAINTENANCE_23:
                code = R.string.alert_m23_code;
                title = R.string.alert_m23_title;
                description = getString(R.string.alert_m23_description);
                break;
            case MAINTENANCE_24:
                code = R.string.alert_m24_code;
                title = R.string.alert_m24_title;
                description = getString(R.string.alert_m24_description);
                break;
            case MAINTENANCE_25:
                code = R.string.alert_m25_code;
                title = R.string.alert_m25_title;
                description = getString(R.string.alert_m25_description);
                break;
            case MAINTENANCE_26:
                code = R.string.alert_m26_code;
                title = R.string.alert_m26_title;
                description = getString(R.string.alert_m26_description);
                break;
            case MAINTENANCE_27:
                code = R.string.alert_m27_code;
                title = R.string.alert_m27_title;
                description = getString(R.string.alert_m27_description);
                break;
            case MAINTENANCE_28:
                code = R.string.alert_m28_code;
                title = R.string.alert_m28_title;
                description = getString(R.string.alert_m28_description);
                break;
            case MAINTENANCE_29:
                code = R.string.alert_m29_code;
                title = R.string.alert_m29_title;
                description = getString(R.string.alert_m29_description);
                break;
            case MAINTENANCE_30:
                code = R.string.alert_m30_code;
                title = R.string.alert_m30_title;
                description = getString(R.string.alert_m30_description);
                break;
            case ERROR_6:
                code = R.string.alert_e6_code;
                title = R.string.alert_e6_title;
                description = getString(R.string.alert_e6_description);
                break;
            case ERROR_10:
                code = R.string.alert_e10_code;
                title = R.string.alert_e10_title;
                description = getString(R.string.alert_e10_description);
                break;
            case ERROR_13:
                code = R.string.alert_e13_code;
                title = R.string.alert_e13_title;
                description = getString(R.string.alert_e13_description);
                break;
        }
        this.icon.setImageDrawable(ContextCompat.getDrawable(this, icon));
        this.errorCode.setText(code);
        this.errorTitle.setText(title);
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
