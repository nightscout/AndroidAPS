package info.nightscout.plugins.skins

import info.nightscout.interfaces.Config
import info.nightscout.plugins.R
import info.nightscout.plugins.databinding.OverviewFragmentBinding
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SkinClassic @Inject constructor(private val config: Config) : SkinInterface {

    override val description: Int get() = R.string.classic_description
    override val mainGraphHeight: Int get() = 200
    override val secondaryGraphHeight: Int get() = 100

    override fun preProcessLandscapeOverviewLayout(binding: OverviewFragmentBinding, isLandscape: Boolean, isTablet: Boolean, isSmallHeight: Boolean) {
        super.preProcessLandscapeOverviewLayout(binding, isLandscape, isTablet, isSmallHeight)
        if (!config.NSCLIENT && (isSmallHeight || isLandscape)) moveButtonsLayout(binding.root)
    }
}
