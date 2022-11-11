package info.nightscout.plugins.di

import dagger.Module
import dagger.Provides
import dagger.multibindings.IntKey
import dagger.multibindings.IntoMap
import info.nightscout.plugins.skins.SkinButtonsOn
import info.nightscout.plugins.skins.SkinClassic
import info.nightscout.plugins.skins.SkinInterface
import info.nightscout.plugins.skins.SkinLargeDisplay
import info.nightscout.plugins.skins.SkinLowRes
import javax.inject.Qualifier

@Module
open class SkinsModule {

    @Provides
    @Skin
    @IntoMap
    @IntKey(0)
    fun bindsSkinClassic(skinClassic: SkinClassic): SkinInterface = skinClassic

    @Provides
    @Skin
    @IntoMap
    @IntKey(10)
    fun bindsSkinButtonsOn(skinButtonsOn: SkinButtonsOn): SkinInterface = skinButtonsOn

    @Provides
    @Skin
    @IntoMap
    @IntKey(20)
    fun bindsSkinLargeDisplay(skinLargeDisplay: SkinLargeDisplay): SkinInterface = skinLargeDisplay

    @Provides
    @Skin
    @IntoMap
    @IntKey(30)
    fun bindsSkinLowRes(skinLowRes: SkinLowRes): SkinInterface = skinLowRes

    @Qualifier
    annotation class Skin
}