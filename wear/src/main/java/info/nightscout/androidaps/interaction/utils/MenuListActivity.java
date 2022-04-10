package info.nightscout.androidaps.interaction.utils;

import android.app.Activity;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;
import androidx.wear.widget.CurvedTextView;
import androidx.wear.widget.WearableLinearLayoutManager;
import androidx.wear.widget.WearableRecyclerView;

import java.util.List;

import info.nightscout.androidaps.R;

/**
 * Created by adrian on 08/02/17.
 */

public abstract class MenuListActivity extends Activity {
    List<MenuItem> elements;

    protected abstract List<MenuItem> getElements();

    protected abstract void doAction(String position);

    public interface AdapterCallback {
        void onItemClicked(MenuAdapter.ItemViewHolder v);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.actions_list_activity);
        setTitleBasedOnScreenShape(String.valueOf(getTitle()));

        elements = getElements();
        CustomScrollingLayoutCallback customScrollingLayoutCallback = new CustomScrollingLayoutCallback();
        WearableLinearLayoutManager layoutManager = new WearableLinearLayoutManager(this);
        WearableRecyclerView listView = findViewById(R.id.action_list);
        boolean isScreenRound = this.getResources().getConfiguration().isScreenRound();
        if (isScreenRound) {
            layoutManager.setLayoutCallback(customScrollingLayoutCallback);
            listView.setEdgeItemsCenteringEnabled(true);
        } else {
            // Bug in androidx.wear:wear:1.2.0 
            // WearableRecyclerView setEdgeItemsCenteringEnabled requires fix for square screen
            listView.setPadding(0, 50, 0, 0);
        }
        listView.setHasFixedSize(true);
        listView.setLayoutManager(layoutManager);
        listView.setAdapter(new MenuAdapter(elements, v -> {
            String tag = (String) v.itemView.getTag();
            doAction(tag);
        }));
    }

    private void setTitleBasedOnScreenShape(String title) {
        CurvedTextView titleViewCurved = findViewById(R.id.title_curved);
        TextView titleView = findViewById(R.id.title);
        if (this.getResources().getConfiguration().isScreenRound()) {
            titleViewCurved.setText(title);
            titleViewCurved.setVisibility(View.VISIBLE);
            titleView.setVisibility((View.GONE));
        } else {
            titleView.setText(title);
            titleView.setVisibility(View.VISIBLE);
            titleViewCurved.setVisibility((View.GONE));
        }
    }

    private static class MenuAdapter extends RecyclerView.Adapter<MenuAdapter.ItemViewHolder> {
        private final List<MenuItem> mDataset;
        private final AdapterCallback callback;

        public MenuAdapter(List<MenuItem> dataset, AdapterCallback callback) {
            mDataset = dataset;
            this.callback = callback;
        }

        public static class ItemViewHolder extends RecyclerView.ViewHolder {
            protected final RelativeLayout menuContainer;
            protected final TextView actionItem;
            protected final ImageView actionIcon;

            public ItemViewHolder(View itemView) {
                super(itemView);
                menuContainer = itemView.findViewById(R.id.menu_container);
                actionItem = itemView.findViewById(R.id.menuItemText);
                actionIcon = itemView.findViewById(R.id.menuItemIcon);
            }
        }

        @Override
        public ItemViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.list_item, parent, false);

            return new ItemViewHolder(view);
        }

        @Override
        public void onBindViewHolder(ItemViewHolder holder, final int position) {
            MenuItem item = mDataset.get(position);
            holder.actionItem.setText(item.actionItem);
            holder.actionIcon.setImageResource(item.actionIcon);
            holder.itemView.setTag(item.actionItem);
            holder.menuContainer.setOnClickListener(v -> callback.onItemClicked(holder));
        }

        @Override
        public int getItemCount() {
            return mDataset.size();
        }
    }

    protected static class MenuItem {
        public MenuItem(int actionIcon, String actionItem) {
            this.actionIcon = actionIcon;
            this.actionItem = actionItem;
        }

        public int actionIcon;
        public String actionItem;
    }

    public static class CustomScrollingLayoutCallback extends WearableLinearLayoutManager.LayoutCallback {
        // How much should we scale the icon at most.
        private static final float MAX_ICON_PROGRESS = 0.65f;

        @Override
        public void onLayoutFinished(View child, RecyclerView parent) {
            // Figure out % progress from top to bottom
            float centerOffset = ((float) child.getHeight() / 2.0f) / (float) parent.getHeight();
            float yRelativeToCenterOffset = (child.getY() / parent.getHeight()) + centerOffset;

            // Normalize for center
            float progressToCenter = Math.abs(0.5f - yRelativeToCenterOffset);
            // Adjust to the maximum scale
            progressToCenter = Math.min(progressToCenter, MAX_ICON_PROGRESS);

            child.setScaleX(1 - progressToCenter);
            child.setScaleY(1 - progressToCenter);
        }
    }

}
