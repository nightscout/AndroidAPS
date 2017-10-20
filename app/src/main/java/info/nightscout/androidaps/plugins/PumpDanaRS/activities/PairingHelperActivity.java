package info.nightscout.androidaps.plugins.PumpDanaRS.activities;

import android.support.v7.app.AppCompatActivity;
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
