package info.nightscout.interfaces.maintenance

import android.os.Parcelable
import info.nightscout.rx.weardata.CustomWatchfaceDrawableDataKey
import info.nightscout.rx.weardata.CustomWatchfaceDrawableDataMap
import info.nightscout.rx.weardata.CustomWatchfaceMetadataMap
import info.nightscout.rx.weardata.DrawableData
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.RawValue
import java.io.File

data class CustomWatchfaceFile(
    val name: String,
    val file: File,
    val baseDir: File,
    val json: String,

    val metadata: @RawValue CustomWatchfaceMetadataMap,
    val drawableFiles: @RawValue CustomWatchfaceDrawableDataMap

)