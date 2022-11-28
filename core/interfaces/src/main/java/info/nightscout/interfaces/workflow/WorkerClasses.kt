package info.nightscout.interfaces.workflow

import androidx.work.ListenableWorker

interface WorkerClasses {
    val nsClientSourceWorker: Class<out ListenableWorker>
    val nsProfileWorker: Class<out ListenableWorker>
    val foodWorker: Class<out ListenableWorker>
}