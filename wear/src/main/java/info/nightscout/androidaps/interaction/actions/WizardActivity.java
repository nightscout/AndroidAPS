package info.nightscout.androidaps.interaction.actions;


import android.app.Activity;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.wearable.view.DotsPageIndicator;
import android.support.wearable.view.GridPagerAdapter;
import android.support.wearable.view.GridViewPager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import java.text.DecimalFormat;

import info.nightscout.androidaps.aaps;
import info.nightscout.androidaps.data.ListenerService;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.interaction.utils.PlusMinusEditText;
import info.nightscout.androidaps.interaction.utils.SafeParse;

/**
 * Created by adrian on 09/02/17.
 */


public class WizardActivity extends ViewSelectorActivity {

    PlusMinusEditText editCarbs;
    PlusMinusEditText editPercentage;

    boolean hasPercentage;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.grid_layout);
        final Resources res = getResources();
        final GridViewPager pager = (GridViewPager) findViewById(R.id.pager);

        pager.setAdapter(new MyGridViewPagerAdapter());
        DotsPageIndicator dotsPageIndicator = (DotsPageIndicator) findViewById(R.id.page_indicator);
        dotsPageIndicator.setPager(pager);
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        hasPercentage =  sp.getBoolean("wizardpercentage", false);
    }


    @Override
    protected void onPause() {
        super.onPause();
        finish();
    }


    private class MyGridViewPagerAdapter extends GridPagerAdapter {
        @Override
        public int getColumnCount(int arg0) {
            return hasPercentage?3:2;
        }

        @Override
        public int getRowCount() {
            return 1;
        }

        @Override
        public Object instantiateItem(ViewGroup container, int row, int col) {

            if(col == 0){
                final View view = getInflatedPlusMinusView(container);
                if (editCarbs == null) {
                    editCarbs = new PlusMinusEditText(view, R.id.amountfield, R.id.plusbutton, R.id.minusbutton, 0d, 0d, 150d, 1d, new DecimalFormat("0"), false);
                } else {
                    double def = SafeParse.stringToDouble(editCarbs.editText.getText().toString());
                    editCarbs = new PlusMinusEditText(view, R.id.amountfield, R.id.plusbutton, R.id.minusbutton, def, 0d, 150d, 1d, new DecimalFormat("0"), false);

                }
                setLabelToPlusMinusView(view, aaps.gs(R.string.action_carbs));
                container.addView(view);
                return view;
            } else if(col == 1 && hasPercentage){
                final View view = getInflatedPlusMinusView(container);
                if (editPercentage == null) {
                    editPercentage = new PlusMinusEditText(view, R.id.amountfield, R.id.plusbutton, R.id.minusbutton, 100d, 50d, 150d, 1d, new DecimalFormat("0"), false);
                } else {
                    double def = SafeParse.stringToDouble(editPercentage.editText.getText().toString());
                    editPercentage = new PlusMinusEditText(view, R.id.amountfield, R.id.plusbutton, R.id.minusbutton, def, 50d, 150d, 1d, new DecimalFormat("0"), false);
                }
                setLabelToPlusMinusView(view, aaps.gs(R.string.action_percentage));
                container.addView(view);
                return view;
            } else {

                final View view = LayoutInflater.from(getApplicationContext()).inflate(R.layout.action_send_item, container, false);
                final ImageView confirmbutton = (ImageView) view.findViewById(R.id.confirmbutton);
                confirmbutton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {

                        //check if it can happen that the fagment is never created that hold data?
                        // (you have to swipe past them anyways - but still)

                        int percentage = 100;

                        if (editPercentage != null) percentage = SafeParse.stringToInt(editPercentage.editText.getText().toString());

                        String actionstring = "wizard2 " + SafeParse.stringToInt(editCarbs.editText.getText().toString())
                                + " " + percentage;
                        ListenerService.initiateAction(WizardActivity.this, actionstring);
                        finish();
                    }
                });
                container.addView(view);
                return view;
            }
        }

        @Override
        public void destroyItem(ViewGroup container, int row, int col, Object view) {
            // Handle this to get the data before the view is destroyed?
            // Object should still be kept by this, just setup for reinit?
            container.removeView((View)view);
        }

        @Override
        public boolean isViewFromObject(View view, Object object) {
            return view==object;
        }


    }
}