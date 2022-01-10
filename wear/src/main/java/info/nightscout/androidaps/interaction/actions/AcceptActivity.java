package info.nightscout.androidaps.interaction.actions;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.os.Vibrator;
import android.support.wearable.view.GridPagerAdapter;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.view.InputDeviceCompat;
import androidx.core.view.MotionEventCompat;
import androidx.core.view.ViewConfigurationCompat;

import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.ListenerService;

/**
 * Created by adrian on 09/02/17.
 */

public class AcceptActivity extends ViewSelectorActivity {

    String message = "";
    String actionstring = "";
    private DismissThread dismissThread;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        this.dismissThread = new DismissThread();
        dismissThread.start();

        Bundle extras = getIntent().getExtras();
        message = extras.getString("message", "");
        actionstring = extras.getString("actionstring", "");

        if ("".equals(message) || "".equals(actionstring)) {
            finish();
            return;
        }

        setAdapter(new MyGridViewPagerAdapter());

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

            if (col == 0) {
                final View view = LayoutInflater.from(getApplicationContext()).inflate(R.layout.action_confirm_text, container, false);
                final TextView textView = view.findViewById(R.id.message);
                final View scrollView = view.findViewById(R.id.message_scroll);
                textView.setText(message);
                container.addView(view);
                scrollView.setOnGenericMotionListener(new View.OnGenericMotionListener() {
                    @Override
                    public boolean onGenericMotion(View v, MotionEvent ev) {
                        if (ev.getAction() == MotionEvent.ACTION_SCROLL &&
                                ev.isFromSource(InputDeviceCompat.SOURCE_ROTARY_ENCODER)
                        ) {
                            float delta = -ev.getAxisValue(MotionEventCompat.AXIS_SCROLL) *
                                    ViewConfigurationCompat.getScaledVerticalScrollFactor(
                                            ViewConfiguration.get(container.getContext()),
                                            container.getContext());
                            v.scrollBy(0, Math.round(delta));

                            return true;
                        }
                        return false;
                    }
                });

                scrollView.requestFocus();
                return view;
            } else {
                final View view = LayoutInflater.from(getApplicationContext()).inflate(R.layout.action_send_item, container, false);
                final ImageView confirmbutton = view.findViewById(R.id.confirmbutton);
                confirmbutton.setOnClickListener((View v) -> {
                    ListenerService.confirmAction(AcceptActivity.this, actionstring);
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

    @Override
    public synchronized void onDestroy() {
        super.onDestroy();
        if (dismissThread != null) {
            dismissThread.invalidate();
        }

    }

    private class DismissThread extends Thread {
        private boolean valid = true;

        public synchronized void invalidate() {
            valid = false;
        }

        @Override
        public void run() {
            SystemClock.sleep(60 * 1000);
            synchronized (this) {
                if (valid) {
                    AcceptActivity.this.finish();
                }
            }
        }
    }

    @Override
    protected synchronized void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (dismissThread != null) dismissThread.invalidate();
        Bundle extras = intent.getExtras();
        Intent msgIntent = new Intent(this, AcceptActivity.class);
        msgIntent.putExtras(extras);
        startActivity(msgIntent);
        finish();
    }
}
