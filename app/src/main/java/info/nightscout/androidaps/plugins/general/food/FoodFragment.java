package info.nightscout.androidaps.plugins.general.food;

import android.app.Activity;
import android.content.DialogInterface;
import android.graphics.Paint;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import com.squareup.otto.Subscribe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.events.EventFoodDatabaseChanged;
import info.nightscout.androidaps.plugins.common.SubscriberFragment;
import info.nightscout.androidaps.utils.FabricPrivacy;
import info.nightscout.androidaps.plugins.general.nsclient.NSUpload;
import info.nightscout.androidaps.utils.SpinnerHelper;

/**
 * Created by mike on 16.10.2017.
 */

public class FoodFragment extends SubscriberFragment {
    private static Logger log = LoggerFactory.getLogger(FoodFragment.class);

    EditText filter;
    ImageView clearFilter;
    SpinnerHelper category;
    SpinnerHelper subcategory;
    RecyclerView recyclerView;

    List<Food> unfiltered;
    List<Food> filtered;
    ArrayList<CharSequence> categories;
    ArrayList<CharSequence> subcategories;

    final String EMPTY = MainApp.gs(R.string.none);

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        try {
            View view = inflater.inflate(R.layout.food_fragment, container, false);
            filter = (EditText) view.findViewById(R.id.food_filter);
            clearFilter = (ImageView) view.findViewById(R.id.food_clearfilter);
            category = new SpinnerHelper(view.findViewById(R.id.food_category));
            subcategory = new SpinnerHelper(view.findViewById(R.id.food_subcategory));
            recyclerView = (RecyclerView) view.findViewById(R.id.food_recyclerview);
            recyclerView.setHasFixedSize(true);
            LinearLayoutManager llm = new LinearLayoutManager(view.getContext());
            recyclerView.setLayoutManager(llm);

            clearFilter.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    filter.setText("");
                    category.setSelection(0);
                    subcategory.setSelection(0);
                    filterData();
                }
            });

            category.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    fillSubcategories();
                    filterData();
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {
                    fillSubcategories();
                    filterData();
                }
            });

            subcategory.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    filterData();
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {
                    filterData();
                }
            });

            filter.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    filterData();
                }

                @Override
                public void afterTextChanged(Editable s) {
                }
            });

            RecyclerViewAdapter adapter = new RecyclerViewAdapter(MainApp
                    .getSpecificPlugin(FoodPlugin.class).getService().getFoodData());
            recyclerView.setAdapter(adapter);

            loadData();
            fillCategories();
            fillSubcategories();
            filterData();
            return view;
        } catch (Exception e) {
            FabricPrivacy.logException(e);
        }

        return null;
    }

    @Subscribe
    @SuppressWarnings("unused")
    public void onStatusEvent(final EventFoodDatabaseChanged ev) {
        loadData();
        filterData();
    }

    void loadData() {
        unfiltered = MainApp.getSpecificPlugin(FoodPlugin.class).getService().getFoodData();
    }

    void fillCategories() {
        Set<CharSequence> catSet = new HashSet<>();

        for (Food f : unfiltered) {
            if (f.category != null && !f.category.equals(""))
                catSet.add(f.category);
        }

        // make it unique
        categories = new ArrayList<>(catSet);
        categories.add(0, MainApp.gs(R.string.none));

        ArrayAdapter<CharSequence> adapterCategories = new ArrayAdapter<>(getContext(),
                R.layout.spinner_centered, categories);
        category.setAdapter(adapterCategories);
    }

    void fillSubcategories() {
        String categoryFilter = category.getSelectedItem().toString();

        Set<CharSequence> subCatSet = new HashSet<>();

        if (!categoryFilter.equals(EMPTY)) {
            for (Food f : unfiltered) {
                if (f.category != null && f.category.equals(categoryFilter))
                    if (f.subcategory != null && !f.subcategory.equals(""))
                        subCatSet.add(f.subcategory);
            }
        }

        // make it unique
        subcategories = new ArrayList<>(subCatSet);
        subcategories.add(0, MainApp.gs(R.string.none));

        ArrayAdapter<CharSequence> adapterSubcategories = new ArrayAdapter<>(getContext(),
                R.layout.spinner_centered, subcategories);
        subcategory.setAdapter(adapterSubcategories);
    }

    void filterData() {
        String textFilter = filter.getText().toString();
        String categoryFilter = category.getSelectedItem().toString();
        String subcategoryFilter = subcategory.getSelectedItem().toString();

        filtered = new ArrayList<>();

        for (Food f : unfiltered) {
            if (f.name == null || f.category == null || f.subcategory == null)
                continue;

            if (!subcategoryFilter.equals(EMPTY) && !f.subcategory.equals(subcategoryFilter))
                continue;
            if (!categoryFilter.equals(EMPTY) && !f.category.equals(categoryFilter))
                continue;
            if (!textFilter.equals("") && !f.name.toLowerCase().contains(textFilter.toLowerCase()))
                continue;
            filtered.add(f);
        }

        updateGUI();
    }

    @Override
    protected void updateGUI() {
        Activity activity = getActivity();
        if (activity != null)
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    recyclerView.swapAdapter(new FoodFragment.RecyclerViewAdapter(filtered), true);
                }
            });
    }

    public class RecyclerViewAdapter extends RecyclerView.Adapter<RecyclerViewAdapter.FoodsViewHolder> {

        List<Food> foodList;

        RecyclerViewAdapter(List<Food> foodList) {
            this.foodList = foodList;
        }

        @Override
        public FoodsViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
            View v = LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.food_item, viewGroup, false);
            return new FoodsViewHolder(v);
        }

        @Override
        public void onBindViewHolder(FoodsViewHolder holder, int position) {
            Food food = foodList.get(position);
            holder.ns.setVisibility(food._id != null ? View.VISIBLE : View.GONE);
            holder.name.setText(food.name);
            holder.portion.setText(food.portion + food.units);
            holder.carbs.setText(food.carbs + MainApp.gs(R.string.shortgramm));
            holder.fat.setText(MainApp.gs(R.string.shortfat) + ": " + food.fat + MainApp.gs(R.string.shortgramm));
            if (food.fat == 0)
                holder.fat.setVisibility(View.INVISIBLE);
            holder.protein.setText(MainApp.gs(R.string.shortprotein) + ": " + food.protein + MainApp.gs(R.string.shortgramm));
            if (food.protein == 0)
                holder.protein.setVisibility(View.INVISIBLE);
            holder.energy.setText(MainApp.gs(R.string.shortenergy) + ": " + food.energy + MainApp.gs(R.string.shortkilojoul));
            if (food.energy == 0)
                holder.energy.setVisibility(View.INVISIBLE);
            holder.remove.setTag(food);
        }

        @Override
        public int getItemCount() {
            return foodList.size();
        }

        class FoodsViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
            TextView name;
            TextView portion;
            TextView carbs;
            TextView fat;
            TextView protein;
            TextView energy;
            TextView ns;
            TextView remove;

            FoodsViewHolder(View itemView) {
                super(itemView);
                name = (TextView) itemView.findViewById(R.id.food_name);
                portion = (TextView) itemView.findViewById(R.id.food_portion);
                carbs = (TextView) itemView.findViewById(R.id.food_carbs);
                fat = (TextView) itemView.findViewById(R.id.food_fat);
                protein = (TextView) itemView.findViewById(R.id.food_protein);
                energy = (TextView) itemView.findViewById(R.id.food_energy);
                ns = (TextView) itemView.findViewById(R.id.ns_sign);
                remove = (TextView) itemView.findViewById(R.id.food_remove);
                remove.setOnClickListener(this);
                remove.setPaintFlags(remove.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
            }

            @Override
            public void onClick(View v) {
                final Food food = (Food) v.getTag();
                switch (v.getId()) {

                    case R.id.food_remove:
                        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
                        builder.setTitle(MainApp.gs(R.string.confirmation));
                        builder.setMessage(MainApp.gs(R.string.removerecord) + "\n" + food.name);
                        builder.setPositiveButton(MainApp.gs(R.string.ok), new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                final String _id = food._id;
                                if (_id != null && !_id.equals("")) {
                                    NSUpload.removeFoodFromNS(_id);
                                }
                                MainApp.getSpecificPlugin(FoodPlugin.class).getService().delete(food);
                            }
                        });
                        builder.setNegativeButton(MainApp.gs(R.string.cancel), null);
                        builder.show();
                        break;

                }
            }
        }
    }

}
