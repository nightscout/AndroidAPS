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
    fun overviewLayout(isLandscape: Boolean, isTablet: Boolean, isSmallHeight: Boolean): Int

    @LayoutRes
    fun actionsLayout(isLandscape: Boolean, isSmallWidth: Boolean): Int = R.layout.actions_fragment

    fun preProcessLandscapeOverviewLayout(dm: DisplayMetrics, view: View, isTablet: Boolean) {
        // pre-process landscape mode
        val screenWidth = dm.widthPixels
        val screenHeight = dm.heightPixels
        val landscape = screenHeight < screenWidth

        if (landscape) {
            val iobLayout = view.findViewById<LinearLayout>(R.id.overview_iob_llayout)
            val iobLayoutParams = iobLayout.layoutParams as ConstraintLayout.LayoutParams
            val timeLayout = view.findViewById<LinearLayout>(R.id.overview_time_llayout)
            iobLayoutParams.startToStart = ConstraintLayout.LayoutParams.UNSET
            iobLayoutParams.startToEnd = timeLayout.id
            iobLayoutParams.topToBottom = ConstraintLayout.LayoutParams.UNSET
            iobLayoutParams.topToTop = ConstraintLayout.LayoutParams.PARENT_ID
            val timeLayoutParams = timeLayout.layoutParams as ConstraintLayout.LayoutParams
            timeLayoutParams.endToEnd = ConstraintLayout.LayoutParams.UNSET
            timeLayoutParams.endToStart = iobLayout.id
            val cobLayoutParams = view.findViewById<LinearLayout>(R.id.overview_cob_llayout).layoutParams as ConstraintLayout.LayoutParams
            cobLayoutParams.topToTop = ConstraintLayout.LayoutParams.PARENT_ID
            val basalLayoutParams = view.findViewById<LinearLayout>(R.id.overview_basal_llayout).layoutParams as ConstraintLayout.LayoutParams
            basalLayoutParams.topToTop = ConstraintLayout.LayoutParams.PARENT_ID
            val extendedLayoutParams = view.findViewById<LinearLayout>(R.id.overview_extended_llayout).layoutParams as ConstraintLayout.LayoutParams
            extendedLayoutParams.topToTop = ConstraintLayout.LayoutParams.PARENT_ID
            val asLayoutParams = view.findViewById<LinearLayout>(R.id.overview_as_llayout).layoutParams as ConstraintLayout.LayoutParams
            asLayoutParams.topToTop = ConstraintLayout.LayoutParams.PARENT_ID

            if (isTablet) {
                for (v in listOf<TextView?>(
                    view.findViewById(R.id.overview_bg),
                    view.findViewById(R.id.overview_time),
                    view.findViewById(R.id.overview_timeagoshort),
                    view.findViewById(R.id.overview_iob),
                    view.findViewById(R.id.overview_cob),
                    view.findViewById(R.id.overview_basebasal),
                    view.findViewById(R.id.overview_extendedbolus),
                    view.findViewById(R.id.overview_sensitivity)
                )) v?.setTextSize(COMPLEX_UNIT_PX, v.textSize * 1.5f)
                for (v in listOf<TextView?>(
                    view.findViewById(R.id.overview_pump),
                    view.findViewById(R.id.overview_openaps),
                    view.findViewById(R.id.overview_uploader),
                    view.findViewById(R.id.careportal_canulaage),
                    view.findViewById(R.id.careportal_insulinage),
                    view.findViewById(R.id.careportal_reservoirlevel),
                    view.findViewById(R.id.careportal_reservoirlevel),
                    view.findViewById(R.id.careportal_sensorage),
                    view.findViewById(R.id.careportal_pbage),
                    view.findViewById(R.id.careportal_batterylevel)
                )) v?.setTextSize(COMPLEX_UNIT_PX, v.textSize * 1.3f)
                timeLayout?.orientation = LinearLayout.HORIZONTAL
                view.findViewById<TextView>(R.id.overview_timeagoshort)?.setTextSize(COMPLEX_UNIT_PX, view.findViewById<TextView>(R.id.overview_time).textSize)

                view.findViewById<TextView>(R.id.overview_delta_large)?.visibility = View.VISIBLE
            } else {
                view.findViewById<TextView>(R.id.overview_delta_large)?.visibility = View.GONE
            }
        }
    }
}