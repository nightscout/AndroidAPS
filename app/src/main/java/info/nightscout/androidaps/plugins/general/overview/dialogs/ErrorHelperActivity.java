package info.nightscout.androidaps.plugins.general.overview.dialogs;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

import info.nightscout.androidaps.R;
import info.nightscout.androidaps.plugins.general.nsclient.NSUpload;
import info.nightscout.androidaps.utils.SP;

public class ErrorHelperActivity extends AppCompatActivity {
    public ErrorHelperActivity() {
        super();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ErrorDialog errorDialog = new ErrorDialog();
        errorDialog.setHelperActivity(this);
        errorDialog.setStatus(getIntent().getStringExtra("status"));
        errorDialog.setSound(getIntent().getIntExtra("soundid", 0));
        errorDialog.setTitle(getIntent().getStringExtra("title"));
        errorDialog.show(this.getSupportFragmentManager(), "Error");

        if (SP.getBoolean(R.string.key_ns_create_announcements_from_errors, true)) {
            NSUpload.uploadError(getIntent().getStringExtra("status"));
        }
    }
}
