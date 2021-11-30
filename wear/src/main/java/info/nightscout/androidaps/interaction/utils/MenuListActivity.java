package info.nightscout.androidaps.interaction.utils;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.support.wearable.view.WearableListView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.List;

import info.nightscout.androidaps.R;

/**
 * Created by adrian on 08/02/17.
 */

public abstract class MenuListActivity extends Activity
        implements WearableListView.ClickListener {

    List<MenuElement> elements;

    protected abstract List<MenuElement> getElements();

    protected abstract void doAction(String position);

    @Override
    protected void onPause(){
        super.onPause();
        finish();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        elements = getElements();
        setContentView(R.layout.actions_list_activity);

        // Get the list component from the layout of the activity
        WearableListView listView =
                findViewById(R.id.wearable_list);

        // Assign an adapter to the list
        listView.setAdapter(new MenuAdapter(this, elements));

        // Set a click listener
        listView.setClickListener(this);
    }

    // WearableListView click listener
    @Override
    public void onClick(WearableListView.ViewHolder v) {
        String tag = (String) v.itemView.getTag();
        doAction(tag);
        //ActionsDefinitions.doAction(v.getAdapterPosition(), this);
        finish();
    }

    @Override
    public void onTopEmptyRegionClick() {
    }


    private class MenuAdapter extends WearableListView.Adapter {
        private final List<MenuElement> mDataset;
        private final Context mContext;
        private final LayoutInflater mInflater;

        // Provide a suitable constructor (depends on the kind of dataset)
        public MenuAdapter(Context context, List<MenuElement> dataset) {
            mContext = context;
            mInflater = LayoutInflater.from(context);
            mDataset = dataset;
        }

        // Provide a reference to the type of views you're using
        public class ItemViewHolder extends WearableListView.ViewHolder {
            private final TextView textView;

            public ItemViewHolder(View itemView) {
                super(itemView);
                // find the text view within the custom item's layout
                textView = itemView.findViewById(R.id.actionitem);
            }
        }

        // Create new views for list items
        // (invoked by the WearableListView's layout manager)
        @Override
        public WearableListView.ViewHolder onCreateViewHolder(ViewGroup parent,
                                                              int viewType) {
            // Inflate our custom layout for list items
            WearableListView.ViewHolder viewHolder = new WearableListView.ViewHolder(new MenuItemView(mContext));
            return viewHolder;
        }

        // Replace the contents of a list item
        // Instead of creating new views, the list tries to recycle existing ones
        // (invoked by the WearableListView's layout manager)
        @Override
        public void onBindViewHolder(WearableListView.ViewHolder holder,
                                     int position) {
            // retrieve the text view
            MenuItemView menuItemView = (MenuItemView) holder.itemView;
            final MenuElement item = mDataset.get(position);
            TextView textView = menuItemView.findViewById(R.id.actionitem);
            textView.setText(item.actionitem);

            final ImageView imageView = menuItemView.findViewById(R.id.actionicon);
            imageView.setImageResource(item.actionicon);

            // replace list item's metadata
            holder.itemView.setTag(item.actionitem);
        }

        // Return the size of your dataset
        // (invoked by the WearableListView's layout manager)
        @Override
        public int getItemCount() {
            return mDataset.size();
        }
    }

    public final class MenuItemView extends FrameLayout implements WearableListView.OnCenterProximityListener {

        final ImageView image;
        final TextView text;
        final float scale = 0.8f;
        final float alpha = 0.8f;

        public MenuItemView(Context context) {
            super(context);
            View.inflate(context, R.layout.list_item, this);
            image = findViewById(R.id.actionicon);
            text = findViewById(R.id.actionitem);
            image.setScaleX(scale);
            image.setScaleX(scale);
            image.setAlpha(alpha);
            text.setScaleX(scale);
            text.setScaleX(scale);
            text.setAlpha(alpha);
        }

        @Override
        public void onCenterPosition(boolean b) {
            // Animation  to be ran when the view becomes the centered one
            image.animate().scaleX(1f).scaleY(1f).alpha(1);
            text.animate().scaleX(1f).scaleY(1f).alpha(1);
        }

        @Override
        public void onNonCenterPosition(boolean b) {
            //Animation to be ran when the view is not the centered one anymore
            image.animate().scaleX(scale).scaleY(scale).alpha(alpha);
            text.animate().scaleX(scale).scaleY(scale).alpha(alpha);
        }
    }

    protected class MenuElement {
        public MenuElement(int actionicon, String actionitem) {
            this.actionicon = actionicon;
            this.actionitem = actionitem;
        }
        public int actionicon;
        public String actionitem;
    }

}
