package info.nightscout.androidaps.plugins.source;

import android.graphics.Paint;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.db.BgReading;
import info.nightscout.androidaps.plugins.bus.RxBus;
import info.nightscout.androidaps.plugins.configBuilder.ProfileFunctions;
import info.nightscout.androidaps.plugins.general.nsclient.NSUpload;
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.events.EventAutosensCalculationFinished;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.FabricPrivacy;
import info.nightscout.androidaps.utils.OKDialog;
import info.nightscout.androidaps.utils.T;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;

/**
 * Created by mike on 16.10.2017.
 */

public class BGSourceFragment extends Fragment {
    private CompositeDisposable disposable = new CompositeDisposable();
    private RecyclerView recyclerView;

    private final long MILLS_TO_THE_PAST = T.hours(12).msecs();

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        try {
            View view = inflater.inflate(R.layout.bgsource_fragment, container, false);

            recyclerView = view.findViewById(R.id.bgsource_recyclerview);
            recyclerView.setHasFixedSize(true);
            LinearLayoutManager llm = new LinearLayoutManager(view.getContext());
            recyclerView.setLayoutManager(llm);

            long now = System.currentTimeMillis();
            RecyclerViewAdapter adapter = new RecyclerViewAdapter(MainApp.getDbHelper().getAllBgreadingsDataFromTime(now - MILLS_TO_THE_PAST, false));
            recyclerView.setAdapter(adapter);

            return view;
        } catch (Exception e) {
            FabricPrivacy.logException(e);
        }

        return null;
    }

    @Override
    public synchronized void onResume() {
        super.onResume();
        disposable.add(RxBus.INSTANCE
                .toObservable(EventAutosensCalculationFinished.class)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(event -> updateGUI(), FabricPrivacy::logException)
        );
    }

    @Override
    public synchronized void onPause() {
        disposable.clear();
        super.onPause();
    }

    protected void updateGUI() {
        long now = System.currentTimeMillis();
        recyclerView.swapAdapter(new RecyclerViewAdapter(MainApp.getDbHelper().getAllBgreadingsDataFromTime(now - MILLS_TO_THE_PAST, false)), true);
    }

    public class RecyclerViewAdapter extends RecyclerView.Adapter<RecyclerViewAdapter.BgReadingsViewHolder> {

        List<BgReading> bgReadings;

        RecyclerViewAdapter(List<BgReading> bgReadings) {
            this.bgReadings = bgReadings;
        }

        @NonNull
        @Override
        public BgReadingsViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
            View v = LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.bgsource_item, viewGroup, false);
            return new BgReadingsViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull BgReadingsViewHolder holder, int position) {
            BgReading bgReading = bgReadings.get(position);
            holder.ns.setVisibility(NSUpload.isIdValid(bgReading._id) ? View.VISIBLE : View.GONE);
            holder.invalid.setVisibility(!bgReading.isValid ? View.VISIBLE : View.GONE);
            holder.date.setText(DateUtil.dateAndTimeString(bgReading.date));
            holder.value.setText(bgReading.valueToUnitsToString(ProfileFunctions.getSystemUnits()));
            holder.direction.setText(bgReading.directionToSymbol());
            holder.remove.setTag(bgReading);
        }

        @Override
        public int getItemCount() {
            return bgReadings.size();
        }

        class BgReadingsViewHolder extends RecyclerView.ViewHolder  {
            TextView date;
            TextView value;
            TextView direction;
            TextView invalid;
            TextView ns;
            TextView remove;

            BgReadingsViewHolder(View itemView) {
                super(itemView);
                date = itemView.findViewById(R.id.bgsource_date);
                value = itemView.findViewById(R.id.bgsource_value);
                direction = itemView.findViewById(R.id.bgsource_direction);
                invalid = itemView.findViewById(R.id.invalid_sign);
                ns = itemView.findViewById(R.id.ns_sign);
                remove = itemView.findViewById(R.id.bgsource_remove);
                remove.setOnClickListener(v -> {
                    final BgReading bgReading = (BgReading) v.getTag();
                    OKDialog.showConfirmation(getContext(), MainApp.gs(R.string.removerecord) + "\n" + DateUtil.dateAndTimeString(bgReading.date) + "\n" + bgReading.valueToUnitsToString(ProfileFunctions.getSystemUnits()), () -> {
                        bgReading.isValid = false;
                        MainApp.getDbHelper().update(bgReading);
                        updateGUI();
                    });
                });
                remove.setPaintFlags(remove.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
            }
        }
    }
}
