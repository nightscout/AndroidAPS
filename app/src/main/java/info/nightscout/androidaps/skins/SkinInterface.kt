package info.nightscout.androidaps.skins

import android.util.DisplayMetrics
import android.widget.LinearLayout
import androidx.annotation.LayoutRes
import androidx.annotation.StringRes
import androidx.constraintlayout.widget.ConstraintLayout
import info.nightscout.androidaps.R

interface SkinInterface {
    @get:StringRes val description : Int

    val mainGraphHeight : Int // in dp
    val secondaryGraphHeight : Int // in dp
    @LayoutRes fun overviewLayout(isLandscape : Boolean, isTablet : Boolean, isSmallHeight : Boolean): Int
    @LayoutRes fun actionsLayout(isLandscape : Boolean, isSmallWidth : Boolean): Int = R.layout.actions_fragment

    fun preProcessLandscapeOverviewLayout(dm: DisplayMetrics, iobLayout: LinearLayout, timeLayout: LinearLayout) {
        // pre-process landscape mode
        val screenWidth = dm.widthPixels
        val screenHeight = dm.heightPixels
        val landscape = screenHeight < screenWidth

        if (landscape) {
            val iobLayoutParams = iobLayout.layoutParams as ConstraintLayout.LayoutParams
            iobLayoutParams.startToStart = ConstraintLayout.LayoutParams.UNSET
            iobLayoutParams.startToEnd = timeLayout.id
            iobLayoutParams.topToBottom = ConstraintLayout.LayoutParams.UNSET
            iobLayoutParams.topToTop = ConstraintLayout.LayoutParams.PARENT_ID
            val timeLayoutParams = timeLayout.layoutParams as ConstraintLayout.LayoutParams
            timeLayoutParams.endToEnd = ConstraintLayout.LayoutParams.UNSET
            timeLayoutParams.endToStart = iobLayout.id
        }
    }
}