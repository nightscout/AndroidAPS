package info.nightscout.androidaps.plugins.pump.eopatch.vo

import android.content.Intent

data class ActivityResultEvent(
        val requestCode: Int,
        val resultCode: Int,
        val data: Intent? = null
)