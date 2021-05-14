package info.nightscout.androidaps.plugins.general.overview

import info.nightscout.androidaps.R
import info.nightscout.androidaps.database.entities.TemporaryBasal
import info.nightscout.androidaps.extensions.convertedToPercent
import info.nightscout.androidaps.extensions.toStringFull
import info.nightscout.androidaps.extensions.toStringShort
import info.nightscout.androidaps.interfaces.Profile
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.resources.ResourceHelper
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OverviewData @Inject constructor(
    private val resourceHelper: ResourceHelper,
    private val dateUtil: DateUtil
) {

    enum class Property {
        PROFILE,
        TEMPORARY_BASAL
    }

    @get:Synchronized @set:Synchronized
    var profile: Profile? = null

    @get:Synchronized @set:Synchronized
    var profileName: String? = null

    var temporaryBasal: TemporaryBasal? = null

    val temporaryBasalText: String
        get() =
            profile?.let { profile ->
                temporaryBasal?.let { "T:" + it.toStringShort() }
                    ?: resourceHelper.gs(R.string.pump_basebasalrate, profile.getBasal())
            } ?: resourceHelper.gs(R.string.notavailable)

    val temporaryBasalDialogText: String
        get() = profile?.let { profile ->
            temporaryBasal?.let { temporaryBasal ->
                "${resourceHelper.gs(R.string.basebasalrate_label)}: ${resourceHelper.gs(R.string.pump_basebasalrate, profile.getBasal())}" +
                    "\n" + resourceHelper.gs(R.string.tempbasal_label) + ": " + temporaryBasal.toStringFull(profile, dateUtil)
            }
                ?: "${resourceHelper.gs(R.string.basebasalrate_label)}: ${resourceHelper.gs(R.string.pump_basebasalrate, profile.getBasal())}"
        } ?: resourceHelper.gs(R.string.notavailable)

    val temporaryBasalIcon: Int
        get() =
            profile?.let { profile ->
                temporaryBasal?.let { temporaryBasal ->
                    val percentRate = temporaryBasal.convertedToPercent(dateUtil.now(), profile)
                    when {
                        percentRate > 100 -> R.drawable.ic_cp_basal_tbr_high
                        percentRate < 100 -> R.drawable.ic_cp_basal_tbr_low
                        else              -> R.drawable.ic_cp_basal_no_tbr
                    }
                }
            } ?: R.drawable.ic_cp_basal_no_tbr

    val temporaryBasalColor: Int
        get() = temporaryBasal?.let { resourceHelper.gc(R.color.basal) }
            ?: resourceHelper.gc(R.color.defaulttextcolor)
}