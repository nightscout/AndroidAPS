package info.nightscout.androidaps.plugins.treatments.fragments;

import android.graphics.Paint;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.Iob;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.db.Source;
import info.nightscout.androidaps.events.EventTreatmentChange;
import info.nightscout.androidaps.plugins.bus.RxBus;
import info.nightscout.androidaps.plugins.configBuilder.ProfileFunctions;
import info.nightscout.androidaps.plugins.general.nsclient.NSUpload;
import info.nightscout.androidaps.plugins.general.nsclient.UploadQueue;
import info.nightscout.androidaps.plugins.general.nsclient.events.EventNSClientRestart;
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.events.EventAutosensCalculationFinished;
import info.nightscout.androidaps.plugins.treatments.Treatment;
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin;
import info.nightscout.androidaps.plugins.treatments.dialogs.WizardInfoDialog;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.DecimalFormatter;
import info.nightscout.androidaps.utils.FabricPrivacy;
import info.nightscout.androidaps.utils.OKDialog;
import info.nightscout.androidaps.utils.SP;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;

import static info.nightscout.androidaps.utils.DateUtil.now;

public class TreatmentsBolusFragment extends Fragment {
    private CompositeDisposable disposable = new CompositeDisposable();

    private RecyclerView recyclerView;

    private TextView iobTotal;
    private TextView activityTotal;
    private Button deleteFutureTreatments;

    public class RecyclerViewAdapter extends RecyclerView.Adapter<RecyclerViewAdapter.TreatmentsViewHolder> {

        List<Treatment> treatments;

        RecyclerViewAdapter(List<Treatment> treatments) {
            this.treatments = treatments;
        }

        @NonNull
        @Override
        public TreatmentsViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
            View v = LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.treatments_bolus_item, viewGroup, false);
            return new TreatmentsViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull TreatmentsViewHolder holder, int position) {
            Profile profile = ProfileFunctions.getInstance().getProfile();
            if (profile == null)
                return;
            Treatment t = treatments.get(position);
            holder.date.setText(DateUtil.dateAndTimeString(t.date));
            holder.insulin.setText(MainApp.gs(R.string.formatinsulinunits, t.insulin));
            holder.carbs.setText(MainApp.gs(R.string.format_carbs, (int) t.carbs));
            Iob iob = t.iobCalc(System.currentTimeMillis(), profile.getDia());
            holder.iob.setText(MainApp.gs(R.string.formatinsulinunits, iob.iobContrib));
            holder.mealOrCorrection.setText(t.isSMB ? "SMB" : t.mealBolus ? MainApp.gs(R.string.mealbolus) : MainApp.gs(R.string.correctionbous));
            holder.ph.setVisibility(t.source == Source.PUMP ? View.VISIBLE : View.GONE);
            holder.ns.setVisibility(NSUpload.isIdValid(t._id) ? View.VISIBLE : View.GONE);
            holder.invalid.setVisibility(t.isValid ? View.GONE : View.VISIBLE);
            if (iob.iobContrib != 0)
                holder.iob.setTextColor(ContextCompat.getColor(MainApp.instance(), R.color.colorActive));
            else
                holder.iob.setTextColor(holder.carbs.getCurrentTextColor());
            if (t.date > now())
                holder.date.setTextColor(ContextCompat.getColor(MainApp.instance(), R.color.colorScheduled));
            else
                holder.date.setTextColor(holder.carbs.getCurrentTextColor());
            holder.remove.setTag(t);
            holder.calculation.setTag(t);
            holder.calculation.setVisibility(t.getBoluscalc() == null ? View.INVISIBLE : View.VISIBLE);
        }

        @Override
        public int getItemCount() {
            return treatments.size();
        }

        @Override
        public void onAttachedToRecyclerView(@NonNull RecyclerView recyclerView) {
            super.onAttachedToRecyclerView(recyclerView);
        }

        public class TreatmentsViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
            CardView cv;
            TextView date;
            TextView insulin;
            TextView carbs;
            TextView iob;
            TextView mealOrCorrection;
            TextView remove;
            TextView calculation;
            TextView ph;
            TextView ns;
            TextView invalid;

            TreatmentsViewHolder(View itemView) {
                super(itemView);
                cv = itemView.findViewById(R.id.treatments_cardview);
                date = itemView.findViewById(R.id.treatments_date);
                insulin = itemView.findViewById(R.id.treatments_insulin);
                carbs = itemView.findViewById(R.id.treatments_carbs);
                iob = itemView.findViewById(R.id.treatments_iob);
                mealOrCorrection = itemView.findViewById(R.id.treatments_mealorcorrection);
                ph = itemView.findViewById(R.id.pump_sign);
                ns = itemView.findViewById(R.id.ns_sign);
                invalid = itemView.findViewById(R.id.invalid_sign);
                calculation = itemView.findViewById(R.id.treatments_calculation);
                calculation.setOnClickListener(this);
                calculation.setPaintFlags(calculation.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
                remove = itemView.findViewById(R.id.treatments_remove);
                remove.setOnClickListener(this);
                remove.setPaintFlags(remove.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
            }

            @Override
            public void onClick(View v) {
                final Treatment treatment = (Treatment) v.getTag();
                if (treatment == null)
                    return;
                switch (v.getId()) {
                    case R.id.treatments_remove:
                        OKDialog.showConfirmation(getContext(), MainApp.gs(R.string.removerecord),
                                MainApp.gs(R.string.configbuilder_insulin) + ": " + MainApp.gs(R.string.formatinsulinunits, treatment.insulin) +
                                        "\n" + MainApp.gs(R.string.carbs) + ": " + MainApp.gs(R.string.format_carbs, (int) treatment.carbs) +
                                        "\n" + MainApp.gs(R.string.date) + ": " + DateUtil.dateAndTimeString(treatment.date),
                                (dialog, id) -> {
                                    final String _id = treatment._id;
                                    if (treatment.source == Source.PUMP) {
                                        treatment.isValid = false;
                                        TreatmentsPlugin.getPlugin().getService().update(treatment);
                                    } else {
                                        if (NSUpload.isIdValid(_id)) {
                                            NSUpload.removeCareportalEntryFromNS(_id);
                                        } else {
                                            UploadQueue.removeID("dbAdd", _id);
                                        }
                                        TreatmentsPlugin.getPlugin().getService().delete(treatment);
                                    }
                                    updateGui();
                                }, null);
                        break;
                    case R.id.treatments_calculation:
                        FragmentManager manager = getFragmentManager();
                        // try to fix  https://fabric.io/nightscout3/android/apps/info.nightscout.androidaps/issues/5aca7a1536c7b23527eb4be7?time=last-seven-days
                        // https://stackoverflow.com/questions/14860239/checking-if-state-is-saved-before-committing-a-fragmenttransaction
                        if (manager.isStateSaved())
                            return;
                        if (treatment.getBoluscalc() != null) {
                            WizardInfoDialog wizardDialog = new WizardInfoDialog();
                            wizardDialog.setData(treatment.getBoluscalc());
                            wizardDialog.show(manager, "WizardInfoDialog");
                        }
                        break;
                }
            }
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.treatments_bolus_fragment, container, false);

        recyclerView = view.findViewById(R.id.treatments_recyclerview);
        recyclerView.setHasFixedSize(true);
        LinearLayoutManager llm = new LinearLayoutManager(view.getContext());
        recyclerView.setLayoutManager(llm);

        RecyclerViewAdapter adapter = new RecyclerViewAdapter(TreatmentsPlugin.getPlugin().getTreatmentsFromHistory());
        recyclerView.setAdapter(adapter);

        iobTotal = view.findViewById(R.id.treatments_iobtotal);
        activityTotal = view.findViewById(R.id.treatments_iobactivitytotal);

        Button refreshFromNS = view.findViewById(R.id.treatments_reshreshfromnightscout);
        refreshFromNS.setOnClickListener(v -> OKDialog.showConfirmation(getContext(), MainApp.gs(R.string.refresheventsfromnightscout) + "?", () -> {
            TreatmentsPlugin.getPlugin().getService().resetTreatments();
            RxBus.INSTANCE.send(new EventNSClientRestart());
        }));

        deleteFutureTreatments = view.findViewById(R.id.treatments_delete_future_treatments);
        deleteFutureTreatments.setOnClickListener(v -> {
            OKDialog.showConfirmation(getContext(), MainApp.gs(R.string.overview_treatment_label), MainApp.gs(R.string.deletefuturetreatments) + "?", () -> {
                final List<Treatment> futureTreatments = TreatmentsPlugin.getPlugin().getService()
                        .getTreatmentDataFromTime(now() + 1000, true);
                for (Treatment treatment : futureTreatments) {
                    final String _id = treatment._id;
                    if (NSUpload.isIdValid(_id)) {
                        NSUpload.removeCareportalEntryFromNS(_id);
                    } else {
                        UploadQueue.removeID("dbAdd", _id);
                    }
                    TreatmentsPlugin.getPlugin().getService().delete(treatment);
                }
                updateGui();
            });
        });

        boolean nsUploadOnly = SP.getBoolean(R.string.key_ns_upload_only, true);
        if (nsUploadOnly)
            refreshFromNS.setVisibility(View.GONE);

        return view;
    }

    @Override
    public synchronized void onResume() {
        super.onResume();
        disposable.add(RxBus.INSTANCE
                .toObservable(EventTreatmentChange.class)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(event -> updateGui(), FabricPrivacy::logException)
        );
        disposable.add(RxBus.INSTANCE
                .toObservable(EventAutosensCalculationFinished.class)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(event -> updateGui(), FabricPrivacy::logException)
        );
        updateGui();
    }

    @Override
    public synchronized void onPause() {
        super.onPause();
        disposable.clear();
    }

    private void updateGui() {
        recyclerView.swapAdapter(new RecyclerViewAdapter(TreatmentsPlugin.getPlugin().getTreatmentsFromHistory()), false);
        if (TreatmentsPlugin.getPlugin().getLastCalculationTreatments() != null) {
            iobTotal.setText(DecimalFormatter.to2Decimal(TreatmentsPlugin.getPlugin().getLastCalculationTreatments().iob) + " " + MainApp.gs(R.string.insulin_unit_shortname));
            activityTotal.setText(DecimalFormatter.to3Decimal(TreatmentsPlugin.getPlugin().getLastCalculationTreatments().activity) + " " + MainApp.gs(R.string.insulin_unit_shortname));
        }
        if (!TreatmentsPlugin.getPlugin().getService().getTreatmentDataFromTime(now() + 1000, true).isEmpty()) {
            deleteFutureTreatments.setVisibility(View.VISIBLE);
        } else {
            deleteFutureTreatments.setVisibility(View.GONE);
        }
    }
}
