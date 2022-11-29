package info.nightscout.interfaces.maintenance

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.RawValue
import java.io.File

@Parcelize
data class PrefsFile(
    val name: String,
    val file: File,
    val baseDir: File,
    val dirKind: PrefsImportDir,

    // metadata here is used only for list display
    val metadata: @RawValue Map<PrefsMetadataKey, PrefMetadata>
) : Parcelable