package info.nightscout.interfaces.maintenance

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class PrefMetadata(var value: String, var status: PrefsStatus, var info: String? = null) : Parcelable
