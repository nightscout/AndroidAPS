package info.nightscout.pump.combo.data

import info.nightscout.pump.combo.R
import info.nightscout.shared.sharedPreferences.SP
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ComboErrorUtil @Inject constructor(
    private val sp: SP
) {

    class ErrorState(val exception: Exception, val timeInMillis: Long)

    enum class DisplayType {
        NEVER, ON_ERROR, ALWAYS
    }

    private var errorMap: MutableMap<String, MutableList<ErrorState>> = HashMap()

    fun addError(exception: Exception) {
        val exceptionMsg = exception.message ?: return
        if (!errorMap.containsKey(exceptionMsg)) errorMap[exceptionMsg] = ArrayList<ErrorState>().also { it.add(createErrorState(exception)) }
        else errorMap[exceptionMsg]?.add(createErrorState(exception))
        updateErrorCount()
    }

    private fun updateErrorCount() {
        var errorCount = 0
        if (!isErrorPresent) for (errorStates in errorMap.values) errorCount += errorStates.size
        if (errorCount == 0) {
            if (sp.contains(R.string.key_combo_error_count)) sp.remove(R.string.key_combo_error_count)
        } else sp.putInt(R.string.key_combo_error_count, errorCount)
    }

    private fun createErrorState(exception: Exception) = ErrorState(exception, System.currentTimeMillis())

    fun clearErrors() {
        errorMap.clear()
        if (sp.contains(R.string.key_combo_error_count)) sp.remove(R.string.key_combo_error_count)
    }

    private val isErrorPresent: Boolean
        get() = errorMap.isNotEmpty()
    val errorCount: Int
        get() = sp.getInt(R.string.key_combo_error_count, -1)
    val displayType: DisplayType
        get() = DisplayType.valueOf(sp.getString(R.string.key_show_comm_error_count, "ON_ERROR"))
}