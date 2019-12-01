package info.nightscout.androidaps.complications;

/*
 * Created by dlvoy on 2019-11-12
 */
public class WallpaperDarkComplication extends WallpaperComplication {

    @Override
    public String getWallpaperAssetsFileName() {
        return "watch_dark.jpg";
    }

    @Override
    public String getProviderCanonicalName() {
        return WallpaperDarkComplication.class.getCanonicalName();
    }

    @Override
    public ComplicationAction getComplicationAction() {
        return ComplicationAction.NONE;
    };
}
