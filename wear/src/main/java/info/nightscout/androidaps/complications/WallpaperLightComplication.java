package info.nightscout.androidaps.complications;

/*
 * Created by dlvoy on 2019-11-12
 */
public class WallpaperLightComplication extends WallpaperComplication {

    @Override
    public String getWallpaperAssetsFileName() {
        return "watch_light.jpg";
    }

    @Override
    public String getProviderCanonicalName() {
        return WallpaperLightComplication.class.getCanonicalName();
    }

    @Override
    public ComplicationAction getComplicationAction() {
        return ComplicationAction.NONE;
    };
}
