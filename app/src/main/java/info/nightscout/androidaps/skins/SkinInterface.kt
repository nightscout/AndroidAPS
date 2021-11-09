package info.nightscout.androidaps.skins

import android.util.DisplayMetrics
import android.util.TypedValue.COMPLEX_UNIT_PX
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.LayoutRes
import androidx.annotation.StringRes
import androidx.constraintlayout.widget.ConstraintLayout
import info.nightscout.androidaps.R

interface SkinInterface {

    @get:StringRes val description: Int

    val mainGraphHeight: Int // in dp
    val secondaryGraphHeight: Int // in dp

    @LayoutRes
    fun actionsLayout(isLandscape: Boolean, isSmallWidth: Boolean): Int = R.layout.actions_fragment

    fun preProcessLandscapeOverviewLayout(dm: DisplayMetrics, view: View, isLandscape: Boolean, isTablet: Boolean, isSmallHeight: Boolean) {
        // pre-process landscape mode
        val screenWidth = dm.widthPixels
        val screenHeight = dm.heightPixels
        val landscape = screenHeight < screenWidth

        if (landscape) {
            val iobLayout = view.findViewById<LinearLayout>(R.id.iob_layout)
            val iobLayoutParams = iobLayout.layoutParams as ConstraintLayout.LayoutParams
            val timeLayout = view.findViewById<LinearLayout>(R.id.time_layout)
            iobLayoutParams.startToStart = ConstraintLayout.LayoutParams.UNSET
            iobLayoutParams.startToEnd = timeLayout.id
            iobLayoutParams.topToBottom = ConstraintLayout.LayoutParams.UNSET
            iobLayoutParams.topToTop = ConstraintLayout.LayoutParams.PARENT_ID
            val timeLayoutParams = timeLayout.layoutParams as ConstraintLayout.LayoutParams
            timeLayoutParams.endToEnd = ConstraintLayout.LayoutParams.UNSET
            timeLayoutParams.endToStart = iobLayout.id
            val cobLayoutParams = view.findViewById<LinearLayout>(R.id.cob_layout).layoutParams as ConstraintLayout.LayoutParams
            cobLayoutParams.topToTop = ConstraintLayout.LayoutParams.PARENT_ID
            val basalLayoutParams = view.findViewById<LinearLayout>(R.id.basal_layout).layoutParams as ConstraintLayout.LayoutParams
            basalLayoutParams.topToTop = ConstraintLayout.LayoutParams.PARENT_ID
            val extendedLayoutParams = view.findViewById<LinearLayout>(R.id.extended_layout).layoutParams as ConstraintLayout.LayoutParams
            extendedLayoutParams.topToTop = ConstraintLayout.LayoutParams.PARENT_ID
            val asLayoutParams = view.findViewById<LinearLayout>(R.id.as_layout).layoutParams as ConstraintLayout.LayoutParams
            asLayoutParams.topToTop = ConstraintLayout.LayoutParams.PARENT_ID

            if (isTablet) {
                for (v in listOf<TextView?>(
                    view.findViewById(R.id.bg),
                    view.findViewById(R.id.time),
                    view.findViewById(R.id.time_ago_short),
                    view.findViewById(R.id.iob),
                    view.findViewById(R.id.cob),
                    view.findViewById(R.id.base_basal),
                    view.findViewById(R.id.extended_bolus),
                    view.findViewById(R.id.sensitivity)
                )) v?.setTextSize(COMPLEX_UNIT_PX, v.textSize * 1.5f)
                for (v in listOf<TextView?>(
                    view.findViewById(R.id.pump),
                    view.findViewById(R.id.openaps),
                    view.findViewById(R.id.uploader),
                    view.findViewById(R.id.cannula_age),
                    view.findViewById(R.id.insulin_age),
                    view.findViewById(R.id.reservoir_level),
                    view.findViewById(R.id.sensor_age),
                    view.findViewById(R.id.pb_age),
                    view.findViewById(R.id.battery_level)
                )) v?.setTextSize(COMPLEX_UNIT_PX, v.textSize * 1.3f)
                timeLayout?.orientation = LinearLayout.HORIZONTAL
                view.findViewById<TextView>(R.id.time_ago_short)?.setTextSize(COMPLEX_UNIT_PX, view.findViewById<TextView>(R.id.time).textSize)

                view.findViewById<TextView>(R.id.delta_large)?.visibility = View.VISIBLE
            } else {
                view.findViewById<TextView>(R.id.delta_large)?.visibility = View.GONE
            }
        }
    }

    fun moveButtonsLayout(root: LinearLayout) {
        val buttonsLayout = root.findViewById<LinearLayout>(R.id.buttons_layout)
        root.removeView(buttonsLayout)
        val innerLayout = root.findViewById<LinearLayout>(R.id.inner_layout)
        innerLayout.addView(buttonsLayout)
    }
}