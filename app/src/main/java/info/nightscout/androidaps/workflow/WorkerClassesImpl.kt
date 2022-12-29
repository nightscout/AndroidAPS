package info.nightscout.androidaps.workflow

import info.nightscout.interfaces.workflow.WorkerClasses
import info.nightscout.plugins.profile.ProfilePlugin
import info.nightscout.source.NSClientSourcePlugin
import javax.inject.Inject

class WorkerClassesImpl @Inject constructor(): WorkerClasses{

    override val nsClientSourceWorker = NSClientSourcePlugin.NSClientSourceWorker::class.java
    override val nsProfileWorker = ProfilePlugin.NSProfileWorker::class.java
}