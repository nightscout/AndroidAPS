package info.nightscout.androidaps.interaction.actions;


import android.app.Activity;
import android.content.res.Resources;
import android.os.Bundle;
import android.support.wearable.view.DotsPageIndicator;
import android.support.wearable.view.GridPagerAdapter;
import android.support.wearable.view.GridViewPager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import java.text.DecimalFormat;

import info.nightscout.androidaps.data.ListenerService;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.interaction.utils.PlusMinusEditText;
import info.nightscout.androidaps.interaction.utils.SafeParse;

/**
 * Created by adrian on 09/02/17.
 */


public class WizardActivity extends ViewSelectorActivity {

    PlusMinusEditText editCarbs;

    boolean useBG;
    boolean includeBolusIOB;
    boolean includeBasalIOB;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.grid_layout);
        final Resources res = getResources();
        final GridViewPager pager = (GridViewPager) findViewById(R.id.pager);

        pager.setAdapter(new MyGridViewPagerAdapter());
        DotsPageIndicator dotsPageIndicator = (DotsPageIndicator) findViewById(R.id.page_indicator);
        dotsPageIndicator.setPager(pager);
    }


    @Override
    protected void onPause() {
        super.onPause();
        finish();
    }


    private class MyGridViewPagerAdapter extends GridPagerAdapter {
        @Override
        public int getColumnCount(int arg0) {
            return 5;
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
                setLabelToPlusMinusView(view, "carbs");
                container.addView(view);
                return view;
            } else if(col == 1){
                final View view = LayoutInflater.from(getApplicationContext()).inflate(R.layout.action_toggle_item, container, false);
                final TextView textView = (TextView) view.findViewById(R.id.label);
                textView.setText("include BG?");

                final ImageView togglebutton = (ImageView) view.findViewById(R.id.togglebutton);
                if(useBG){
                    togglebutton.setImageResource(R.drawable.ic_toggle_on);
                } else {
                    togglebutton.setImageResource(R.drawable.ic_toggle_off);
                }
                togglebutton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        useBG = !useBG;
                        if(useBG){
                            togglebutton.setImageResource(R.drawable.ic_toggle_on);
                        } else {
                            togglebutton.setImageResource(R.drawable.ic_toggle_off);
                        }
                    }
                });
                container.addView(view);
                return view;
            } else if(col == 2){
                final View view = LayoutInflater.from(getApplicationContext()).inflate(R.layout.action_toggle_item, container, false);
                final TextView textView = (TextView) view.findViewById(R.id.label);
                textView.setText("Bolus IOB?");

                final ImageView togglebutton = (ImageView) view.findViewById(R.id.togglebutton);
                if(includeBolusIOB){
                    togglebutton.setImageResource(R.drawable.ic_toggle_on);
                } else {
                    togglebutton.setImageResource(R.drawable.ic_toggle_off);
                }
                togglebutton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        includeBolusIOB = !includeBolusIOB;
                        if(includeBolusIOB){
                            togglebutton.setImageResource(R.drawable.ic_toggle_on);
                        } else {
                            togglebutton.setImageResource(R.drawable.ic_toggle_off);
                        }
                    }
                });
                container.addView(view);
                return view;
            } else if(col == 3){
                final View view = LayoutInflater.from(getApplicationContext()).inflate(R.layout.action_toggle_item, container, false);
                final TextView textView = (TextView) view.findViewById(R.id.label);
                textView.setText("Basal IOB?");

                final ImageView togglebutton = (ImageView) view.findViewById(R.id.togglebutton);
                if(includeBasalIOB){
                    togglebutton.setImageResource(R.drawable.ic_toggle_on);
                } else {
                    togglebutton.setImageResource(R.drawable.ic_toggle_off);
                }
                togglebutton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        includeBasalIOB = !includeBasalIOB;
                        if(includeBasalIOB){
                            togglebutton.setImageResource(R.drawable.ic_toggle_on);
                        } else {
                            togglebutton.setImageResource(R.drawable.ic_toggle_off);
                        }
                    }
                });
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

                        String actionstring = "wizard " + SafeParse.stringToInt(editCarbs.editText.getText().toString())
                                + " " + useBG
                                + " " + includeBolusIOB
                                + " " + includeBasalIOB;
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