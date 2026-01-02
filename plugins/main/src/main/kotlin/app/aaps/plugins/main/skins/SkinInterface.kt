package app.aaps.plugins.main.skins

import android.util.TypedValue.COMPLEX_UNIT_PX
import android.view.View
import android.widget.LinearLayout
import androidx.annotation.StringRes
import androidx.constraintlayout.widget.ConstraintLayout
import app.aaps.plugins.main.R
import app.aaps.plugins.main.databinding.ActionsFragmentBinding
import app.aaps.plugins.main.databinding.OverviewFragmentBinding

interface SkinInterface {

    @get:StringRes val description: Int

    val mainGraphHeight: Int // in dp
    val secondaryGraphHeight: Int // in dp

    // no pre processing by default
    fun preProcessLandscapeActionsLayout(isLandscape: Boolean, binding: ActionsFragmentBinding) {
    }

    fun preProcessLandscapeOverviewLayout(binding: OverviewFragmentBinding, isLandscape: Boolean, isTablet: Boolean, isSmallHeight: Boolean) {
        if (isLandscape) {
            val iobLayout = binding.infoLayout.iobLayout
            val iobLayoutParams = iobLayout.layoutParams as ConstraintLayout.LayoutParams
            val timeLayout = binding.infoLayout.timeLayout
            iobLayoutParams.startToStart = ConstraintLayout.LayoutParams.UNSET
            iobLayoutParams.startToEnd = timeLayout.id
            iobLayoutParams.topToBottom = ConstraintLayout.LayoutParams.UNSET
            iobLayoutParams.topToTop = ConstraintLayout.LayoutParams.PARENT_ID
            val timeLayoutParams = timeLayout.layoutParams as ConstraintLayout.LayoutParams
            timeLayoutParams.endToEnd = ConstraintLayout.LayoutParams.UNSET
            timeLayoutParams.endToStart = iobLayout.id
            val cobLayoutParams = binding.infoLayout.cobLayout.layoutParams as ConstraintLayout.LayoutParams
            cobLayoutParams.topToTop = ConstraintLayout.LayoutParams.PARENT_ID
            val basalLayoutParams = binding.infoLayout.basalLayout.layoutParams as ConstraintLayout.LayoutParams
            basalLayoutParams.topToTop = ConstraintLayout.LayoutParams.PARENT_ID
            val extendedLayoutParams = binding.infoLayout.extendedLayout.layoutParams as ConstraintLayout.LayoutParams
            extendedLayoutParams.topToTop = ConstraintLayout.LayoutParams.PARENT_ID
            val asLayoutParams = binding.infoLayout.asLayout.layoutParams as ConstraintLayout.LayoutParams
            asLayoutParams.topToTop = ConstraintLayout.LayoutParams.PARENT_ID

            if (isTablet) {
                timeLayout.visibility = View.VISIBLE
                binding.infoLayout.apply {
                    val texts = listOf(bg, iob, cob, baseBasal, extendedBolus, sensitivity)
                    for (v in texts) v.setTextSize(COMPLEX_UNIT_PX, v.textSize * 1.5f)
                    val textsTime = listOf(time, timeAgoShort)
                    for (v in textsTime) v.setTextSize(COMPLEX_UNIT_PX, v.textSize * 2.25f)
                }
                binding.apply {
                    val texts = listOf(pump, openaps, uploader)
                    for (v in texts) v.setTextSize(COMPLEX_UNIT_PX, v.textSize * 1.3f)
                }
                binding.statusLightsLayout.apply {
                    val texts = listOf(cannulaAge, insulinAge, reservoirLevel, sensorAge, pbAge, pbLevel)
                    for (v in texts) v.setTextSize(COMPLEX_UNIT_PX, v.textSize * 1.3f)
                }
                timeLayout.orientation = LinearLayout.HORIZONTAL
                binding.infoLayout.timeAgoShort.setTextSize(COMPLEX_UNIT_PX, binding.infoLayout.time.textSize)

                binding.infoLayout.deltaLarge.visibility = View.VISIBLE
            } else {
                timeLayout.visibility = View.GONE
                binding.infoLayout.deltaLarge.visibility = View.GONE
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
