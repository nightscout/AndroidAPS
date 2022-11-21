package info.nightscout.androidaps.di

import dagger.Module
import dagger.android.ContributesAndroidInjector
import info.nightscout.androidaps.setupwizard.SWEventListener
import info.nightscout.androidaps.setupwizard.SWScreen
import info.nightscout.androidaps.setupwizard.elements.SWBreak
import info.nightscout.androidaps.setupwizard.elements.SWButton
import info.nightscout.androidaps.setupwizard.elements.SWEditEncryptedPassword
import info.nightscout.androidaps.setupwizard.elements.SWEditIntNumber
import info.nightscout.androidaps.setupwizard.elements.SWEditNumber
import info.nightscout.androidaps.setupwizard.elements.SWEditNumberWithUnits
import info.nightscout.androidaps.setupwizard.elements.SWEditString
import info.nightscout.androidaps.setupwizard.elements.SWEditUrl
import info.nightscout.androidaps.setupwizard.elements.SWFragment
import info.nightscout.androidaps.setupwizard.elements.SWHtmlLink
import info.nightscout.androidaps.setupwizard.elements.SWInfoText
import info.nightscout.androidaps.setupwizard.elements.SWItem
import info.nightscout.androidaps.setupwizard.elements.SWPlugin
import info.nightscout.androidaps.setupwizard.elements.SWPreference
import info.nightscout.androidaps.setupwizard.elements.SWRadioButton

@Module
@Suppress("unused")
abstract class WizardModule {

    @ContributesAndroidInjector abstract fun swBreakInjector(): SWBreak
    @ContributesAndroidInjector abstract fun swButtonInjector(): SWButton
    @ContributesAndroidInjector abstract fun swEditNumberWithUnitsInjector(): SWEditNumberWithUnits
    @ContributesAndroidInjector abstract fun swEditNumberInjector(): SWEditNumber
    @ContributesAndroidInjector abstract fun swEditIntNumberInjector(): SWEditIntNumber
    @ContributesAndroidInjector abstract fun swEditStringInjector(): SWEditString
    @ContributesAndroidInjector abstract fun swEditEncryptedPasswordInjector(): SWEditEncryptedPassword
    @ContributesAndroidInjector abstract fun swEditUrlInjector(): SWEditUrl
    @ContributesAndroidInjector abstract fun swFragmentInjector(): SWFragment
    @ContributesAndroidInjector abstract fun swPreferenceInjector(): SWPreference
    @ContributesAndroidInjector abstract fun swHtmlLinkInjector(): SWHtmlLink
    @ContributesAndroidInjector abstract fun swInfoTextInjector(): SWInfoText
    @ContributesAndroidInjector abstract fun swItemInjector(): SWItem
    @ContributesAndroidInjector abstract fun swPluginInjector(): SWPlugin
    @ContributesAndroidInjector abstract fun swRadioButtonInjector(): SWRadioButton
    @ContributesAndroidInjector abstract fun swScreenInjector(): SWScreen
    @ContributesAndroidInjector abstract fun swEventListenerInjector(): SWEventListener
}