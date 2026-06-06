package app.aaps.core.interfaces.maintenance

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.RawValue

@Parcelize
data class PrefsFile(
    val name: String,
    val content: String,

    // metadata here is used only for list display
    val metadata: @RawValue Map<PrefsMetadataKey, PrefMetadata>,

    // Stable unique identifier from the storage provider (e.g. Google Drive file id).
    // Null for local files, which are uniquely identified by their name.
    val id: String? = null
) : Parcelable