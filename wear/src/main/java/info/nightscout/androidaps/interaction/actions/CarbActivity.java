package info.nightscout.androidaps.interaction.actions;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.wearable.view.GridPagerAdapter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import java.text.DecimalFormat;

import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.ListenerService;
import info.nightscout.androidaps.interaction.utils.PlusMinusEditText;
import info.nightscout.shared.SafeParse;

public class CarbActivity extends ViewSelectorActivity {

    PlusMinusEditText editCarbs;
    int maxCarbs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setAdapter(new MyGridViewPagerAdapter());
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        maxCarbs = sp.getInt(getString(R.string.key_treatmentssafety_maxcarbs), 48);
    }

    @Override
    protected void onPause() {
        super.onPause();
        finish();
    }

    private class MyGridViewPagerAdapter extends GridPagerAdapter {
        @Override
        public int getColumnCount(int arg0) {
            return 2;
        }

        @Override
        public int getRowCount() {
            return 1;
        }

        @Override
        public Object instantiateItem(ViewGroup container, int row, int col) {

            if (col == 0) {
                final View view = getInflatedPlusMinusView(container);
                double def = 0;
                if (editCarbs != null) {
                    def = SafeParse.stringToDouble(editCarbs.editText.getText().toString());
                }
                editCarbs = new PlusMinusEditText(view, R.id.amountfield, R.id.plusbutton, R.id.minusbutton, def, 0d, (double)maxCarbs, 1d, new DecimalFormat("0"), true);
                setLabelToPlusMinusView(view, getString(R.string.action_carbs));
                container.addView(view);
                view.requestFocus();
                return view;
            } else {
                final View view = LayoutInflater.from(getApplicationContext()).inflate(R.layout.action_send_item, container, false);
                final ImageView confirmbutton = view.findViewById(R.id.confirmbutton);
                confirmbutton.setOnClickListener((View v) -> {
                    // With start time 0 and duration 0
                    String actionstring = "ecarbs " + SafeParse.stringToInt(editCarbs.editText.getText().toString()) + " 0 0";
                    ListenerService.initiateAction(CarbActivity.this, actionstring);
                    confirmAction(CarbActivity.this, R.string.action_ecarb_confirmation);
                    finishAffinity();

                });
                container.addView(view);
                return view;
            }
        }

        @Override
        public void destroyItem(ViewGroup container, int row, int col, Object view) {
            // Handle this to get the data before the view is destroyed?
            // Object should still be kept by this, just setup for reinit?
            container.removeView((View) view);
        }

        @Override
        public boolean isViewFromObject(View view, Object object) {
            return view == object;
        }

    }
}
