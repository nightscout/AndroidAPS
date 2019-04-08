package info.nightscout.androidaps.plugins.general.overview.dialogs;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

/**
 * Created by adrian on 09/02/17.
 */

public class BolusProgressHelperActivity extends AppCompatActivity {
    public BolusProgressHelperActivity() {
        super();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            this.getIntent().getDoubleExtra("insulin", 0d);
            BolusProgressDialog bolusProgressDialog = new BolusProgressDialog();
            bolusProgressDialog.setHelperActivity(this);
            bolusProgressDialog.setInsulin(this.getIntent().getDoubleExtra("insulin", 0d));
            bolusProgressDialog.show(this.getSupportFragmentManager(), "BolusProgress");
    }
}
