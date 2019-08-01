package info.nightscout.androidaps.plugins.general.overview.dialogs;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

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
            BolusProgressDialog bolusProgressDialog = new BolusProgressDialog();
            bolusProgressDialog.setHelperActivity(this);
            bolusProgressDialog.setInsulin(getIntent().getDoubleExtra("insulin", 0d));
            bolusProgressDialog.show(getSupportFragmentManager(), "BolusProgress");
    }
}
