package com.ms_square.etsyblur

import android.os.AsyncTask
import androidx.annotation.CallSuper
import java.util.*

/**
 * BaseBlurEngine.java
 *
 * @author Manabu-GT on 3/22/17.
 */
internal abstract class BaseBlurEngine(val blurConfig: BlurConfig) : BlurEngine {

    @JvmField val asyncTasks: MutableList<AsyncTask<*, *, *>> = LinkedList()
    @CallSuper override fun destroy() {
        for (asyncTask in asyncTasks) {
            asyncTask.cancel(true)
        }
        asyncTasks.clear()
    }

    abstract fun calculateComputation(bmpWidth: Int, bmpHeight: Int, radius: Int): Long
    abstract fun shouldAsync(bmpWidth: Int, bmpHeight: Int, radius: Int): Boolean

}