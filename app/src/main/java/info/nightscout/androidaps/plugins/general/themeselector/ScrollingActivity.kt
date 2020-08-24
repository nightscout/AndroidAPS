package info.nightscout.androidaps.plugins.general.themeselector

import android.R.attr
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.CompoundButton
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.skydoves.colorpickerview.ColorPickerDialog
import com.skydoves.colorpickerview.ColorPickerView
import com.skydoves.colorpickerview.listeners.ColorEnvelopeListener
import com.skydoves.colorpickerview.preference.ColorPickerPreferenceManager
import info.nightscout.androidaps.MainActivity
import info.nightscout.androidaps.R
import info.nightscout.androidaps.plugins.general.colorpicker.CustomFlag
import info.nightscout.androidaps.plugins.general.themeselector.adapter.RecyclerViewClickListener
import info.nightscout.androidaps.plugins.general.themeselector.adapter.ThemeAdapter
import info.nightscout.androidaps.plugins.general.themeselector.model.Theme
import info.nightscout.androidaps.plugins.general.themeselector.util.ThemeUtil.THEME_DARKSIDE
import info.nightscout.androidaps.plugins.general.themeselector.util.ThemeUtil.getThemeId
import info.nightscout.androidaps.plugins.general.themeselector.util.ThemeUtil.themeList
import info.nightscout.androidaps.plugins.general.themeselector.view.ThemeView
import kotlinx.android.synthetic.main.colorpicker_flagview.*
import kotlinx.android.synthetic.main.themeselector_bottom_sheet.*
import kotlinx.android.synthetic.main.themeselector_scrolling_fragment.*
import java.util.*

class ScrollingActivity : MainActivity(), View.OnClickListener {
    companion object {
        var mThemeList: MutableList<Theme> = ArrayList()
        var selectedTheme = 0

        init {
            selectedTheme = 0
        }
    }

    private var actualTheme = 0
    private var mAdapter: ThemeAdapter? = null
    private var mBottomSheetBehavior: BottomSheetBehavior<*>? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.themeselector_scrolling_fragment)
        initBottomSheet()
        prepareThemeData()
        actualTheme = sp.getInt("theme", THEME_DARKSIDE)
        val themeView = findViewById<ThemeView>(R.id.theme_selected)
        themeView.setTheme(mThemeList[actualTheme], actualTheme)
        setBackground()
    }

    private fun setBackground() {
        // get theme attribute
        val a = TypedValue()
        val drawable: Drawable
        theme.resolveAttribute(attr.windowBackground, a, true)
        if (a.type >= TypedValue.TYPE_FIRST_COLOR_INT && a.type <= TypedValue.TYPE_LAST_COLOR_INT) {
            // windowBackground is a color
            drawable =  ColorDrawable(a.data)
        } else {
            // windowBackground is not a color, probably a drawable
            drawable = resources.getDrawable(a.resourceId, theme)
        }


        if ( sp.getBoolean("daynight", true)) {
            val cd = ColorDrawable(sp.getInt("darkBackgroundColor", info.nightscout.androidaps.core.R.color.background_dark))
            if ( !sp.getBoolean("backgroundcolor", true)) {
                scrollingactivity.background =  cd
            } else {
                scrollingactivity.background =  drawable
            }
        } else {
            val cd = ColorDrawable(sp.getInt("lightBackgroundColor", info.nightscout.androidaps.core.R.color.background_light))
            if ( !sp.getBoolean("backgroundcolor", true)) {
                scrollingactivity.background =  cd
            } else {
                scrollingactivity.background =  drawable
            }
        }
    }


    private fun initBottomSheet() {
        val nightMode = sp.getBoolean("daynight", true)
        // init the bottom sheet behavior
        mBottomSheetBehavior = BottomSheetBehavior.from(bottom_sheet)
        val backGround = sp.getBoolean("backgroundcolor", true)
        if (backGround == true) select_backgroundcolor.setVisibility(View.GONE) else select_backgroundcolor.setVisibility(View.VISIBLE)
        val switchCompatBackground = findViewById<SwitchCompat>(R.id.switch_backgroundimage)
        switchCompatBackground.isChecked = backGround
        switchCompatBackground.setOnCheckedChangeListener { compoundButton: CompoundButton, b: Boolean ->
            sp.putBoolean("backgroundcolor", b)
            if (b == true) select_backgroundcolor.setVisibility(View.GONE) else select_backgroundcolor.setVisibility(View.VISIBLE)
            val delayTime = 200
            compoundButton.postDelayed(Runnable { changeTheme(sp.getInt("theme", THEME_DARKSIDE)) }, delayTime.toLong())
        }
        val switchCompat = findViewById<SwitchCompat>(R.id.switch_dark_mode)
        switchCompat.isChecked = nightMode
        switchCompat.setOnCheckedChangeListener { compoundButton: CompoundButton, b: Boolean ->
            sp.putBoolean("daynight", b)
            var delayTime = 200
            if ((mBottomSheetBehavior as BottomSheetBehavior<*>).getState() == BottomSheetBehavior.STATE_EXPANDED) {
                delayTime = 400
                (mBottomSheetBehavior as BottomSheetBehavior<*>).setState(BottomSheetBehavior.STATE_EXPANDED)
            }
            compoundButton.postDelayed(object : Runnable {
                override fun run() {
                    if (b) {
                        delegate.localNightMode = AppCompatDelegate.MODE_NIGHT_YES
                    } else {
                        delegate.localNightMode = AppCompatDelegate.MODE_NIGHT_NO
                    }
                    changeTheme(sp.getInt("theme", THEME_DARKSIDE))
                }
            }, delayTime.toLong())
        }
        select_backgroundcolordark.setBackgroundColor(sp.getInt("darkBackgroundColor", ContextCompat.getColor(this, info.nightscout.androidaps.core.R.color.background_dark)))
        select_backgroundcolorlight.setBackgroundColor(sp.getInt("lightBackgroundColor", ContextCompat.getColor(this, info.nightscout.androidaps.core.R.color.background_light)))
        select_backgroundcolordark.setOnClickListener(View.OnClickListener { selectColor("dark") })
        select_backgroundcolorlight.setOnClickListener(View.OnClickListener { selectColor("light") })

        setDefaultColorDark?.setOnClickListener(View.OnClickListener {
            sp.putInt("darkBackgroundColor", ContextCompat.getColor(this, R.color.background_dark))
            select_backgroundcolordark!!.setBackgroundColor( getColor((R.color.background_dark)))
            val delayTime = 200
            select_backgroundcolordark!!.postDelayed({ changeTheme(sp.getInt("theme", THEME_DARKSIDE)) }, delayTime.toLong())
        })

        setDefaultColorLight?.setOnClickListener(View.OnClickListener {
            sp.putInt("lightBackgroundColor",  ContextCompat.getColor(this, R.color.background_light))
            select_backgroundcolorlight!!.setBackgroundColor( getColor((R.color.background_light)))
            val delayTime = 200
            select_backgroundcolorlight!!.postDelayed({ changeTheme(sp.getInt("theme", THEME_DARKSIDE)) }, delayTime.toLong())
        })

        mAdapter = ThemeAdapter(sp, mThemeList, object : RecyclerViewClickListener {
            override fun onClick(view: View?, position: Int) {
                (mBottomSheetBehavior as BottomSheetBehavior<*>).setState(BottomSheetBehavior.STATE_EXPANDED)
                view!!.postDelayed({
                    val themeView = findViewById<ThemeView>(R.id.theme_selected)
                    themeView.setTheme(mThemeList[selectedTheme], getThemeId(selectedTheme))
                    changeTheme(selectedTheme)
                }, 500)
            }
        })
        val mLayoutManager: RecyclerView.LayoutManager = GridLayoutManager(applicationContext, 3)
        recyclerView.setLayoutManager(mLayoutManager)
        recyclerView.setItemAnimator(DefaultItemAnimator())
        recyclerView.setAdapter(mAdapter)
    }

    private fun prepareThemeData() {
        mThemeList.clear()
        mThemeList.addAll(themeList)
        mAdapter!!.notifyDataSetChanged()
    }

    override fun onClick(view: View) {
        when (view.id) {
            R.id.fab -> when (mBottomSheetBehavior!!.state) {
                BottomSheetBehavior.STATE_HIDDEN    -> mBottomSheetBehavior!!.setState(BottomSheetBehavior.STATE_EXPANDED)
                BottomSheetBehavior.STATE_COLLAPSED -> mBottomSheetBehavior!!.setState(BottomSheetBehavior.STATE_EXPANDED)
                BottomSheetBehavior.STATE_EXPANDED  -> mBottomSheetBehavior!!.setState(BottomSheetBehavior.STATE_COLLAPSED)
            }
        }
    }

    private fun selectColor(lightOrDark: String) {
        val colorPickerDialog = ColorPickerDialog.Builder(this)
            .setTitle("Select Background Color")
            .setPreferenceName("MyColorPickerDialog")
            .setPositiveButton(getString(R.string.confirm),
                ColorEnvelopeListener { envelope, _ -> //setLayoutColor(envelope);
                    if (lightOrDark === "light") {
                        sp.putInt("lightBackgroundColor", envelope.color)
                        select_backgroundcolorlight!!.setBackgroundColor(envelope.color)
                        val delayTime = 200
                        select_backgroundcolorlight!!.postDelayed({ changeTheme(sp.getInt("theme", THEME_DARKSIDE)) }, delayTime.toLong())
                    } else if (lightOrDark === "dark") {
                        sp.putInt("darkBackgroundColor", envelope.color)
                        select_backgroundcolordark!!.setBackgroundColor(envelope.color)
                        val delayTime = 200
                        select_backgroundcolordark!!.postDelayed({ changeTheme(sp.getInt("theme", THEME_DARKSIDE)) }, delayTime.toLong())
                    }
                })
            .setNegativeButton(getString(R.string.cancel)
            ) { dialogInterface, _ -> dialogInterface.dismiss() }
            .attachAlphaSlideBar(false) // default is true. If false, do not show the AlphaSlideBar.
            .attachBrightnessSlideBar(true) // default is true. If false, do not show the BrightnessSlideBar.
            .setBottomSpace(12) // set bottom space between the last slidebar and buttons.

        val colorPickerView: ColorPickerView = colorPickerDialog.colorPickerView
        colorPickerView.setFlagView(CustomFlag(this, R.layout.colorpicker_flagview)) // sets a custom flagView
        colorPickerDialog.show()

        setBackground()
    }
}