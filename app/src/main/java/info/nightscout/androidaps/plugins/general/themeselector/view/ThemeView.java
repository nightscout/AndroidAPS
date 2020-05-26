package info.nightscout.androidaps.plugins.general.themeselector.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import info.nightscout.androidaps.R;
import info.nightscout.androidaps.plugins.general.themeselector.model.Theme;

/**
 * Created by Pankaj on 27-10-2017.
 */

public class ThemeView extends View {
    private Theme mTheme = new Theme(R.color.primaryColorAmber , R.color.primaryDarkColorAmber, R.color.secondaryColorAmber);

    private Paint mBoarderPaint;
    private Paint mPrimaryPaint;
    private Paint mPrimaryDarkPaint;
    private Paint mAccentPaint;
    private Paint mBackgroundPaint;

    private TextView themeLabel;

    private float stroke;

    public ThemeView(Context context) {
        super(context);
        init();
    }

    public ThemeView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ThemeView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    public ThemeView(Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init();
    }

    public void setTheme(Theme theme){
        this.mTheme = theme;
        init();
        invalidate();
    }

    private void init(){
        try {

            themeLabel = findViewById(R.id.themeLabel);

            if(themeLabel != null){
                themeLabel.setText("Test");
            }

            mBoarderPaint = new Paint();
            mBoarderPaint.setStyle(Paint.Style.STROKE);
            if (this.isSelected()) {
                mBoarderPaint.setColor(Color.BLUE);
            } else {
                mBoarderPaint.setColor(Color.GRAY);
            }

            mBackgroundPaint = new Paint();
            mBackgroundPaint.setStyle(Paint.Style.FILL);
            int color = android.R.color.background_light;
            TypedValue a = new TypedValue();
            getContext().getTheme().resolveAttribute(android.R.attr.windowBackground, a, true);
            if (a.type >= TypedValue.TYPE_FIRST_COLOR_INT && a.type <= TypedValue.TYPE_LAST_COLOR_INT)
                color = a.data;    // windowBackground is a color
            mBackgroundPaint.setColor(color);

            mPrimaryDarkPaint = new Paint();
            mPrimaryDarkPaint.setStyle(Paint.Style.FILL);
            mPrimaryDarkPaint.setColor(ContextCompat.getColor(getContext(), mTheme.getPrimaryDarkColor()));

            mPrimaryPaint = new Paint();
            mPrimaryPaint.setStyle(Paint.Style.FILL);
            mPrimaryPaint.setColor(ContextCompat.getColor(getContext(), mTheme.getPrimaryColor()));

            mAccentPaint = new Paint();
            mAccentPaint.setStyle(Paint.Style.FILL);
            mAccentPaint.setColor(ContextCompat.getColor(getContext(), mTheme.getAccentColor()));
            mAccentPaint.setAntiAlias(true);
            mAccentPaint.setDither(true);
        }catch (Exception ex){
            ex.printStackTrace();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        final float height = getHeight();
        final float width = getWidth();
        stroke = height*8/100f;
        final float statusbar = height * 16/100f; 
        final float toolbar = height * 72/100f;

        if (this.isActivated()) {
            mBoarderPaint.setColor(ContextCompat.getColor(getContext(), R.color.themeSelected));
        } else {
            mBoarderPaint.setColor(ContextCompat.getColor(getContext(), R.color.themeDeselected));
        }
        mBoarderPaint.setStrokeWidth(stroke);
        canvas.drawRect(0,0,width,height,mBackgroundPaint);
        canvas.drawRect(0,0,width,statusbar,mPrimaryDarkPaint);
        canvas.drawRect(0,statusbar,width,toolbar,mPrimaryPaint);
        canvas.drawCircle(width-stroke-height*20 /100f,toolbar, height*16/100, mAccentPaint);
        canvas.drawRect(0,0,width,height,mBoarderPaint);
    }
}
