package info.nightscout.androidaps.plugins.Overview.Dialogs;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

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
    }
}
