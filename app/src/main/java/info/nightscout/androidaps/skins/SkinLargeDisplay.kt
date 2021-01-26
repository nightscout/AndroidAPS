package info.nightscout.androidaps.skins

import android.util.DisplayMetrics
import android.view.View
import android.widget.LinearLayout
import info.nightscout.androidaps.Config
import info.nightscout.androidaps.R
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SkinLargeDisplay @Inject constructor(private val config: Config): SkinInterface {

    override val description: Int get() = R.string.largedisplay_description
    override val mainGraphHeight: Int get() = 400
    override val secondaryGraphHeight: Int get() = 150

    override fun preProcessLandscapeOverviewLayout(dm: DisplayMetrics, view: View, isLandscape: Boolean, isTablet: Boolean, isSmallHeight: Boolean) {
        super.preProcessLandscapeOverviewLayout(dm, view, isLandscape, isTablet, isSmallHeight)
        if (!config.NSCLIENT && (isSmallHeight || isLandscape)) moveButtonsLayout(view as LinearLayout)
    }
}