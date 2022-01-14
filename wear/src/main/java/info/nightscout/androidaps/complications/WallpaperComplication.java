package info.nightscout.androidaps.complications;

import android.app.PendingIntent;
import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Icon;
import android.support.wearable.complications.ComplicationData;
import android.util.DisplayMetrics;
import android.view.WindowManager;

import java.io.IOException;
import java.io.InputStream;

import info.nightscout.androidaps.data.RawDisplayData;
import info.nightscout.shared.logging.LTag;

/*
 * Created by dlvoy on 2019-11-12
 */
public abstract class WallpaperComplication extends BaseComplicationProviderService {

    public abstract String getWallpaperAssetsFileName();

    public ComplicationData buildComplicationData(int dataType, RawDisplayData raw, PendingIntent complicationPendingIntent) {

        ComplicationData complicationData = null;

        if (dataType == ComplicationData.TYPE_LARGE_IMAGE) {

            DisplayMetrics metrics = new DisplayMetrics();
            WindowManager windowManager = (WindowManager) getApplicationContext()
                    .getSystemService(Context.WINDOW_SERVICE);
            windowManager.getDefaultDisplay().getMetrics(metrics);
            int width = metrics.widthPixels;
            int height = metrics.heightPixels;

            final ComplicationData.Builder builder = new ComplicationData.Builder(ComplicationData.TYPE_LARGE_IMAGE);

            AssetManager assetManager = getAssets();
            try (InputStream iStr = assetManager.open(getWallpaperAssetsFileName())) {
                Bitmap bitmap = BitmapFactory.decodeStream(iStr);
                Bitmap scaled = Bitmap.createScaledBitmap(bitmap, width, height, true);
                builder.setLargeImage(Icon.createWithBitmap(scaled));
            } catch (IOException e) {
                aapsLogger.error(LTag.WEAR, "Cannot read wallpaper asset: " + e.getMessage(), e);
            }

            complicationData = builder.build();
        } else {
            aapsLogger.warn(LTag.WEAR, "Unexpected complication type " + dataType);
        }
        return complicationData;
    }
}
