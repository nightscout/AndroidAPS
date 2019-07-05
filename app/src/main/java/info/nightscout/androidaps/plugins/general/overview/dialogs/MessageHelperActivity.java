package info.nightscout.androidaps.plugins.general.overview.dialogs;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

public class MessageHelperActivity extends AppCompatActivity {

    public MessageHelperActivity() {
        super();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        MessageDialog messageDialog = new MessageDialog();
        messageDialog.setHelperActivity(this);
        messageDialog.setStatus(getIntent().getStringExtra("status"));

        messageDialog.setTitle(getIntent().getStringExtra("title"));
        messageDialog.show(this.getSupportFragmentManager(), "Message");
    }
}
