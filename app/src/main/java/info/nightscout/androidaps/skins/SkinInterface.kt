package info.nightscout.androidaps.skins

import androidx.annotation.LayoutRes
import androidx.annotation.StringRes

interface SkinInterface {
    @get:StringRes val description : Int

    val mainGraphHeight : Int // in dp
    val secondaryGraphHeight : Int // in dp
    @LayoutRes fun overviewLayout(isLandscape : Boolean, isTablet : Boolean, isSmallHeight : Boolean): Int
}