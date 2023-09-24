package app.aaps.core.interfaces.constraints

interface Objectives {
    companion object {

        const val FIRST_OBJECTIVE = 0
        @Suppress("unused") const val USAGE_OBJECTIVE = 1
        @Suppress("unused") const val EXAM_OBJECTIVE = 2
        @Suppress("unused") const val OPENLOOP_OBJECTIVE = 3
        @Suppress("unused") const val MAXBASAL_OBJECTIVE = 4
        const val MAXIOB_ZERO_CL_OBJECTIVE = 5
        @Suppress("unused") const val MAXIOB_OBJECTIVE = 6
        const val AUTOSENS_OBJECTIVE = 7
        const val SMB_OBJECTIVE = 8
        const val AUTO_OBJECTIVE = 9
        const val DYN_ISF_OBJECTIVE = 10
    }

    fun isAccomplished(index: Int): Boolean
    fun isStarted(index: Int): Boolean
}