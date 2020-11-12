package info.nightscout.androidaps.skins

import androidx.annotation.LayoutRes
import androidx.annotation.StringRes
import info.nightscout.androidaps.R

interface SkinInterface {
    @get:StringRes val description : Int

    val mainGraphHeight : Int // in dp
    val secondaryGraphHeight : Int // in dp
    @LayoutRes fun overviewLayout(isLandscape : Boolean, isTablet : Boolean, isSmallHeight : Boolean): Int
    @LayoutRes fun actionsLayout(isLandscape : Boolean, isSmallWidth : Boolean): Int = R.layout.actions_fragment
}