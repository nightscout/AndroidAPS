package app.aaps.plugins.main.di

import app.aaps.plugins.main.skins.SkinButtonsOn
import app.aaps.plugins.main.skins.SkinClassic
import app.aaps.plugins.main.skins.SkinInterface
import app.aaps.plugins.main.skins.SkinLargeDisplay
import app.aaps.plugins.main.skins.SkinLowRes
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntKey
import dagger.multibindings.IntoMap
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