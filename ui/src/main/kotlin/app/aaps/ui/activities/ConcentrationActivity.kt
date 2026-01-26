package app.aaps.ui.activities

import android.os.Bundle
import app.aaps.core.ui.activities.TranslatedDaggerAppCompatActivity
import app.aaps.ui.activities.fragments.ConcentrationFragment
import app.aaps.ui.dialogs.ConcentrationDialog

class ConcentrationActivity : TranslatedDaggerAppCompatActivity(){

    @Override
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportFragmentManager.beginTransaction()   // add an empty fragment to support the sequence of 3 Dialogs launch with uiInteraction
            .add(android.R.id.content, ConcentrationFragment())
            .commit()
    }
}