package info.nightscout.androidaps.plugins.general.maintenance

import android.os.Parcelable
import info.nightscout.androidaps.plugins.general.maintenance.formats.PrefMetadata
import info.nightscout.androidaps.plugins.general.maintenance.formats.PrefsMetadataKey
import kotlinx.parcelize.RawValue
import kotlinx.parcelize.Parcelize
import java.io.File

@Parcelize
data class PrefsFile(
    val name: String,
    val file: File,
    val baseDir: File,
    val dirKind: PrefsImportDir,
    val handler: PrefsFormatsHandler,

    // metadata here is used only for list display
    val metadata: @RawValue Map<PrefsMetadataKey, PrefMetadata>
) : Parcelable

