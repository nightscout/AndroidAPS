package info.nightscout.androidaps.plugins.pump.danaRS.activities;

import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;

public class PairingHelperActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        PairingProgressDialog bolusProgressDialog = new PairingProgressDialog();
        bolusProgressDialog.setHelperActivity(this);
        bolusProgressDialog.show(this.getSupportFragmentManager(), "PairingProgress");
    }
}
