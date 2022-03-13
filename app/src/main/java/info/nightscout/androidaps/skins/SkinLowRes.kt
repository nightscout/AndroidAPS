package info.nightscout.androidaps.skins

import android.util.DisplayMetrics
import android.view.View.GONE
import info.nightscout.androidaps.interfaces.Config
import info.nightscout.androidaps.R
import info.nightscout.androidaps.databinding.ActionsFragmentBinding
import info.nightscout.androidaps.databinding.OverviewFragmentBinding
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SkinLowRes @Inject constructor(private val config: Config) : SkinInterface {

    override val description: Int get() = R.string.lowres_description
    override val mainGraphHeight: Int get() = 200
    override val secondaryGraphHeight: Int get() = 100

    override fun preProcessLandscapeActionsLayout(dm: DisplayMetrics, binding: ActionsFragmentBinding) {
        val screenWidth = dm.widthPixels
        val screenHeight = dm.heightPixels
        val isLandscape = screenHeight < screenWidth

        if (!isLandscape) {
            binding.status.apply {
                sensorAgeLabel.visibility = GONE
                sensorAgeLabel.visibility = GONE
                sensorLevelLabel.visibility = GONE
                insulinAgeLabel.visibility = GONE
                insulinLevelLabel.visibility = GONE
                cannulaAgeLabel.visibility = GONE
                cannulaPlaceholder.visibility = GONE
                pbAgeLabel.visibility = GONE
                pbLevelLabel.visibility = GONE
            }
        }
    }

    override fun preProcessLandscapeOverviewLayout(dm: DisplayMetrics, binding: OverviewFragmentBinding, isLandscape: Boolean, isTablet: Boolean, isSmallHeight: Boolean) {
        if (!config.NSCLIENT && isLandscape) moveButtonsLayout(binding.root)
    }

}
