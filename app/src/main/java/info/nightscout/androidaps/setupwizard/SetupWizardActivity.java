package info.nightscout.androidaps.setupwizard;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;

import java.util.List;

import info.nightscout.androidaps.MainActivity;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.activities.NoSplashAppCompatActivity;
import info.nightscout.androidaps.events.EventProfileNeedsUpdate;
import info.nightscout.androidaps.events.EventProfileStoreChanged;
import info.nightscout.androidaps.events.EventPumpStatusChanged;
import info.nightscout.androidaps.plugins.bus.RxBus;
import info.nightscout.androidaps.plugins.general.nsclient.events.EventNSClientStatus;
import info.nightscout.androidaps.setupwizard.elements.SWItem;
import info.nightscout.androidaps.setupwizard.events.EventSWUpdate;
import info.nightscout.androidaps.utils.AndroidPermission;
import info.nightscout.androidaps.utils.FabricPrivacy;
import info.nightscout.androidaps.utils.LocaleHelper;
import info.nightscout.androidaps.utils.OKDialog;
import info.nightscout.androidaps.utils.SP;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;

public class SetupWizardActivity extends NoSplashAppCompatActivity {
    private CompositeDisposable disposable = new CompositeDisposable();

    ScrollView scrollView;

    private SWDefinition swDefinition = new SWDefinition();
    private List<SWScreen> screens = swDefinition.getScreens();
    private int currentWizardPage = 0;
    public static final String INTENT_MESSAGE = "WIZZARDPAGE";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LocaleHelper.INSTANCE.update(getApplicationContext());
        setContentView(R.layout.activity_setupwizard);

        scrollView = findViewById(R.id.sw_scrollview);

        Intent intent = getIntent();
        currentWizardPage = intent.getIntExtra(SetupWizardActivity.INTENT_MESSAGE, 0);
        if (screens.size() > 0 && currentWizardPage < screens.size()) {
            SWScreen currentScreen = screens.get(currentWizardPage);

            //Set screen name
            TextView screenName = findViewById(R.id.sw_content);
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
        disposable.clear();
    }

    @Override
    protected void onResume() {
        super.onResume();
        swDefinition.setActivity(this);
        disposable.add(RxBus.INSTANCE
                .toObservable(EventPumpStatusChanged.class)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(event -> updateButtons(), FabricPrivacy::logException)
        );
        disposable.add(RxBus.INSTANCE
                .toObservable(EventNSClientStatus.class)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(event -> updateButtons(), FabricPrivacy::logException)
        );
        disposable.add(RxBus.INSTANCE
                .toObservable(EventProfileNeedsUpdate.class)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(event -> updateButtons(), FabricPrivacy::logException)
        );
        disposable.add(RxBus.INSTANCE
                .toObservable(EventProfileStoreChanged.class)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(event -> updateButtons(), FabricPrivacy::logException)
        );
        disposable.add(RxBus.INSTANCE
                .toObservable(EventSWUpdate.class)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(event -> {
                    if (event.getRedraw()) generateLayout();
                    updateButtons();
                }, FabricPrivacy::logException)
        );
    }

    private void generateLayout() {
        SWScreen currentScreen = screens.get(currentWizardPage);
        LinearLayout layout = SWItem.generateLayout(this.findViewById(R.id.sw_content_fields));
        for (int i = 0; i < currentScreen.items.size(); i++) {
            SWItem currentItem = currentScreen.items.get(i);
            currentItem.generateDialog(layout);
        }
        scrollView.smoothScrollTo(0, 0);
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

    @Override
    public void onBackPressed() {
        if (currentWizardPage == 0)
            OKDialog.showConfirmation(this, MainApp.gs(R.string.exitwizard), this::finish);
        else showPreviousPage(null);
    }

    public void exitPressed(View view) {
        SP.putBoolean(R.string.key_setupwizard_processed, true);
        OKDialog.showConfirmation(this, MainApp.gs(R.string.exitwizard), this::finish);
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
        SP.putBoolean(R.string.key_setupwizard_processed, true);
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
        finish();
    }

    private int nextPage() {
        int page = currentWizardPage + 1;
        while (page < screens.size()) {
            if (screens.get(page).visibility == null || screens.get(page).visibility.isValid())
                return page;
            page++;
        }
        return Math.min(currentWizardPage, screens.size() - 1);
    }

    private int previousPage() {
        int page = currentWizardPage - 1;
        while (page >= 0) {
            if (screens.get(page).visibility == null || screens.get(page).visibility.isValid())
                return page;
            page--;
        }
        return Math.max(currentWizardPage, 0);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (permissions.length != 0) {
            if (ActivityCompat.checkSelfPermission(this, permissions[0]) == PackageManager.PERMISSION_GRANTED) {
                switch (requestCode) {
                    case AndroidPermission.CASE_STORAGE:
                        //show dialog after permission is granted
                        OKDialog.show(this, MainApp.gs(R.string.permission), MainApp.gs(R.string.alert_dialog_storage_permission_text));
                        break;
                    case AndroidPermission.CASE_LOCATION:
                    case AndroidPermission.CASE_SMS:
                    case AndroidPermission.CASE_BATTERY:
                        break;
                }
            }
        }
        updateButtons();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == AndroidPermission.CASE_BATTERY)
            updateButtons();
    }
}
