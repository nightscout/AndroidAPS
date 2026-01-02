package app.aaps.core.ui.activities

import android.content.Context
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import androidx.core.view.MenuProvider
import app.aaps.core.ui.locale.LocaleHelper
import dagger.android.support.DaggerAppCompatActivity

open class TranslatedDaggerAppCompatActivity : DaggerAppCompatActivity() {

    private var menuProvider: MenuProvider? = null

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Add menu items without overriding methods in the Activity
        menuProvider = object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {}

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean =
                when (menuItem.itemId) {
                    android.R.id.home -> {
                        onBackPressedDispatcher.onBackPressed()
                        true
                    }

                    else              -> false
                }
        }
        menuProvider?.let { addMenuProvider(it) }
    }

    override fun onDestroy() {
        super.onDestroy()
        menuProvider?.let { removeMenuProvider(it) }
    }
}
