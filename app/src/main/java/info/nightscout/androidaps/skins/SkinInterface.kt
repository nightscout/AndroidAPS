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
import kotlinx.android.synthetic.main.overview_fragment_nsclient.view.*
import kotlinx.android.synthetic.main.overview_info_layout.view.*
import kotlinx.android.synthetic.main.overview_statuslights_layout.view.*
import kotlinx.android.synthetic.main.overview_statuslights_layout.view.careportal_batterylevel
import kotlinx.android.synthetic.main.overview_statuslights_layout.view.careportal_canulaage
import kotlinx.android.synthetic.main.overview_statuslights_layout.view.careportal_insulinage
import kotlinx.android.synthetic.main.overview_statuslights_layout.view.careportal_pbage
import kotlinx.android.synthetic.main.overview_statuslights_layout.view.careportal_reservoirlevel
import kotlinx.android.synthetic.main.overview_statuslights_layout.view.careportal_sensorage
import kotlinx.android.synthetic.main.status_fragment.view.*

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
            iobLayoutParams.startToStart = ConstraintLayout.LayoutParams.UNSET
           // iobLayoutParams.startToEnd = view.overview_time_llayout.id
            iobLayoutParams.topToBottom = ConstraintLayout.LayoutParams.UNSET
            iobLayoutParams.topToTop = ConstraintLayout.LayoutParams.PARENT_ID
           // val timeLayoutParams = view.overview_time_llayout.layoutParams as ConstraintLayout.LayoutParams
           // timeLayoutParams.endToEnd = ConstraintLayout.LayoutParams.UNSET
           // timeLayoutParams.endToStart = view.overview_iob_llayout.id
            val cobLayoutParams = view.overview_cob_llayout.layoutParams as ConstraintLayout.LayoutParams
            cobLayoutParams.topToTop = ConstraintLayout.LayoutParams.PARENT_ID
            val basalLayoutParams = view.findViewById<LinearLayout>(R.id.overview_basal_llayout).layoutParams as ConstraintLayout.LayoutParams
            basalLayoutParams.topToTop = ConstraintLayout.LayoutParams.PARENT_ID
            val extendedLayoutParams = view.findViewById<LinearLayout>(R.id.overview_extended_llayout).layoutParams as ConstraintLayout.LayoutParams
            extendedLayoutParams.topToTop = ConstraintLayout.LayoutParams.PARENT_ID
            val asLayoutParams = view.findViewById<LinearLayout>(R.id.overview_as_llayout).layoutParams as ConstraintLayout.LayoutParams
            asLayoutParams.topToTop = ConstraintLayout.LayoutParams.PARENT_ID

            if (isTablet) {
                for (v in listOf<TextView?>(
                    view.overview_bg,
                   // view.overview_time,
                   // view.overview_timeagoshort,
                    view.overview_iob,
                    view.overview_cob,
                    view.overview_basebasal,
                    view.overview_extendedbolus,
                    view.overview_sensitivity
                )) v?.setTextSize(COMPLEX_UNIT_PX, v.textSize * 1.5f)
                for (v in listOf<TextView?>(
                    view.overview_pump,
                    view.overview_openaps,
                    view.overview_uploader,
                    view.careportal_canulaage,
                    view.careportal_insulinage,
                    view.careportal_reservoirlevel,
                    view.careportal_reservoirlevel,
                    view.careportal_sensorage,
                    view.careportal_pbage,
                    view.careportal_batterylevel
                )) if (v != null) {
                    v.setTextSize(COMPLEX_UNIT_PX, v.textSize * 1.3f)
                }
               // view.overview_time_llayout.orientation = LinearLayout.HORIZONTAL
               // view.overview_timeagoshort.setTextSize(COMPLEX_UNIT_PX, view.overview_time.textSize)
            }
        }
    }
}