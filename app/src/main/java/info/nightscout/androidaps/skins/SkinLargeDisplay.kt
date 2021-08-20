package info.nightscout.androidaps.skins

import info.nightscout.androidaps.Config
import info.nightscout.androidaps.R
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SkinLargeDisplay @Inject constructor(private val config: Config): SkinInterface {

    override val description: Int get() = R.string.largedisplay_description
    override val mainGraphHeight: Int get() = 400
    override val secondaryGraphHeight: Int get() = 150

    override fun overviewLayout(isLandscape: Boolean, isTablet: Boolean, isSmallHeight: Boolean): Int =
        when {
            config.NSCLIENT              -> R.layout.overview_fragment_nsclient
            isSmallHeight || isLandscape -> R.layout.overview_fragment_landscape
            else                         -> R.layout.overview_fragment
        }

}