package info.nightscout.androidaps.interaction.actions;


import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.SystemClock;
import android.os.Vibrator;
import androidx.core.app.NotificationManagerCompat;
import android.support.wearable.view.DotsPageIndicator;
import android.support.wearable.view.GridPagerAdapter;
import android.support.wearable.view.GridViewPager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.ListenerService;

/**
 * Created by adrian on 09/02/17.
 */


public class AcceptActivity extends ViewSelectorActivity {


    String title = "";
    String message = "";
    String actionstring = "";
    private DismissThread dismissThread;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        this.dismissThread = new DismissThread();
        dismissThread.start();

        Bundle extras = getIntent().getExtras();
        title = extras.getString("title", "");
        message = extras.getString("message", "");
        actionstring = extras.getString("actionstring", "");

        if ("".equals(message) || "".equals(actionstring) ){
            finish(); return;
        }

        setContentView(R.layout.grid_layout);
        final Resources res = getResources();
        final GridViewPager pager = (GridViewPager) findViewById(R.id.pager);

        pager.setAdapter(new MyGridViewPagerAdapter());
        DotsPageIndicator dotsPageIndicator = (DotsPageIndicator) findViewById(R.id.page_indicator);
        dotsPageIndicator.setPager(pager);

        Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        long[] vibratePattern = new long[]{0, 100, 50, 100, 50};
        v.vibrate(vibratePattern, -1);
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

            if(col == 0){
                final View view = LayoutInflater.from(getApplicationContext()).inflate(R.layout.action_confirm_text, container, false);
                final TextView headingView = (TextView) view.findViewById(R.id.title);
                headingView.setText(title);
                final TextView textView = (TextView) view.findViewById(R.id.message);
                textView.setText(message);
                container.addView(view);
                return view;
            } else {
                final View view = LayoutInflater.from(getApplicationContext()).inflate(R.layout.action_send_item, container, false);
                final ImageView confirmbutton = (ImageView) view.findViewById(R.id.confirmbutton);
                confirmbutton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        ListenerService.confirmAction(AcceptActivity.this, actionstring);
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

    @Override
    public synchronized void onDestroy(){
        super.onDestroy();
        if(dismissThread != null){
            dismissThread.invalidate();
        }

    }

    private class DismissThread extends Thread{
        private boolean valid = true;

        public synchronized void invalidate(){
            valid = false;
        }

        @Override
        public void run() {
            SystemClock.sleep(60 * 1000);
            synchronized (this) {
                if(valid) {
                    AcceptActivity.this.finish();
                }
            }
        }
    }

    @Override
    protected synchronized void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if(dismissThread != null) dismissThread.invalidate();
        Bundle extras = intent.getExtras();
        Intent msgIntent = new Intent(this, AcceptActivity.class);
        msgIntent.putExtras(extras);
        startActivity(msgIntent);
        finish();
    }
}