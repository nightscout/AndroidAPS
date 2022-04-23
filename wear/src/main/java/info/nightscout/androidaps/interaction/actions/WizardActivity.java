package info.nightscout.androidaps.interaction.actions;

import android.os.Bundle;
import android.support.wearable.view.GridPagerAdapter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import java.text.DecimalFormat;

import info.nightscout.androidaps.R;
import info.nightscout.androidaps.events.EventWearToMobile;
import info.nightscout.androidaps.interaction.utils.PlusMinusEditText;
import info.nightscout.shared.SafeParse;
import info.nightscout.shared.weardata.EventData;

/**
 * Created by adrian on 09/02/17.
 */

public class WizardActivity extends ViewSelectorActivity {

    PlusMinusEditText editCarbs;
    PlusMinusEditText editPercentage;

    boolean hasPercentage;
    int percentage;
    int maxCarbs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setAdapter(new MyGridViewPagerAdapter());
        hasPercentage = sp.getBoolean("wizardpercentage", false);
        percentage = sp.getInt(getString(R.string.key_bolus_wizard_percentage), 100);
        maxCarbs = sp.getInt(getString(R.string.key_treatments_safety_max_carbs), 48);
    }

    @Override
    protected void onPause() {
        super.onPause();
        finish();
    }

    @SuppressWarnings("deprecation")
    private class MyGridViewPagerAdapter extends GridPagerAdapter {
        @Override
        public int getColumnCount(int arg0) {
            return hasPercentage ? 3 : 2;
        }

        @Override
        public int getRowCount() {
            return 1;
        }

        @Override
        public Object instantiateItem(ViewGroup container, int row, int col) {

            if (col == 0) {
                final View view = getInflatedPlusMinusView(container);
                if (editCarbs == null) {
                    editCarbs = new PlusMinusEditText(view, R.id.amountfield, R.id.plusbutton, R.id.minusbutton, 0d, 0d, (double)maxCarbs, 1d, new DecimalFormat("0"), false);
                } else {
                    double def = SafeParse.stringToDouble(editCarbs.editText.getText().toString());
                    editCarbs = new PlusMinusEditText(view, R.id.amountfield, R.id.plusbutton, R.id.minusbutton, def, 0d, (double)maxCarbs, 1d, new DecimalFormat("0"),false);
                }
                setLabelToPlusMinusView(view, getString(R.string.action_carbs));
                container.addView(view);
                view.requestFocus();
                return view;
            } else if (col == 1 && hasPercentage) {
                final View view = getInflatedPlusMinusView(container);
                if (editPercentage == null) {
                    editPercentage = new PlusMinusEditText(view, R.id.amountfield, R.id.plusbutton, R.id.minusbutton, (double)percentage, 50d, 150d, 1d, new DecimalFormat("0"), false);
                } else {
                    double def = SafeParse.stringToDouble(editPercentage.editText.getText().toString());
                    editPercentage = new PlusMinusEditText(view, R.id.amountfield, R.id.plusbutton, R.id.minusbutton, def, 50d, 150d, 1d, new DecimalFormat("0"), false);
                }
                setLabelToPlusMinusView(view, getString(R.string.action_percentage));
                container.addView(view);
                return view;
            } else {

                final View view = LayoutInflater.from(getApplicationContext()).inflate(R.layout.action_send_item, container, false);
                final ImageView confirmButton = view.findViewById(R.id.confirmbutton);
                confirmButton.setOnClickListener((View v) -> {
                    if (editPercentage != null) {
                        percentage = SafeParse.stringToInt(editPercentage.editText.getText().toString());
                    }

                    EventData.ActionWizardPreCheck action = new EventData.ActionWizardPreCheck(
                            SafeParse.stringToInt(editCarbs.editText.getText().toString()),
                            percentage
                    );
                    rxBus.send(new EventWearToMobile(action));
                    showToast(WizardActivity.this, R.string.action_wizard_confirmation);
                    finishAffinity();
                });
                container.addView(view);
                return view;
            }
        }

        @Override
        public void destroyItem(ViewGroup container, int row, int col, Object view) {
            // Handle this to get the data before the view is destroyed?
            // Object should still be kept by this, just setup for re-init?
            container.removeView((View) view);
        }

        @Override
        public boolean isViewFromObject(View view, Object object) {
            return view == object;
        }

    }
}
