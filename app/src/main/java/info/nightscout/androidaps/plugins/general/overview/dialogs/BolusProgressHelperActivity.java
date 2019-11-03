package info.nightscout.androidaps.plugins.general.overview.dialogs;

import android.os.Bundle;

import info.nightscout.androidaps.activities.NoSplashAppCompatActivity;

public class BolusProgressHelperActivity extends NoSplashAppCompatActivity {
    public BolusProgressHelperActivity() {
        super();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        BolusProgressDialog bolusProgressDialog = new BolusProgressDialog();
        bolusProgressDialog.setHelperActivity(this);
        bolusProgressDialog.setInsulin(getIntent().getDoubleExtra("insulin", 0d));
        bolusProgressDialog.show(getSupportFragmentManager(), "BolusProgress");
    }
}
