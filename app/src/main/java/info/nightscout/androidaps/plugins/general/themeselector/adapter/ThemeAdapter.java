package info.nightscout.androidaps.plugins.general.themeselector.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import javax.inject.Inject;

import info.nightscout.androidaps.MainActivity;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.plugins.general.themeselector.ScrollingActivity;
import info.nightscout.androidaps.plugins.general.themeselector.model.Theme;
import info.nightscout.androidaps.plugins.general.themeselector.view.ThemeView;
import info.nightscout.androidaps.utils.sharedPreferences.SP;

import static info.nightscout.androidaps.plugins.general.themeselector.util.ThemeUtil.THEME_DARKSIDE;

public class ThemeAdapter extends RecyclerView.Adapter<ThemeAdapter.MyViewHolder> {

    @Inject SP sp;

    private List<Theme> themeList;
    private RecyclerViewClickListener mRecyclerViewClickListener;
 
    public class MyViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
        public ThemeView themeView;
        private RecyclerViewClickListener mListener;
 
        public MyViewHolder(View view, RecyclerViewClickListener listener) {
            super(view);
            mListener = listener;
            themeView = view.findViewById(R.id.themeView);
            view.setOnClickListener(this);
        }

        @Override
        public void onClick(View view) {
            mListener.onClick(view, getAdapterPosition());
            ScrollingActivity.selectedTheme = getAdapterPosition();
            sp.putInt("theme", ScrollingActivity.mThemeList.get(getAdapterPosition()).getId());
            themeView.setActivated(true);
            ThemeAdapter.this.notifyDataSetChanged();
        }
    }
 
 
    public ThemeAdapter(List<Theme> themeList, RecyclerViewClickListener recyclerViewClickListener) {
        this.themeList = themeList;
        mRecyclerViewClickListener = recyclerViewClickListener;
    }
 
    @Override
    public MyViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.themeselector_list_row_theme, parent, false);
 
        return new MyViewHolder(itemView, mRecyclerViewClickListener);
    }
 
    @Override
    public void onBindViewHolder(final MyViewHolder holder, final int position) {
        Theme theme = themeList.get(position);
        holder.themeView.setTheme(theme);

        if(ScrollingActivity.selectedTheme == position){
            holder.themeView.setActivated(true);
        }else {
            holder.themeView.setActivated(false);
        }
    }
 
    @Override
    public int getItemCount() {
        return themeList.size();
    }
}