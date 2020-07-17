package info.nightscout.androidaps.plugins.general.themeselector;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.SwitchCompat;
import androidx.cardview.widget.CardView;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.skydoves.colorpickerview.ColorEnvelope;
import com.skydoves.colorpickerview.ColorPickerDialog;
import com.skydoves.colorpickerview.ColorPickerView;
import com.skydoves.colorpickerview.listeners.ColorEnvelopeListener;

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

;

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
    TextView selectdarkcolor ;
    TextView selectlightcolor ;
    CardView cardView;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.themeselector_scrolling_fragment);
        initBottomSheet();
        prepareThemeData();

        actualTheme = sp.getInt("theme", THEME_DARKSIDE);
        ThemeView themeView = findViewById(R.id.theme_selected);
        themeView.setTheme(mThemeList.get(actualTheme), actualTheme);
    }

    private void initBottomSheet(){

        int theme = sp.getInt("theme", THEME_DARKSIDE);
        boolean nightMode = sp.getBoolean("daynight", true);
        // get the bottom sheet view
        LinearLayout llBottomSheet = (LinearLayout) findViewById(R.id.bottom_sheet);

        cardView =  findViewById(R.id.select_backgroundcolor);

        // init the bottom sheet behavior
        mBottomSheetBehavior = BottomSheetBehavior.from(llBottomSheet);
        boolean backGround = sp.getBoolean("backgroundcolor", true);

        if(backGround == true)
            cardView.setVisibility(View.GONE);
        else
            cardView.setVisibility(View.VISIBLE);

        SwitchCompat switchCompatBackground = findViewById(R.id.switch_backgroundimage);
        switchCompatBackground.setChecked(backGround);
        switchCompatBackground.setOnCheckedChangeListener((compoundButton, b) -> {

            sp.putBoolean("backgroundcolor", b);

            if(b == true)
                cardView.setVisibility(View.GONE);
            else
                cardView.setVisibility(View.VISIBLE);

            int delayTime = 200;
            compoundButton.postDelayed(new Runnable() {
                @Override
                public void run() {
                    changeTheme(sp.getInt("theme", THEME_DARKSIDE));
                }
            },delayTime);
        });

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
                    changeTheme(sp.getInt("theme", THEME_DARKSIDE));
                }
            },delayTime);

        });

        selectdarkcolor = findViewById(R.id.select_backgroundcolordark);
        selectlightcolor = findViewById(R.id.select_backgroundcolorlight);

        selectdarkcolor.setBackgroundColor(sp.getInt("darkBackgroundColor", ContextCompat.getColor(this, info.nightscout.androidaps.core.R.color.background_dark)));
        selectlightcolor.setBackgroundColor( sp.getInt("lightBackgroundColor", ContextCompat.getColor(this, info.nightscout.androidaps.core.R.color.background_light)));

        selectdarkcolor.setOnClickListener(v -> {
            selectColor("dark");
        });

        selectlightcolor.setOnClickListener(v -> {
            selectColor("light");
        });

        mRecyclerView = findViewById(R.id.recyclerView);

        mAdapter = new ThemeAdapter( sp, mThemeList, new RecyclerViewClickListener() {
            @Override
            public void onClick(View view, int position) {
                mBottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                view.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        ThemeView themeView = findViewById(R.id.theme_selected);
                        themeView.setTheme(mThemeList.get(selectedTheme), ThemeUtil.getThemeId(selectedTheme));
                        changeTheme(selectedTheme);
                    }
                },500);
            }
        });

        RecyclerView.LayoutManager mLayoutManager = new GridLayoutManager(getApplicationContext(),3);
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
        }
    }

    public void selectColor(String lightOrDark) {
        new ColorPickerDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setTitle("Select Background Color")
                .setPreferenceName("MyColorPickerDialog")
                .setPositiveButton(getString(R.string.confirm),
                        new ColorEnvelopeListener() {
                            @Override
                            public void onColorSelected(ColorEnvelope envelope, boolean fromUser) {
                                //setLayoutColor(envelope);
                                if(lightOrDark == "light") {
                                    sp.putInt("lightBackgroundColor", envelope.getColor());
                                    selectlightcolor.setBackgroundColor(envelope.getColor());
                                    int delayTime = 200;
                                    selectlightcolor.postDelayed(new Runnable() {
                                        @Override
                                        public void run() {
                                            changeTheme(sp.getInt("theme", THEME_DARKSIDE));
                                        }
                                    },delayTime);
                                } else if (lightOrDark == "dark") {
                                    sp.putInt("darkBackgroundColor", envelope.getColor());
                                    selectdarkcolor.setBackgroundColor(envelope.getColor());
                                    int delayTime = 200;
                                    selectdarkcolor.postDelayed(new Runnable() {
                                        @Override
                                        public void run() {
                                            changeTheme(sp.getInt("theme", THEME_DARKSIDE));
                                        }
                                    },delayTime);
                                }

                            }
                        })
                .setNegativeButton(getString(R.string.cancel),
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                dialogInterface.dismiss();
                            }
                        })
                .attachAlphaSlideBar(false) // default is true. If false, do not show the AlphaSlideBar.
                .attachBrightnessSlideBar(true)  // default is true. If false, do not show the BrightnessSlideBar.
                .setBottomSpace(12) // set bottom space between the last slidebar and buttons.
                .show();

    }

}
