package info.nightscout.androidaps.dependencyInjection

import dagger.Module
import dagger.android.ContributesAndroidInjector
import info.nightscout.androidaps.setupwizard.SWEventListener
import info.nightscout.androidaps.setupwizard.SWScreen
import info.nightscout.androidaps.setupwizard.elements.*

@Module
@Suppress("unused")
abstract class WizardModule {

    @ContributesAndroidInjector abstract fun swBreakInjector(): SWBreak
    @ContributesAndroidInjector abstract fun swButtonInjector(): SWButton
    @ContributesAndroidInjector abstract fun swEditNumberWithUnitsInjector(): SWEditNumberWithUnits
    @ContributesAndroidInjector abstract fun swEditNumberInjector(): SWEditNumber
    @ContributesAndroidInjector abstract fun swEditStringInjector(): SWEditString
    @ContributesAndroidInjector abstract fun swEditEncryptedPasswordInjector(): SWEditEncryptedPassword
    @ContributesAndroidInjector abstract fun swEditUrlInjector(): SWEditUrl
    @ContributesAndroidInjector abstract fun swFragmentInjector(): SWFragment
    @ContributesAndroidInjector abstract fun swHtmlLinkInjector(): SWHtmlLink
    @ContributesAndroidInjector abstract fun swInfotextInjector(): SWInfoText
    @ContributesAndroidInjector abstract fun swItemInjector(): SWItem
    @ContributesAndroidInjector abstract fun swPluginInjector(): SWPlugin
    @ContributesAndroidInjector abstract fun swRadioButtonInjector(): SWRadioButton
    @ContributesAndroidInjector abstract fun swScreenInjector(): SWScreen
    @ContributesAndroidInjector abstract fun swEventListenerInjector(): SWEventListener
}