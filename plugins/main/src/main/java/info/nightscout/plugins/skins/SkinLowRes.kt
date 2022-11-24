package info.nightscout.plugins.skins

import android.util.DisplayMetrics
import android.view.View.GONE
import android.view.ViewGroup
import info.nightscout.interfaces.Config
import info.nightscout.plugins.R
import info.nightscout.plugins.databinding.ActionsFragmentBinding
import info.nightscout.plugins.databinding.OverviewFragmentBinding
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
                cannulaUsageLabel.visibility = GONE
                pbAgeLabel.visibility = GONE
                pbLevelLabel.visibility = GONE
            }
        }
    }

    override fun preProcessLandscapeOverviewLayout(dm: DisplayMetrics, binding: OverviewFragmentBinding, isLandscape: Boolean, isTablet: Boolean, isSmallHeight: Boolean) {
        if (!config.NSCLIENT && isLandscape) moveButtonsLayout(binding.root)

        binding.apply {
            infoCard.elevation = 0F
            infoCard.radius = 0F
            val paramInfo = (infoCard.layoutParams as ViewGroup.MarginLayoutParams).apply {
                setMargins(0, 0, 0, 0)
            }
            infoCard.layoutParams = paramInfo

            statusCard.elevation = 0F
            statusCard.radius = 0F
            statusCard.strokeWidth = 1
            val paramStatus = (statusCard.layoutParams as ViewGroup.MarginLayoutParams).apply {
                setMargins(0, 0, 0, 0)
            }
            statusCard.layoutParams = paramStatus

            nsclientCard.elevation = 0F
            nsclientCard.radius = 0F
            val paramNsClient = (nsclientCard.layoutParams as ViewGroup.MarginLayoutParams).apply {
                setMargins(0, 0, 0, 0)
            }
            nsclientCard.layoutParams = paramNsClient

            graphCard.elevation = 0F
            graphCard.radius = 0F
            val paramGraph = (graphCard.layoutParams as ViewGroup.MarginLayoutParams).apply {
                setMargins(0, 0, 0, 0)
            }
            graphCard.layoutParams = paramGraph

            activeProfile.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0)
            tempTarget.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0)
        }

    }

}
