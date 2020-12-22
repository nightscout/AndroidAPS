package info.nightscout.androidaps.skins

import android.util.DisplayMetrics
import android.view.View
import info.nightscout.androidaps.Config
import info.nightscout.androidaps.R
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SkinLowRes @Inject constructor(private val config: Config) : SkinInterface {

    override val description: Int get() = R.string.lowres_description
    override val mainGraphHeight: Int get() = 200
    override val secondaryGraphHeight: Int get() = 100

    override fun overviewLayout(isLandscape: Boolean, isTablet: Boolean, isSmallHeight: Boolean): Int =
        when {
            config.NSCLIENT              -> R.layout.overview_fragment_nsclient
            isLandscape                  -> R.layout.overview_fragment_landscape
            else                         -> R.layout.overview_fragment
        }

    override fun actionsLayout(isLandscape: Boolean, isSmallWidth: Boolean): Int =
        when {
            isLandscape                  -> R.layout.actions_fragment
            else                         -> R.layout.actions_fragment_lowres
        }

    override fun preProcessLandscapeOverviewLayout(dm: DisplayMetrics, view: View, isTablet: Boolean) {}
}
