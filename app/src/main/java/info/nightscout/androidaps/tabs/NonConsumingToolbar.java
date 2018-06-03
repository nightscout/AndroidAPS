package info.nightscout.androidaps.tabs;

import android.content.Context;
import android.support.annotation.Nullable;
import android.support.v7.widget.Toolbar;
import android.util.AttributeSet;
import android.view.MotionEvent;

public class NonConsumingToolbar extends Toolbar {
    public NonConsumingToolbar(Context context) {
        super(context);
    }

    public NonConsumingToolbar(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public NonConsumingToolbar(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN || ev.getAction() == MotionEvent.ACTION_POINTER_DOWN)
            return performClick();
        return false;
    }
}
