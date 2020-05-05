package info.nightscout.androidaps.dependencyInjection

import dagger.Module
import dagger.android.ContributesAndroidInjector
import info.nightscout.androidaps.plugins.aps.loop.APSResult
import info.nightscout.androidaps.plugins.aps.openAPSAMA.DetermineBasalResultAMA
import info.nightscout.androidaps.plugins.aps.openAPSSMB.DetermineBasalAdapterSMBJS
import info.nightscout.androidaps.plugins.aps.openAPSSMB.DetermineBasalResultSMB
import info.nightscout.androidaps.plugins.constraints.objectives.objectives.*
import info.nightscout.androidaps.plugins.general.overview.graphData.GraphData
import info.nightscout.androidaps.plugins.general.overview.notifications.NotificationWithAction
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.AutosensData
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.IobCobOref1Thread
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.IobCobThread
import info.nightscout.androidaps.skins.SkinListPreference

@Module
@Suppress("unused")
abstract class UIModule {
    @ContributesAndroidInjector abstract fun skinListPreferenceInjector(): SkinListPreference
}