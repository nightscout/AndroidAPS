package info.nightscout.androidaps.interaction.actions;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.wearable.view.DotsPageIndicator;
import android.support.wearable.view.GridPagerAdapter;
import android.support.wearable.view.GridViewPager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.wear.widget.CurvedTextView;

import info.nightscout.androidaps.R;

/**
 * Created by adrian on 13/02/17.
 */

public class ViewSelectorActivity extends Activity {

    private GridViewPager pager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.grid_layout);

        setTitleBasedOnScreenShape();

        pager = findViewById(R.id.pager);
        DotsPageIndicator dotsPageIndicator = findViewById(R.id.page_indicator);
        dotsPageIndicator.setPager(pager);
        pager.setOnPageChangeListener(new GridViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int row, int column, float rowOffset, float columnOffset, int rowOffsetPixels, int columnOffsetPixels) {
                dotsPageIndicator.onPageScrolled(row, column, rowOffset, columnOffset, rowOffsetPixels,
                        columnOffsetPixels);
            }

            @Override
            public void onPageSelected(int row, int column) {
                dotsPageIndicator.onPageSelected(row, column);
                View view = pager.getChildAt(column);
                view.requestFocus();
            }

            @Override
            public void onPageScrollStateChanged(int state) {
                dotsPageIndicator.onPageScrollStateChanged(state);
            }
        });
    }

    public void setAdapter(GridPagerAdapter adapter) {
        pager.setAdapter(adapter);
    }

    private void setTitleBasedOnScreenShape() {
        // intents can inject dynamic titles, otherwise we'll use the default
        String title = String.valueOf(getTitle());
        if (getIntent().getExtras() != null) {
            title = getIntent().getExtras().getString("title", title);
        }
        CurvedTextView titleViewCurved = findViewById(R.id.title_curved);
        TextView titleView = findViewById(R.id.title);
        if (this.getResources().getConfiguration().isScreenRound()) {
            titleViewCurved.setText(title);
            titleViewCurved.setVisibility(View.VISIBLE);
            titleView.setVisibility((View.GONE));
        } else {
            titleView.setText(title);
            titleView.setVisibility(View.VISIBLE);
            titleViewCurved.setVisibility((View.GONE));
        }
    }

    View getInflatedPlusMinusView(ViewGroup container) {
        SharedPreferences sharedPrefs = PreferenceManager
                .getDefaultSharedPreferences(this);
        int design = Integer.parseInt(sharedPrefs.getString("input_design", "1"));

        if (design == 2) {
            return LayoutInflater.from(getApplicationContext()).inflate(R.layout.action_editplusminus_item_quickrighty, container, false);
        } else if (design == 3) {
            return LayoutInflater.from(getApplicationContext()).inflate(R.layout.action_editplusminus_item_quicklefty, container, false);
        } else if (design == 4) {
            return LayoutInflater.from(getApplicationContext()).inflate(R.layout.action_editplusminus_item_viktoria, container, false);
        }

        //default
        return LayoutInflater.from(getApplicationContext()).inflate(R.layout.action_editplusminus_item, container, false);
    }

    void setLabelToPlusMinusView(View view, String labelText) {
        final TextView textView = view.findViewById(R.id.label);
        textView.setText(labelText);
    }

    void confirmAction(Context context, int text)  {
        Toast.makeText(context, getString(text), Toast.LENGTH_LONG).show();
    }

}
