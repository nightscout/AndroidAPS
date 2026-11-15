package app.aaps.shared.impl.weardata

import android.content.res.Resources
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.PictureDrawable
import app.aaps.core.interfaces.rx.weardata.ResData
import app.aaps.core.interfaces.rx.weardata.ResFormat
import com.caverock.androidsvg.SVG
import java.io.File
import java.io.FileOutputStream

fun ResData.toDrawable(resources: Resources, width: Int? = null, height: Int? = null): Drawable? {
    try {
        return when (format) {
            ResFormat.PNG, ResFormat.JPG -> {
                val bitmap = BitmapFactory.decodeByteArray(value, 0, value.size)
                BitmapDrawable(resources, bitmap)
            }

            ResFormat.SVG                -> {
                val svg = SVG.getFromString(String(value))
                svg.documentWidth = width?.toFloat() ?: svg.documentWidth
                svg.documentHeight = height?.toFloat() ?: svg.documentHeight
                val picture = svg.renderToPicture()
                PictureDrawable(picture).apply {
                    setBounds(0, 0, svg.documentWidth.toInt(), svg.documentHeight.toInt())
                }
            }

            else                         -> null
        }
    } catch (_: Exception) {
        return null
    }
}

fun ResData.toTypeface(): Typeface? {
    try {
        return when (format) {
            ResFormat.TTF, ResFormat.OTF -> {
                // Workaround with temporary File, Typeface.createFromFileDescriptor(null, value, 0, value.size) more simple not available
                File.createTempFile("temp", format.extension).let { tempFile ->
                    FileOutputStream(tempFile).let { fileOutputStream ->
                        fileOutputStream.write(value)
                        fileOutputStream.close()
                    }

                    Typeface.createFromFile(tempFile).let {
                        if (!tempFile.delete()) {
                            // delete tempfile after usage
                        }
                        it
                    }
                }
            }

            else                         -> {
                null

            }
        }
    } catch (_: Exception) {
        return null
    }
}
