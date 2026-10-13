package app.aaps.core.interfaces.constraints

interface Objectives {
    companion object {

        const val FIRST_OBJECTIVE = 0
        @Suppress("unused") const val USAGE_OBJECTIVE = 1
        @Suppress("unused") const val EXAM_OBJECTIVE = 2
        @Suppress("unused") const val OPEN_LOOP_OBJECTIVE = 3
        @Suppress("unused") const val SAFETY_MAX_BASAL_OBJECTIVE = 4
        const val LGS_OBJECTIVE = 5
        const val CLOSED_LOOP_OBJECTIVE = 6
        const val AUTOSENS_OBJECTIVE = 7
        const val SMB_OBJECTIVE = 8
        const val AUTO_OBJECTIVE = 9
    }

    fun isAccomplished(index: Int): Boolean
    fun isStarted(index: Int): Boolean
}