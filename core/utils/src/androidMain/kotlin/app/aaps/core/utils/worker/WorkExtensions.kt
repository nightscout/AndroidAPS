package app.aaps.core.utils.worker

import androidx.work.OneTimeWorkRequest
import androidx.work.WorkContinuation

fun WorkContinuation.then(runIf: Boolean, work: OneTimeWorkRequest): WorkContinuation =
    if (runIf) then(work) else this

