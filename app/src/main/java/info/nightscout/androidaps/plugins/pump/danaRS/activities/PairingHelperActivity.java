package info.nightscout.androidaps.plugins.pump.danaRS.activities;

import android.os.Bundle;

import info.nightscout.androidaps.activities.NoSplashAppCompatActivity;

public class PairingHelperActivity extends NoSplashAppCompatActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        PairingProgressDialog bolusProgressDialog = new PairingProgressDialog();
        bolusProgressDialog.setHelperActivity(this);
        bolusProgressDialog.show(this.getSupportFragmentManager(), "PairingProgress");
    }
}
