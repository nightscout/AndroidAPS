package com.ms_square.etsyblur

import android.view.View
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import info.nightscout.androidaps.core.R

/**
 * BlurSupport.java
 *
 * @author Manabu-GT on 3/17/17.
 */
object BlurSupport {

    fun addTo(drawerLayout: DrawerLayout) {
        val viewToBlur = drawerLayout.getChildAt(0)
        val blurringView = drawerLayout.findViewById<View>(R.id.blurring_view)
        checkNotNull(viewToBlur) { "There's no view to blur. DrawerLayout does not have the first child view." }
        checkNotNull(blurringView) { "There's no blurringView. Include BlurringView with id set to 'blurring_view'" }
        check(blurringView is BlurringView) { "blurring_view must be type BlurringView" }
        blurringView.blurredView(viewToBlur)
        drawerLayout.addDrawerListener(BlurDrawerListener(blurringView))
        drawerLayout.post {
            if (drawerLayout.isDrawerVisible(GravityCompat.START) || drawerLayout.isDrawerVisible(GravityCompat.END)) {
                // makes sure to set it to be visible if the drawer is visible at start
                blurringView.setVisibility(View.VISIBLE)
            }
        }
    }
}