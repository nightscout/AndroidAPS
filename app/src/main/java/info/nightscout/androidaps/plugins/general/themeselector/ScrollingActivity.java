package info.nightscout.androidaps.plugins.general.themeselector;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.SwitchCompat;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetBehavior;

import java.util.ArrayList;
import java.util.List;

import info.nightscout.androidaps.MainActivity;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.plugins.general.themeselector.adapter.RecyclerViewClickListener;
import info.nightscout.androidaps.plugins.general.themeselector.adapter.ThemeAdapter;
import info.nightscout.androidaps.plugins.general.themeselector.model.Theme;
import info.nightscout.androidaps.plugins.general.themeselector.util.ThemeUtil;
import info.nightscout.androidaps.plugins.general.themeselector.view.ThemeView;

import static info.nightscout.androidaps.plugins.general.themeselector.util.ThemeUtil.THEME_DARKSIDE;

public class ScrollingActivity  extends MainActivity implements View.OnClickListener{

    public static List<Theme> mThemeList = new ArrayList<>();
    public static int selectedTheme;
    static {
        selectedTheme = 0;
    }

    private int actualTheme;

    private RecyclerView mRecyclerView;
    private ThemeAdapter mAdapter;
    private BottomSheetBehavior mBottomSheetBehavior;
    private Button savebutton;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.themeselector_scrolling_fragment);
        initBottomSheet();
        prepareThemeData();

        actualTheme = sp.getInt("theme", THEME_DARKSIDE);
        ThemeView themeView = findViewById(R.id.theme_selected);
        themeView.setTheme(mThemeList.get(actualTheme));
    }

    private void initBottomSheet(){

        int theme = sp.getInt("theme", THEME_DARKSIDE);
        boolean nightMode = sp.getBoolean("daynight", true);
        // get the bottom sheet view
        LinearLayout llBottomSheet = (LinearLayout) findViewById(R.id.bottom_sheet);

        // init the bottom sheet behavior
        mBottomSheetBehavior = BottomSheetBehavior.from(llBottomSheet);

        SwitchCompat switchCompat = findViewById(R.id.switch_dark_mode);
        switchCompat.setChecked(nightMode);
        switchCompat.setOnCheckedChangeListener((compoundButton, b) -> {
            sp.putBoolean("daynight", b);
            int delayTime = 200;
            if(mBottomSheetBehavior.getState() == BottomSheetBehavior.STATE_EXPANDED){
                delayTime = 400;
                mBottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            }
            compoundButton.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if(b){
                        getDelegate().setLocalNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                    }else{
                        getDelegate().setLocalNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                    }
                    //changeTheme(selectedTheme);
                }
            },delayTime);

        });

        savebutton = findViewById(R.id.save_theme);
        savebutton.setOnClickListener(this);


        mRecyclerView = findViewById(R.id.recyclerView);

        mAdapter = new ThemeAdapter(mThemeList, new RecyclerViewClickListener() {
            @Override
            public void onClick(View view, int position) {
                mBottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                view.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        ThemeView themeView = findViewById(R.id.theme_selected);
                        sp.putInt("theme",selectedTheme );
                        themeView.setTheme(mThemeList.get(sp.getInt("theme", THEME_DARKSIDE)));
                        setTheme(selectedTheme);
                        ScrollingActivity.this.recreate();
                        changeTheme(selectedTheme);
                    }
                },500);
                //sp.putInt("theme",actualTheme );
            }
        });

        RecyclerView.LayoutManager mLayoutManager = new GridLayoutManager(getApplicationContext(),4);
        mRecyclerView.setLayoutManager(mLayoutManager);
        mRecyclerView.setItemAnimator(new DefaultItemAnimator());
        mRecyclerView.setAdapter(mAdapter);
    }

    private void prepareThemeData() {
        mThemeList.clear();
        mThemeList.addAll(ThemeUtil.getThemeList());
        mAdapter.notifyDataSetChanged();
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()){
            //case R.id.theme_selected :
            case R.id.fab:
                // change the state of the bottom sheet
                switch (mBottomSheetBehavior.getState()){
                    case BottomSheetBehavior.STATE_HIDDEN :
                        mBottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                        break;

                    case BottomSheetBehavior.STATE_COLLAPSED :
                        mBottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                        break;

                    case BottomSheetBehavior.STATE_EXPANDED :
                        mBottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
                        break;
                }
                break;
            case R.id.save_theme:
                view.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        //ScrollingActivity.this.recreate();
                        changeTheme(selectedTheme);
                    }
                },500);
                break;
        }
    }


}
