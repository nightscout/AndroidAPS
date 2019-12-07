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

import info.nightscout.androidaps.R;
import info.nightscout.androidaps.aaps;
import info.nightscout.androidaps.data.ListenerService;
import info.nightscout.androidaps.interaction.utils.PlusMinusEditText;
import info.nightscout.androidaps.interaction.utils.SafeParse;

import static android.preference.PreferenceManager.getDefaultSharedPreferences;

/**
 * Created by adrian on 09/02/17.
 */


public class TempTargetActivity extends ViewSelectorActivity {

    PlusMinusEditText lowRange;
    PlusMinusEditText highRange;
    PlusMinusEditText time;
    boolean isMGDL;
    boolean isSingleTarget;

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
        isMGDL = sp.getBoolean("units_mgdl", true);
        isSingleTarget =  sp.getBoolean("singletarget", true);
    }


    @Override
    protected void onPause() {
        super.onPause();
        finish();
    }


    private class MyGridViewPagerAdapter extends GridPagerAdapter {
        @Override
        public int getColumnCount(int arg0) {
            return isSingleTarget?3:4;
        }

        @Override
        public int getRowCount() {
            return 1;
        }

        @Override
        public Object instantiateItem(ViewGroup container, int row, int col) {

             if(col == 0){
                final View view = getInflatedPlusMinusView(container);
                final TextView textView = (TextView) view.findViewById(R.id.label);
                textView.setText("duration");
                if (time == null) {
                    time = new PlusMinusEditText(view, R.id.amountfield, R.id.plusbutton, R.id.minusbutton, 60d, 0d, 24 * 60d, 5d, new DecimalFormat("0"), false);
                } else {
                    double def = SafeParse.stringToDouble(time.editText.getText().toString());
                    time = new PlusMinusEditText(view, R.id.amountfield, R.id.plusbutton, R.id.minusbutton, def, 0d, 24 * 60d, 5d, new DecimalFormat("0"), false);
                }
                 setLabelToPlusMinusView(view, aaps.gs(R.string.action_duration));
                 container.addView(view);
                return view;

            } else if(col == 1){
                 final View view = getInflatedPlusMinusView(container);
                 if (isMGDL){
                     double def = 100;
                     if (lowRange != null){
                         def = SafeParse.stringToDouble(lowRange.editText.getText().toString());
                     }
                     lowRange = new PlusMinusEditText(view, R.id.amountfield, R.id.plusbutton, R.id.minusbutton, def, 72d, 180d, 1d, new DecimalFormat("0"), false);
                 } else {
                     double def = 5.5;
                     if (lowRange != null){
                         def = SafeParse.stringToDouble(lowRange.editText.getText().toString());
                     }
                     lowRange = new PlusMinusEditText(view, R.id.amountfield, R.id.plusbutton, R.id.minusbutton, def, 4d, 10d, 0.1d, new DecimalFormat("#0.0"), false);
                 }
                 if(isSingleTarget){
                     setLabelToPlusMinusView(view, aaps.gs(R.string.action_target));
                 } else {
                     setLabelToPlusMinusView(view, aaps.gs(R.string.action_low));
                 }
                 container.addView(view);
                 return view;
             } else if(col == 2 && ! isSingleTarget){
                 final View view = getInflatedPlusMinusView(container);
                 if (isMGDL){
                     double def = 100;
                     if (highRange != null){
                         def = SafeParse.stringToDouble(highRange.editText.getText().toString());
                     }
                     highRange = new PlusMinusEditText(view, R.id.amountfield, R.id.plusbutton, R.id.minusbutton, def, 72d, 180d, 1d, new DecimalFormat("0"), false);
                 } else {
                     double def = 5.5;
                     if (highRange != null){
                         def = SafeParse.stringToDouble(highRange.editText.getText().toString());
                     }
                     highRange = new PlusMinusEditText(view, R.id.amountfield, R.id.plusbutton, R.id.minusbutton, def, 4d, 10d, 0.1d, new DecimalFormat("#0.0"), false);
                 }
                 setLabelToPlusMinusView(view, aaps.gs(R.string.action_high));
                 container.addView(view);
                 return view;
             }else {

                final View view = LayoutInflater.from(getApplicationContext()).inflate(R.layout.action_send_item, container, false);
                final ImageView confirmbutton = (ImageView) view.findViewById(R.id.confirmbutton);
                confirmbutton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {

                        //check if it can happen that the fagment is never created that hold data?
                        // (you have to swipe past them anyways - but still)

                        String actionstring = "temptarget "
                                + " " + isMGDL
                                + " " + SafeParse.stringToInt(time.editText.getText().toString())
                                + " " + SafeParse.stringToDouble(lowRange.editText.getText().toString())
                                + " " + (isSingleTarget?SafeParse.stringToDouble(lowRange.editText.getText().toString()):SafeParse.stringToDouble(highRange.editText.getText().toString()))
                                ;

                        ListenerService.initiateAction(TempTargetActivity.this, actionstring);
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