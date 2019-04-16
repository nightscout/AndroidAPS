package info.nightscout.androidaps.plugins.general.overview.activities;

import android.app.Activity;
import android.os.Bundle;
import android.support.v4.app.FragmentManager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.CardView;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import com.squareup.otto.Subscribe;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.QuickWizard;
import info.nightscout.androidaps.plugins.general.overview.dialogs.EditQuickWizardDialog;
import info.nightscout.androidaps.plugins.general.overview.OverviewPlugin;
import info.nightscout.androidaps.plugins.general.overview.events.EventQuickWizardChange;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.DecimalFormatter;

public class QuickWizardListActivity extends AppCompatActivity implements View.OnClickListener {

    RecyclerView recyclerView;
    LinearLayoutManager llm;

    Button adButton;

    public static class RecyclerViewAdapter extends RecyclerView.Adapter<RecyclerViewAdapter.QuickWizardEntryViewHolder> {

        QuickWizard qvData;
        FragmentManager fragmentManager;

        RecyclerViewAdapter(QuickWizard data, FragmentManager fragmentManager) {
            this.qvData = data;
            this.fragmentManager = fragmentManager;
        }

        @Override
        public QuickWizardEntryViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
            View v = LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.overview_quickwizardlist_item, viewGroup, false);
            QuickWizardEntryViewHolder quickWizardEntryViewHolder = new QuickWizardEntryViewHolder(v, fragmentManager, qvData);
            return quickWizardEntryViewHolder;
        }

        @Override
        public void onBindViewHolder(QuickWizardEntryViewHolder holder, int position) {
            holder.from.setText(DateUtil.timeString(qvData.get(position).validFromDate()));
            holder.to.setText(DateUtil.timeString(qvData.get(position).validToDate()));
            holder.buttonText.setText(qvData.get(position).buttonText());
            holder.carbs.setText(DecimalFormatter.to0Decimal(qvData.get(position).carbs()) + " g");
        }

        @Override
        public int getItemCount() {
            return qvData.size();
        }

        @Override
        public void onAttachedToRecyclerView(RecyclerView recyclerView) {
            super.onAttachedToRecyclerView(recyclerView);
        }

        public static class QuickWizardEntryViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
            CardView cv;
            TextView buttonText;
            TextView carbs;
            TextView from;
            TextView to;
            Button editButton;
            Button removeButton;
            FragmentManager fragmentManager;
            QuickWizard qvData;

            QuickWizardEntryViewHolder(View itemView, FragmentManager fragmentManager, QuickWizard qvData) {
                super(itemView);
                cv = (CardView) itemView.findViewById(R.id.overview_quickwizard_cardview);
                buttonText = (TextView) itemView.findViewById(R.id.overview_quickwizard_item_buttonText);
                carbs = (TextView) itemView.findViewById(R.id.overview_quickwizard_item_carbs);
                from = (TextView) itemView.findViewById(R.id.overview_quickwizard_item_from);
                to = (TextView) itemView.findViewById(R.id.overview_quickwizard_item_to);
                editButton = (Button) itemView.findViewById(R.id.overview_quickwizard_item_edit_button);
                removeButton = (Button) itemView.findViewById(R.id.overview_quickwizard_item_remove_button);
                editButton.setOnClickListener(this);
                removeButton.setOnClickListener(this);
                this.fragmentManager = fragmentManager;
                this.qvData = qvData;
            }

            @Override
            public void onClick(View v) {
                int position = getAdapterPosition();
                switch (v.getId()) {
                    case R.id.overview_quickwizard_item_edit_button:
                        FragmentManager manager = fragmentManager;
                        EditQuickWizardDialog editQuickWizardDialog = new EditQuickWizardDialog();
                        editQuickWizardDialog.setData(qvData.get(position));
                        editQuickWizardDialog.show(manager, "EditQuickWizardDialog");
                        break;
                    case R.id.overview_quickwizard_item_remove_button:
                        qvData.remove(position);
                        MainApp.bus().post(new EventQuickWizardChange());
                        break;
                }
            }
        }
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.overview_quickwizardlist_activity);

        recyclerView = (RecyclerView) findViewById(R.id.overview_quickwizardactivity_recyclerview);
        recyclerView.setHasFixedSize(true);
        llm = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(llm);

        RecyclerViewAdapter adapter = new RecyclerViewAdapter(MainApp.getSpecificPlugin(OverviewPlugin.class).quickWizard, getSupportFragmentManager());
        recyclerView.setAdapter(adapter);

        adButton = (Button) findViewById(R.id.overview_quickwizardactivity_add_button);
        adButton.setOnClickListener(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        MainApp.bus().register(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        MainApp.bus().unregister(this);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.overview_quickwizardactivity_add_button:
                FragmentManager manager = getSupportFragmentManager();
                EditQuickWizardDialog editQuickWizardDialog = new EditQuickWizardDialog();
                editQuickWizardDialog.show(manager, "EditQuickWizardDialog");
                break;
        }
    }

    @Subscribe
    public void onStatusEvent(final EventQuickWizardChange ev) {
        updateGUI();
    }

    public void updateGUI() {
        Activity activity = this;
        if (activity != null && recyclerView != null) {
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    RecyclerViewAdapter adapter = new RecyclerViewAdapter(MainApp.getSpecificPlugin(OverviewPlugin.class).quickWizard, getSupportFragmentManager());
                    recyclerView.swapAdapter(adapter, false);
                }
            });
        }
    }
}
