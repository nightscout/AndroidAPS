package app.aaps.wear.interaction.utils

object WearUtil {

    fun msSince(whenever: Long): Long {
        return System.currentTimeMillis() - whenever
    }

}
