package info.nightscout.androidaps.complications;

/*
 * Created by dlvoy on 2019-11-12
 */
public class WallpaperGrayComplication extends WallpaperComplication {

    @Override
    public String getWallpaperAssetsFileName() {
        return "watch_gray.jpg";
    }

    @Override
    public String getProviderCanonicalName() {
        return WallpaperGrayComplication.class.getCanonicalName();
    }

    @Override
    public ComplicationAction getComplicationAction() {
        return ComplicationAction.NONE;
    };
}
