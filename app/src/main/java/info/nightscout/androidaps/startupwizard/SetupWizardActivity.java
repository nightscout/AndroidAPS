package info.nightscout.androidaps.startupwizard;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import info.nightscout.androidaps.MainActivity;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.events.EventRefreshGui;
import info.nightscout.utils.LocaleHelper;
import info.nightscout.utils.SP;

public class SetupWizardActivity extends AppCompatActivity {
    private List<String> labels = new ArrayList<>();
    private List<String> comments = new ArrayList<>();

    private TextView screenName;

    //logging
    private static Logger log = LoggerFactory.getLogger(SetupWizardActivity.class);

    private int currentWizardPage = 0;
    public static final String INTENT_MESSAGE = "WIZZARDPAGE";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setupwizard);

        Intent intent = getIntent();
        int showPage = intent.getIntExtra(SetupWizardActivity.INTENT_MESSAGE, 0);
        SWDefinition swDefinition = SWDefinition.getInstance();
        List<SWScreen> screens = swDefinition.getScreens();
        if (screens.size() > 0 && showPage < screens.size()) {
            SWScreen currentScreen = screens.get(showPage);
            currentWizardPage = showPage;
            // show/hide prev/next buttons if we are at the beninning/end
            //showNextButton(showPage, screens.size()-1);

            if (showPage == 0)
                ((Button) findViewById(R.id.previous_button)).setVisibility(View.GONE);
            //Set screen name
            screenName = (TextView) findViewById(R.id.fullscreen_content);
            screenName.setText(currentScreen.getHeader());

            //Generate layout first
            LinearLayout layout = info.nightscout.androidaps.startupwizard.SWItem.generateLayout(this.findViewById(R.id.fullscreen_content_fields));
            for (int i = 0; i < currentScreen.items.size(); i++) {
                SWItem currentItem = currentScreen.items.get(i);
                labels.add(i, currentItem.getLabel());
                comments.add(i, currentItem.getComment());
                currentItem.setOptions(labels, comments);
                currentItem.generateDialog(this.findViewById(R.id.fullscreen_content_fields), layout);
            }
            // Check if input isValid or screen is sckippable
            if (currentScreen.validator.isValid() || currentScreen.skippable) {
                showNextButton(showPage, screens.size() - 1);
            }

        }

    }

    @Override
    protected void onResume() {

        super.onResume();
        // check is current locale is different from the one in preferences
//        log.debug("Current: "+LocaleHelper.getLanguage(this)+" preferences: "+SP.getString("language", "en"));
        if (!LocaleHelper.getLanguage(this).equals(SP.getString("language", "en"))) {
            // it is so change it in locale and restart SetupWizard
//            log.debug("Setting locale to: "+SP.getString("language", "en")+" and restarting");
            LocaleHelper.setLocale(this, SP.getString(R.string.key_language, "en"));
            MainApp.bus().post(new EventRefreshGui(true));
            Intent intent = getIntent();
            this.finish();
            startActivity(intent);
        }
    }

    private void showNextButton(int currentPage, int maxPages) {
        if (currentPage == maxPages) {
            ((Button) findViewById(R.id.finish_button)).setVisibility(View.VISIBLE);
            ((Button) findViewById(R.id.next_button)).setVisibility(View.GONE);
        } else
            ((Button) findViewById(R.id.next_button)).setVisibility(View.VISIBLE);
    }

    public void showNextPage(View view) {
        Intent intent = new Intent(this, SetupWizardActivity.class);
        intent.putExtra(INTENT_MESSAGE, currentWizardPage + 1);
        startActivity(intent);
    }

    public void showPreviousPage(View view) {
        Intent intent = new Intent(this, SetupWizardActivity.class);
        if (currentWizardPage > 0)
            intent.putExtra(INTENT_MESSAGE, currentWizardPage - 1);
        else
            intent.putExtra(INTENT_MESSAGE, 0);
        startActivity(intent);
    }

    // Go back to overview
    public void finishSetupWizard(View view) {
        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
    }

}
