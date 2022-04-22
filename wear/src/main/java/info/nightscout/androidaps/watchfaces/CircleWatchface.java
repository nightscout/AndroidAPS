package info.nightscout.androidaps.watchfaces;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.RectF;
import android.os.PowerManager;
import android.support.wearable.watchface.WatchFaceStyle;
import android.util.TypedValue;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import com.ustwo.clockwise.common.WatchFaceTime;
import com.ustwo.clockwise.wearable.WatchFace;

import java.util.ArrayList;
import java.util.Calendar;

import javax.inject.Inject;

import dagger.android.AndroidInjection;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.events.EventWearToMobile;
import info.nightscout.androidaps.interaction.menus.MainMenuActivity;
import info.nightscout.androidaps.plugins.bus.RxBus;
import info.nightscout.androidaps.utils.rx.AapsSchedulers;
import info.nightscout.shared.logging.AAPSLogger;
import info.nightscout.shared.logging.LTag;
import info.nightscout.shared.sharedPreferences.SP;
import info.nightscout.shared.weardata.EventData;
import io.reactivex.rxjava3.disposables.CompositeDisposable;


public class CircleWatchface extends WatchFace {

    @Inject RxBus rxBus;
    @Inject AapsSchedulers aapsSchedulers;
    @Inject AAPSLogger aapsLogger;
    @Inject SP sp;

    CompositeDisposable disposable = new CompositeDisposable();

    private EventData.SingleBg singleBg = new EventData.SingleBg(0, "---", "-", "--", "--", "--", 0, 0.0, 0.0, 0.0, 0);
    private EventData.GraphData graphData;
    private EventData.Status status = new EventData.Status("no status", "IOB", "-.--", false, "--g", "-.--U/h", "--", "--", -1, "--", false, 1);

    public final float PADDING = 20f;
    public final float CIRCLE_WIDTH = 10f;
    public final int BIG_HAND_WIDTH = 16;
    public final int SMALL_HAND_WIDTH = 8;
    public final int NEAR = 2; //how near do the hands have endTime be endTime activate overlapping mode
    public final boolean ALWAYS_HIGHLIGT_SMALL = false;

    //variables for time
    private float angleBig = 0f;
    private float angleSMALL = 0f;
    private int color;
    private final Paint circlePaint = new Paint();
    private final Paint removePaint = new Paint();
    private RectF rect, rectDelete;
    private boolean overlapping;

    public Point displaySize = new Point();

    public ArrayList<EventData.SingleBg> bgDataList = new ArrayList<>();

    private int specW;
    private int specH;
    private View myLayout;

    private TextView mSgv;
    private long sgvTapTime = 0;


    @SuppressLint("InflateParams") @Override
    public void onCreate() {
        AndroidInjection.inject(this);
        super.onCreate();

        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        PowerManager.WakeLock wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AndroidAPS:CircleWatchface");
        wakeLock.acquire(30000);

        Display display = ((WindowManager) getSystemService(Context.WINDOW_SERVICE))
                .getDefaultDisplay();
        display.getSize(displaySize);

        specW = View.MeasureSpec.makeMeasureSpec(displaySize.x,
                View.MeasureSpec.EXACTLY);
        specH = View.MeasureSpec.makeMeasureSpec(displaySize.y,
                View.MeasureSpec.EXACTLY);

        //register Message Receiver

        LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        myLayout = inflater.inflate(R.layout.modern_layout, null);
        prepareLayout();
        prepareDrawTime();

        disposable.add(rxBus
                .toObservable(EventData.SingleBg.class)
                .observeOn(aapsSchedulers.getMain())
                .subscribe(event -> singleBg = event)
        );
        disposable.add(rxBus
                .toObservable(EventData.GraphData.class)
                .observeOn(aapsSchedulers.getMain())
                .subscribe(event -> graphData = event)
        );
        disposable.add(rxBus
                .toObservable(EventData.Status.class)
                .observeOn(aapsSchedulers.getMain())
                .subscribe(event -> {
                    // this event is received as last batch of data
                    aapsLogger.debug(LTag.WEAR, "Status received");
                    status = event;
                    addToWatchSet();
                    prepareLayout();
                    prepareDrawTime();
                    invalidate();
                })
        );
        disposable.add(rxBus
                .toObservable(EventData.Preferences.class)
                .observeOn(aapsSchedulers.getMain())
                .subscribe(event -> {
                    prepareDrawTime();
                    prepareLayout();
                    invalidate();
                })
        );
        rxBus.send(new EventWearToMobile(new EventData.ActionResendData("CircleWatchFace::onCreate")));

        wakeLock.release();
    }


    @Override
    public void onDestroy() {
        disposable.clear();
        super.onDestroy();
    }

    @Override
    protected synchronized void onDraw(Canvas canvas) {
        aapsLogger.debug(LTag.WEAR, "start onDraw");
        canvas.drawColor(getBackgroundColor());
        drawTime(canvas);
        drawOtherStuff(canvas);
        myLayout.draw(canvas);

    }

    private synchronized void prepareLayout() {

        aapsLogger.debug(LTag.WEAR, "start startPrepareLayout");

        // prepare fields

        mSgv = myLayout.findViewById(R.id.sgvString);
        if (sp.getBoolean("showBG", true)) {
            mSgv.setVisibility(View.VISIBLE);
            mSgv.setText(singleBg.getSgvString());
            mSgv.setTextColor(getTextColor());

        } else {
            //Also possible: View.INVISIBLE instead of View.GONE (no layout change)
            mSgv.setVisibility(View.INVISIBLE);
        }

        TextView textView = myLayout.findViewById(R.id.statusString);
        if (sp.getBoolean("showExternalStatus", true)) {
            textView.setVisibility(View.VISIBLE);
            textView.setText(status.getExternalStatus());
            textView.setTextColor(getTextColor());

        } else {
            //Also possible: View.INVISIBLE instead of View.GONE (no layout change)
            textView.setVisibility(View.GONE);
        }

        textView = myLayout.findViewById(R.id.agoString);
        if (sp.getBoolean("showAgo", true)) {
            textView.setVisibility(View.VISIBLE);

            if (sp.getBoolean("showBigNumbers", false)) {
                textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
            } else {
                ((TextView) myLayout.findViewById(R.id.agoString)).setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
            }
            textView.setText(getMinutes());
            textView.setTextColor(getTextColor());
        } else {
            //Also possible: View.INVISIBLE instead of View.GONE (no layout change)
            textView.setVisibility(View.INVISIBLE);
        }

        textView = myLayout.findViewById(R.id.deltaString);
        if (sp.getBoolean("showDelta", true)) {
            textView.setVisibility(View.VISIBLE);
            textView.setText(singleBg.getDelta());
            textView.setTextColor(getTextColor());
            if (sp.getBoolean("showBigNumbers", false)) {
                textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 25);
            } else {
                textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
            }
            if (sp.getBoolean("showAvgDelta", true)) {
                textView.append("  " + singleBg.getAvgDelta());
            }

        } else {
            //Also possible: View.INVISIBLE instead of View.GONE (no layout change)
            textView.setVisibility(View.INVISIBLE);
        }

        myLayout.measure(specW, specH);
        myLayout.layout(0, 0, myLayout.getMeasuredWidth(),
                myLayout.getMeasuredHeight());
    }

    public String getMinutes() {
        String minutes = "--'";
        if (singleBg.getTimeStamp() != 0) {
            minutes = ((int) Math.floor((System.currentTimeMillis() - singleBg.getTimeStamp()) / 60000.0)) + "'";
        }
        return minutes;
    }

    private void drawTime(Canvas canvas) {

        //draw circle
        circlePaint.setColor(color);
        circlePaint.setStrokeWidth(CIRCLE_WIDTH);
        canvas.drawArc(rect, 0, 360, false, circlePaint);
        //"remove" hands from circle
        removePaint.setStrokeWidth(CIRCLE_WIDTH * 3);

        canvas.drawArc(rectDelete, angleBig, (float) BIG_HAND_WIDTH, false, removePaint);
        canvas.drawArc(rectDelete, angleSMALL, (float) SMALL_HAND_WIDTH, false, removePaint);


        if (overlapping) {
            //add small hand with extra
            circlePaint.setStrokeWidth(CIRCLE_WIDTH * 2);
            circlePaint.setColor(color);
            canvas.drawArc(rect, angleSMALL, (float) SMALL_HAND_WIDTH, false, circlePaint);

            //remove inner part of hands
            removePaint.setStrokeWidth(CIRCLE_WIDTH);
            canvas.drawArc(rect, angleBig, (float) BIG_HAND_WIDTH, false, removePaint);
            canvas.drawArc(rect, angleSMALL, (float) SMALL_HAND_WIDTH, false, removePaint);
        }

    }

    private synchronized void prepareDrawTime() {
        aapsLogger.debug(LTag.WEAR, "start prepareDrawTime");

        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY) % 12;
        int minute = Calendar.getInstance().get(Calendar.MINUTE);
        angleBig = (((hour + minute / 60f) / 12f * 360) - 90 - BIG_HAND_WIDTH / 2f + 360) % 360;
        angleSMALL = ((minute / 60f * 360) - 90 - SMALL_HAND_WIDTH / 2f + 360) % 360;


        color = 0;
        switch ((int) singleBg.getSgvLevel()) {
            case -1:
                color = getLowColor();
                break;
            case 0:
                color = getInRangeColor();
                break;
            case 1:
                color = getHighColor();
                break;
        }


        circlePaint.setShader(null);

        circlePaint.setStyle(Paint.Style.STROKE);
        circlePaint.setStrokeWidth(CIRCLE_WIDTH);
        circlePaint.setAntiAlias(true);
        circlePaint.setColor(color);

        removePaint.setStyle(Paint.Style.STROKE);
        removePaint.setStrokeWidth(CIRCLE_WIDTH);
        removePaint.setAntiAlias(true);
        removePaint.setColor(getBackgroundColor());

        rect = new RectF(PADDING, PADDING, displaySize.x - PADDING, displaySize.y - PADDING);
        rectDelete = new RectF(PADDING - CIRCLE_WIDTH / 2, PADDING - CIRCLE_WIDTH / 2, displaySize.x - PADDING + CIRCLE_WIDTH / 2, displaySize.y - PADDING + CIRCLE_WIDTH / 2);
        overlapping = ALWAYS_HIGHLIGT_SMALL || areOverlapping(angleSMALL, angleSMALL + SMALL_HAND_WIDTH + NEAR, angleBig, angleBig + BIG_HAND_WIDTH + NEAR);
        aapsLogger.debug(LTag.WEAR, "end prepareDrawTime");

    }

    private boolean areOverlapping(float aBegin, float aEnd, float bBegin, float bEnd) {
        return
                aBegin <= bBegin && aEnd >= bBegin ||
                        aBegin <= bBegin && (bEnd > 360) && bEnd % 360 > aBegin ||
                        bBegin <= aBegin && bEnd >= aBegin ||
                        bBegin <= aBegin && aEnd > 360 && aEnd % 360 > bBegin;
    }

    @Override
    protected void onTimeChanged(WatchFaceTime oldTime, WatchFaceTime newTime) {
        if (oldTime.hasMinuteChanged(newTime)) {
            PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
            PowerManager.WakeLock wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AndroidAPS:CircleWatchface_onTimeChanged");
            wakeLock.acquire(30000);
            /*Preparing the layout just on every minute tick:
             *  - hopefully better battery life
             *  - drawback: might update the minutes since last reading up endTime one minute late*/
            prepareLayout();
            prepareDrawTime();
            invalidate();  //redraw the time
            wakeLock.release();

        }
    }


    // defining color for dark and bright
    public int getLowColor() {
        if (sp.getBoolean("dark", true)) {
            return Color.argb(255, 255, 120, 120);
        } else {
            return Color.argb(255, 255, 80, 80);
        }
    }

    public int getInRangeColor() {
        if (sp.getBoolean("dark", true)) {
            return Color.argb(255, 120, 255, 120);
        } else {
            return Color.argb(255, 0, 240, 0);

        }
    }

    public int getHighColor() {
        if (sp.getBoolean("dark", true)) {
            return Color.argb(255, 255, 255, 120);
        } else {
            return Color.argb(255, 255, 200, 0);
        }

    }

    public int getBackgroundColor() {
        if (sp.getBoolean("dark", true)) {
            return Color.BLACK;
        } else {
            return Color.WHITE;

        }
    }

    public int getTextColor() {
        if (sp.getBoolean("dark", true)) {
            return Color.WHITE;
        } else {
            return Color.BLACK;

        }
    }

    public void drawOtherStuff(Canvas canvas) {
        aapsLogger.debug(LTag.WEAR, "start onDrawOtherStuff. bgDataList.size(): " + bgDataList.size());

        if (sp.getBoolean("showRingHistory", false)) {
            //Perfect low and High indicators
            if (bgDataList.size() > 0) {
                addIndicator(canvas, 100, Color.LTGRAY);
                addIndicator(canvas, (float) bgDataList.iterator().next().getLow(), getLowColor());
                addIndicator(canvas, (float) bgDataList.iterator().next().getHigh(), getHighColor());


                if (sp.getBoolean("softRingHistory", true)) {
                    for (EventData.SingleBg data : bgDataList) {
                        addReadingSoft(canvas, data);
                    }
                } else {
                    for (EventData.SingleBg data : bgDataList) {
                        addReading(canvas, data);
                    }
                }
            }
        }
    }

    public synchronized void addToWatchSet() {

        bgDataList.clear();
        if (!sp.getBoolean("showRingHistory", false)) return;

        double threshold = (System.currentTimeMillis() - (1000L * 60 * 30)); // 30 min
        for (EventData.SingleBg entry : graphData.getEntries())
            if (entry.getTimeStamp() >= threshold) bgDataList.add(entry);
        aapsLogger.debug(LTag.WEAR, "addToWatchSet size=" + bgDataList.size());
    }

    public int darken(int color, double fraction) {
        int red = Color.red(color);
        int green = Color.green(color);
        int blue = Color.blue(color);
        red = darkenColor(red, fraction);
        green = darkenColor(green, fraction);
        blue = darkenColor(blue, fraction);
        int alpha = Color.alpha(color);

        return Color.argb(alpha, red, green, blue);
    }

    private int darkenColor(int color, double fraction) {

        return (int) Math.max(color - (color * fraction), 0);
    }


    public void addArch(Canvas canvas, float offset, int color, float size) {
        Paint paint = new Paint();
        paint.setColor(color);
        RectF rectTemp = new RectF(PADDING + offset - CIRCLE_WIDTH / 2, PADDING + offset - CIRCLE_WIDTH / 2, (displaySize.x - PADDING - offset + CIRCLE_WIDTH / 2), (displaySize.y - PADDING - offset + CIRCLE_WIDTH / 2));
        canvas.drawArc(rectTemp, 270, size, true, paint);
    }

    public void addArch(Canvas canvas, float start, float offset, int color, float size) {
        Paint paint = new Paint();
        paint.setColor(color);
        RectF rectTemp = new RectF(PADDING + offset - CIRCLE_WIDTH / 2, PADDING + offset - CIRCLE_WIDTH / 2, (displaySize.x - PADDING - offset + CIRCLE_WIDTH / 2), (displaySize.y - PADDING - offset + CIRCLE_WIDTH / 2));
        canvas.drawArc(rectTemp, start + 270, size, true, paint);
    }

    public void addIndicator(Canvas canvas, float bg, int color) {
        float convertedBg;
        convertedBg = bgToAngle(bg);
        convertedBg += 270;
        Paint paint = new Paint();
        paint.setColor(color);
        float offset = 9;
        RectF rectTemp = new RectF(PADDING + offset - CIRCLE_WIDTH / 2, PADDING + offset - CIRCLE_WIDTH / 2, (displaySize.x - PADDING - offset + CIRCLE_WIDTH / 2), (displaySize.y - PADDING - offset + CIRCLE_WIDTH / 2));
        canvas.drawArc(rectTemp, convertedBg, 2, true, paint);
    }

    private float bgToAngle(float bg) {
        if (bg > 100) {
            return (((bg - 100f) / 300f) * 225f) + 135;
        } else {
            return ((bg / 100) * 135);
        }
    }


    public void addReadingSoft(Canvas canvas, EventData.SingleBg entry) {

        aapsLogger.debug(LTag.WEAR, "addReadingSoft");
        double size;
        int color = Color.LTGRAY;
        if (sp.getBoolean("dark", true)) {
            color = Color.DKGRAY;
        }

        float offsetMultiplier = (((displaySize.x / 2f) - PADDING) / 12f);
        float offset = (float) Math.max(1,
                Math.ceil((System.currentTimeMillis() - entry.getTimeStamp()) / (1000 * 60 * 5.0)));
        size = bgToAngle((float) entry.getSgv());
        addArch(canvas, offset * offsetMultiplier + 10, color, (float) size);
        addArch(canvas, (float) size, offset * offsetMultiplier + 10, getBackgroundColor(), (float) (360 - size));
        addArch(canvas, (offset + .8f) * offsetMultiplier + 10, getBackgroundColor(), 360);
    }

    public void addReading(Canvas canvas, EventData.SingleBg entry) {
        aapsLogger.debug(LTag.WEAR, "addReading");

        double size;
        int color = Color.LTGRAY;
        int indicatorColor = Color.DKGRAY;
        if (sp.getBoolean("dark", true)) {
            color = Color.DKGRAY;
            indicatorColor = Color.LTGRAY;
        }
        int barColor = Color.GRAY;
        if (entry.getSgv() >= entry.getHigh()) {
            indicatorColor = getHighColor();
            barColor = darken(getHighColor(), .5);
        } else if (entry.getSgv() <= entry.getLow()) {
            indicatorColor = getLowColor();
            barColor = darken(getLowColor(), .5);
        }
        float offsetMultiplier = (((displaySize.x / 2f) - PADDING) / 12f);
        float offset = (float) Math.max(1,
                Math.ceil((System.currentTimeMillis() - entry.getTimeStamp()) / (1000 * 60 * 5.0)));
        size = bgToAngle((float) entry.getSgv());
        addArch(canvas, offset * offsetMultiplier + 11, barColor, (float) size - 2); // Dark Color Bar
        addArch(canvas, (float) size - 2, offset * offsetMultiplier + 11, indicatorColor, 2f); // Indicator at end of bar
        addArch(canvas, (float) size, offset * offsetMultiplier + 11, color, (float) (360f - size)); // Dark fill
        addArch(canvas, (offset + .8f) * offsetMultiplier + 11, getBackgroundColor(), 360);
    }

    @Override
    protected void onTapCommand(int tapType, int x, int y, long eventTime) {
        if (mSgv == null) return;
        int extra = (mSgv.getRight() - mSgv.getLeft()) / 2;

        if (tapType == TAP_TYPE_TAP &&
                x + extra >= mSgv.getLeft() &&
                x - extra <= mSgv.getRight() &&
                y >= mSgv.getTop() &&
                y <= mSgv.getBottom()) {
            if (eventTime - sgvTapTime < 800) {
                Intent intent = new Intent(this, MainMenuActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            }
            sgvTapTime = eventTime;
        }
    }

    @Override
    protected WatchFaceStyle getWatchFaceStyle() {
        return new WatchFaceStyle.Builder(this).setAcceptsTapEvents(true).build();
    }
}