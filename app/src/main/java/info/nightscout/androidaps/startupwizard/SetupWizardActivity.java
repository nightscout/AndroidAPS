package info.nightscout.androidaps.startupwizard;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.squareup.otto.Subscribe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import info.nightscout.androidaps.MainActivity;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.events.EventProfileStoreChanged;
import info.nightscout.androidaps.events.EventPumpStatusChanged;
import info.nightscout.androidaps.plugins.NSClientInternal.events.EventNSClientStatus;
import info.nightscout.androidaps.startupwizard.events.EventSWUpdate;
import info.nightscout.utils.LocaleHelper;

public class SetupWizardActivity extends AppCompatActivity {
    //logging
    private static Logger log = LoggerFactory.getLogger(SetupWizardActivity.class);

    private SWDefinition swDefinition = new SWDefinition();
    private List<SWScreen> screens = swDefinition.getScreens();
    private int currentWizardPage = 0;
    public static final String INTENT_MESSAGE = "WIZZARDPAGE";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LocaleHelper.onCreate(this, "en");
        setContentView(R.layout.activity_setupwizard);

        Intent intent = getIntent();
        currentWizardPage = intent.getIntExtra(SetupWizardActivity.INTENT_MESSAGE, 0);
        if (screens.size() > 0 && currentWizardPage < screens.size()) {
            SWScreen currentScreen = screens.get(currentWizardPage);

            //Set screen name
            TextView screenName = (TextView) findViewById(R.id.sw_content);
            screenName.setText(currentScreen.getHeader());

            swDefinition.setActivity(this);
            //Generate layout first
            generateLayout();
            updateButtons();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        MainApp.bus().unregister(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        MainApp.bus().register(this);
        swDefinition.setActivity(this);
    }

    @Subscribe
    public void onContentUpdate(EventSWUpdate ev) {
        if (ev.redraw)
            generateLayout();
        updateButtons();
    }

    @Subscribe
    public void onEventNSClientStatus(EventNSClientStatus ignored) {
        updateButtons();
    }

    @Subscribe
    public void onEventPumpStatusChanged(EventPumpStatusChanged ignored) {
        updateButtons();
    }

    @Subscribe
    public void onEventProfileStoreChanged(EventProfileStoreChanged ignored) {
        updateButtons();
    }

    private void generateLayout() {
        SWScreen currentScreen = screens.get(currentWizardPage);
        LinearLayout layout = SWItem.generateLayout(this.findViewById(R.id.sw_content_fields));
        for (int i = 0; i < currentScreen.items.size(); i++) {
            SWItem currentItem = currentScreen.items.get(i);
            currentItem.generateDialog(this.findViewById(R.id.sw_content_fields), layout);
        }
    }

    private void updateButtons() {
        runOnUiThread(() -> {
            SWScreen currentScreen = screens.get(currentWizardPage);
            if (currentScreen.validator == null || currentScreen.validator.isValid() || currentScreen.skippable) {
                if (currentWizardPage == nextPage()) {
                    findViewById(R.id.finish_button).setVisibility(View.VISIBLE);
                    findViewById(R.id.next_button).setVisibility(View.GONE);
                } else {
                    findViewById(R.id.finish_button).setVisibility(View.GONE);
                    findViewById(R.id.next_button).setVisibility(View.VISIBLE);
                }
            } else {
                findViewById(R.id.finish_button).setVisibility(View.GONE);
                findViewById(R.id.next_button).setVisibility(View.GONE);
            }
            if (currentWizardPage == 0)
                findViewById(R.id.previous_button).setVisibility(View.GONE);
            else
                findViewById(R.id.previous_button).setVisibility(View.VISIBLE);
            currentScreen.processVisibility();
        });
    }

    public void showNextPage(View view) {
        this.finish();
        Intent intent = new Intent(this, SetupWizardActivity.class);
        intent.putExtra(INTENT_MESSAGE, nextPage());
        startActivity(intent);
    }

    public void showPreviousPage(View view) {
        this.finish();
        Intent intent = new Intent(this, SetupWizardActivity.class);
        intent.putExtra(INTENT_MESSAGE, previousPage());
        startActivity(intent);
    }

    // Go back to overview
    public void finishSetupWizard(View view) {
        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
    }

    private int nextPage() {
        int page = currentWizardPage + 1;
        while (page < screens.size()) {
            if (screens.get(page).visibility == null || screens.get(page).visibility.isValid())
                return page;
            page++;
        }
        return currentWizardPage;
    }

    private int previousPage() {
        int page = currentWizardPage - 1;
        while (page > 0) {
            if (screens.get(page).visibility == null || screens.get(page).visibility.isValid())
                return page;
            page--;
        }
        return currentWizardPage;
    }
}