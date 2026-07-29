package app.aaps.wear.interaction

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import androidx.wear.watchface.editor.EditorSession
import kotlinx.coroutines.launch

/**
 * Transparent activity that lets the user pick a complication data source for one of
 * CustomWatchface's slots directly from the AAPS Settings menu, without relying on the system's
 * long-press "Customize" gesture (which some WearOS 5 sideload-workaround installs don't expose).
 *
 * Declared with an intent-filter for ACTION_WATCH_FACE_EDITOR so
 * androidx.wear.watchface.editor.WatchFaceEditorContract resolves to this activity when launched
 * with our own package name as the editor package (see WatchfaceConfigurationActivity).
 */
class ComplicationSlotEditorActivity : ComponentActivity() {

    companion object {

        const val EXTRA_SLOT_ID = "app.aaps.wear.interaction.EXTRA_SLOT_ID"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val slotId = intent.getIntExtra(EXTRA_SLOT_ID, -1)
        if (slotId == -1) {
            finish()
            return
        }
        lifecycleScope.launch {
            try {
                val session = EditorSession.createOnWatchEditorSession(this@ComplicationSlotEditorActivity)
                session.openComplicationDataSourceChooser(slotId)
                session.close()
            } finally {
                finish()
            }
        }
    }
}
