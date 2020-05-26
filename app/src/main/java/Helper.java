import android.content.Context;
import android.content.res.Resources;
import android.util.TypedValue;

import androidx.annotation.ColorInt;

public class Helper {
    public static int getAttributeColor(
            Context context,
            int attributeId) {
        TypedValue typedValue = new TypedValue();
        Resources.Theme theme = context.getTheme();
        theme.resolveAttribute(attributeId, typedValue, true);
        @ColorInt int color = typedValue.data;
        return color;

    }
}
