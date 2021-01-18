package info.nightscout.androidaps.interaction.actions;


import android.content.res.Resources;
import android.os.Bundle;
import android.support.wearable.view.DotsPageIndicator;
import android.support.wearable.view.GridPagerAdapter;
import android.support.wearable.view.GridViewPager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import java.text.DecimalFormat;

import info.nightscout.androidaps.R;
import info.nightscout.androidaps.aaps;
import info.nightscout.androidaps.data.ListenerService;
import info.nightscout.androidaps.interaction.utils.PlusMinusEditText;
import info.nightscout.androidaps.interaction.utils.SafeParse;

/**
 * Created by adrian on 09/02/17.
 */


public class CPPActivity extends ViewSelectorActivity {

    PlusMinusEditText editPercentage;
    PlusMinusEditText editTimeshift;

    int percentage = -1;
    int timeshift = -25;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle extras = getIntent().getExtras();
        percentage = extras.getInt("percentage", -1);
        timeshift = extras.getInt("timeshift", -1);

        if (percentage ==-1 || timeshift ==-25){
            finish(); return;
        }

        if(timeshift < 0) timeshift += 24;

        setContentView(R.layout.grid_layout);
        final Resources res = getResources();
        final GridViewPager pager = findViewById(R.id.pager);

        pager.setAdapter(new MyGridViewPagerAdapter());
        DotsPageIndicator dotsPageIndicator = findViewById(R.id.page_indicator);
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
            return 3;
        }

        @Override
        public int getRowCount() {
            return 1;
        }

        @Override
        public Object instantiateItem(ViewGroup container, int row, int col) {

            if(col == 0){
                final View view = getInflatedPlusMinusView(container);
                double def = timeshift;
                if (editTimeshift != null){
                    def = SafeParse.stringToDouble(editTimeshift.editText.getText().toString());
                }
                editTimeshift = new PlusMinusEditText(view, R.id.amountfield, R.id.plusbutton, R.id.minusbutton, def, 0d, 23d, 1d, new DecimalFormat("0"), true, true);
                setLabelToPlusMinusView(view, aaps.gs(R.string.action_timeshift));
                container.addView(view);
                return view;
            } else if(col == 1){
                final View view = getInflatedPlusMinusView(container);
                double def = percentage;
                if (editPercentage != null){
                    def = SafeParse.stringToDouble(editPercentage.editText.getText().toString());
                }
                editPercentage = new PlusMinusEditText(view, R.id.amountfield, R.id.plusbutton, R.id.minusbutton, def, 30d, 250d, 1d, new DecimalFormat("0"), false);
                setLabelToPlusMinusView(view, aaps.gs(R.string.action_percentage));
                container.addView(view);
                return view;
            } else {

                final View view = LayoutInflater.from(getApplicationContext()).inflate(R.layout.action_send_item, container, false);
                final ImageView confirmbutton = view.findViewById(R.id.confirmbutton);
                confirmbutton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {

                        //check if it can happen that the fagment is never created that hold data?
                        // (you have to swipe past them anyways - but still)

                        String actionstring = "cppset " +SafeParse.stringToInt(editTimeshift.editText.getText().toString())
                                + " " + SafeParse.stringToInt(editPercentage.editText.getText().toString());
                        ListenerService.initiateAction(CPPActivity.this, actionstring);
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
