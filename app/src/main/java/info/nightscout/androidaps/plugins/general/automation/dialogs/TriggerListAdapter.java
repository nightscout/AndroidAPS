package info.nightscout.androidaps.plugins.general.automation.dialogs;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Spinner;

import androidx.fragment.app.FragmentManager;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.plugins.general.automation.triggers.Trigger;
import info.nightscout.androidaps.plugins.general.automation.triggers.TriggerConnector;
import info.nightscout.androidaps.utils.resources.ResourceHelper;

public class TriggerListAdapter {
    private final LinearLayout mRootLayout;
    private final Context mContext;
    private final TriggerConnector mRootConnector;
    private final MainApp mainApp;
    private final ResourceHelper resourceHelper;

    public TriggerListAdapter(MainApp mainApp, ResourceHelper resourceHelper, Context context, LinearLayout rootLayout, TriggerConnector rootTrigger) {
        mRootLayout = rootLayout;
        this.mainApp = mainApp;
        this.resourceHelper = resourceHelper;
        mContext = context;
        mRootConnector = rootTrigger;
        build(rootTrigger.scanForActivity(context).getSupportFragmentManager());
    }

    public Context getContext() {
        return mContext;
    }

    private void destroy() {
        mRootLayout.removeAllViews();
    }

    private void build(FragmentManager fragmentManager) {
        for (int i = 0; i < mRootConnector.size(); ++i) {
            final Trigger trigger = mRootConnector.get(i);

            // spinner
            if (i > 0) {
                createSpinner(trigger, fragmentManager);
            }

            // trigger layout
            trigger.generateDialog(mRootLayout);

            // buttons
            createButtons(fragmentManager, trigger);
        }

        if (mRootConnector.size() == 0) {
            Button buttonAdd = new Button(mContext);
            buttonAdd.setText(resourceHelper.gs(R.string.addnew));
            buttonAdd.setOnClickListener(v -> {
                ChooseTriggerDialog dialog = new ChooseTriggerDialog();
                dialog.setOnClickListener(newTriggerObject -> {
                    mRootConnector.add(newTriggerObject);
                    rebuild(fragmentManager);
                });
                dialog.show(fragmentManager, "ChooseTriggerDialog");
            });
            mRootLayout.addView(buttonAdd);
        }
    }

    private Spinner createSpinner() {
        Spinner spinner = new Spinner(mContext);
        ArrayAdapter<String> spinnerArrayAdapter = new ArrayAdapter<>(mContext, R.layout.spinner_centered, TriggerConnector.Type.AND.labels(resourceHelper));
        spinnerArrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(spinnerArrayAdapter);
        return spinner;
    }

    private void createSpinner(Trigger trigger, FragmentManager fragmentManager) {
        final TriggerConnector connector = trigger.getConnector();
        final int initialPosition = connector.getConnectorType().ordinal();
        Spinner spinner = createSpinner();
        spinner.setSelection(initialPosition);
        spinner.setBackgroundColor(resourceHelper.gc(R.color.black_overlay));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, resourceHelper.dpToPx(8), 0, resourceHelper.dpToPx(8));
        spinner.setLayoutParams(params);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position != initialPosition) {
                    // connector type changed
                    changeConnector(fragmentManager, trigger, connector, TriggerConnector.Type.values()[position]);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        mRootLayout.addView(spinner);
    }

    private void createButtons(FragmentManager fragmentManager, Trigger trigger) {
        // do not create buttons for TriggerConnector
        if (trigger instanceof TriggerConnector) {
            return;
        }

        // Button Layout
        LinearLayout buttonLayout = new LinearLayout(mContext);
        buttonLayout.setOrientation(LinearLayout.HORIZONTAL);
        buttonLayout.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        mRootLayout.addView(buttonLayout);

        // Button [-]
        Button buttonRemove = new Button(mContext);
        buttonRemove.setText(resourceHelper.gs(R.string.delete_short));
        buttonRemove.setOnClickListener(v -> {
            final TriggerConnector connector = trigger.getConnector();
            connector.remove(trigger);
            connector.simplify().rebuildView(fragmentManager);
        });
        buttonLayout.addView(buttonRemove);

        // Button [+]
        Button buttonAdd = new Button(mContext);
        buttonAdd.setText(resourceHelper.gs(R.string.add_short));
        buttonAdd.setOnClickListener(v -> {
            ChooseTriggerDialog dialog = new ChooseTriggerDialog();
            dialog.show(fragmentManager, "ChooseTriggerDialog");
            dialog.setOnClickListener(newTriggerObject -> {
                TriggerConnector connector = trigger.getConnector();
                connector.add(connector.pos(trigger) + 1, newTriggerObject);
                connector.simplify().rebuildView(fragmentManager);
            });
        });
        buttonLayout.addView(buttonAdd);

        // Button [*]
        Button buttonCopy = new Button(mContext);
        buttonCopy.setText(resourceHelper.gs(R.string.copy_short));
        buttonCopy.setOnClickListener(v -> {
            TriggerConnector connector = trigger.getConnector();
            connector.add(connector.pos(trigger) + 1, trigger.duplicate());
            connector.simplify().rebuildView(fragmentManager);
        });
        buttonLayout.addView(buttonCopy);
    }

    public void rebuild(FragmentManager fragmentManager) {
        destroy();
        build(fragmentManager);
    }

    public void changeConnector(final FragmentManager fragmentManager, final Trigger trigger, final TriggerConnector connector, final TriggerConnector.Type newConnectorType) {
        if (connector.size() > 2) {
            // split connector
            int pos = connector.pos(trigger) - 1;

            TriggerConnector newConnector = new TriggerConnector(mainApp, newConnectorType);

            // move trigger from pos and pos+1 into new connector
            for (int i = 0; i < 2; ++i) {
                Trigger t = connector.get(pos);
                newConnector.add(t);
                connector.remove(t);
            }

            connector.add(pos, newConnector);
        } else {
            connector.setType(newConnectorType);
        }
        connector.simplify().rebuildView(fragmentManager);
    }
}
