package info.nightscout.androidaps.startupwizard;

import java.util.ArrayList;
import java.util.List;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.events.EventRefreshGui;
import info.nightscout.androidaps.plugins.NSClientInternal.NSClientPlugin;
import info.nightscout.utils.LocaleHelper;
import info.nightscout.utils.SP;

public class SWDefinition {

    private static SWDefinition swDefinition = null;

    public static SWDefinition getInstance() {
        if (swDefinition == null)
            swDefinition = new SWDefinition();
        return swDefinition;
    }
    android.content.Context context = MainApp.instance().getApplicationContext();

    static List<SWScreen> screens = new ArrayList<>();

    public static List<SWScreen> getScreens() {
        return screens;
    }

    SWDefinition add(SWScreen newScreen) {
        screens.add(newScreen);
        return this;
    }

    SWDefinition() {
        // List all the screens here
        // todo: SWValidator ?!?
        add(new SWScreen(R.string.language)
        .skippable(false)
        .add(new SWRadioButton().option(R.array.languagesArray, R.array.languagesValues).preferenceId(R.string.key_language).label(R.string.language).comment(R.string.setupwizard_language_prompt))
        .validator(() -> {
            context = MainApp.instance().getApplicationContext();
            LocaleHelper.setLocale(context, SP.getString(R.string.key_language, "en"));
            MainApp.bus().post(new EventRefreshGui(true));
            return SP.contains(R.string.key_language);}
        ))
        .add(new SWScreen(R.string.nsclientinternal_title)
                .skippable(true)
                .add(new SWUrl().preferenceId(R.string.key_nsclientinternal_url).label(R.string.nsclientinternal_url_title).comment(R.string.nsclientinternal_url_dialogmessage))
                .add(new SWString().preferenceId(R.string.key_nsclientinternal_api_secret).label(R.string.nsclientinternal_secret_dialogtitle).comment(R.string.nsclientinternal_secret_dialogmessage))
                .validator(() -> NSClientPlugin.getPlugin().nsClientService.isConnected && NSClientPlugin.getPlugin().nsClientService.hasWriteAuth)
        )
        .add(new SWScreen(R.string.patientage)
                .skippable(false)
                .add(new SWRadioButton().option(R.array.ageArray, R.array.ageValues).preferenceId(R.string.key_age).label(R.string.patientage).comment(R.string.patientage_summary))
                .validator(() -> SP.contains(R.string.key_age))
        )

        ;
    }

}
