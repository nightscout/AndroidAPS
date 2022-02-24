package com.ms_square.etsyblur

import android.view.View
import androidx.drawerlayout.widget.DrawerLayout.SimpleDrawerListener

/**
 * BlurDrawerListener.java
 *
 * @author Manabu-GT on 3/17/17.
 */
internal class BlurDrawerListener(private val blurringView: BlurringView) : SimpleDrawerListener() {

    override fun onDrawerSlide(drawerView: View, slideOffset: Float) {
        if (blurringView.visibility != View.VISIBLE) {
            blurringView.visibility = View.VISIBLE
        }
        blurringView.alpha = slideOffset
    }

    override fun onDrawerClosed(drawerView: View) {
        blurringView.visibility = View.INVISIBLE
    }

    init {
        blurringView.visibility = View.INVISIBLE
    }
}