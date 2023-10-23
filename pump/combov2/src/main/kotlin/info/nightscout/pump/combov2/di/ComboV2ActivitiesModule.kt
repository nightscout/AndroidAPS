package info.nightscout.pump.combov2.di

import dagger.Module
import dagger.android.ContributesAndroidInjector
import info.nightscout.pump.combov2.ComboV2Fragment
import info.nightscout.pump.combov2.activities.ComboV2PairingActivity

@Module
@Suppress("unused")
abstract class ComboV2ActivitiesModule {
    @ContributesAndroidInjector abstract fun contributesComboV2PairingActivity(): ComboV2PairingActivity

    @ContributesAndroidInjector abstract fun contributesComboV2Fragment(): ComboV2Fragment
}
