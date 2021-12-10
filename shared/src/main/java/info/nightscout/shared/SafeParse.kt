package info.nightscout.shared

import java.lang.Exception

object SafeParse {

    // TODO return logging with dagger
    //    private static Logger log = StacktraceLoggerWrapper.getLogger(SafeParse.class);
    @JvmStatic fun stringToDouble(inputString: String?): Double {
        var input = inputString ?: return 0.0
        var result = 0.0
        input = input.replace(",", ".")
        input = input.replace("−", "-")
        if (input == "") return 0.0
        try {
            result = input.toDouble()
        } catch (e: Exception) {
//            log.error("Error parsing " + input + " to double");
        }
        return result
    }

    @JvmStatic fun stringToFloat(inputString: String?): Float {
        var input = inputString ?: return 0f
        var result = 0f
        input = input.replace(",", ".")
        input = input.replace("−", "-")
        if (input == "") return 0f
        try {
            result = input.toFloat()
        } catch (e: Exception) {
//            log.error("Error parsing " + input + " to float");
        }
        return result
    }

    @JvmStatic fun stringToInt(inputString: String?): Int {
        var input = inputString ?: return 0
        var result = 0
        input = input.replace(",", ".")
        input = input.replace("−", "-")
        if (input == "") return 0
        try {
            result = input.toInt()
        } catch (e: Exception) {
//            log.error("Error parsing " + input + " to int");
        }
        return result
    }

    fun stringToLong(inputString: String?): Long {
        var input = inputString ?: return 0L
        var result = 0L
        input = input.replace(",", ".")
        input = input.replace("−", "-")
        if (input == "") return 0L
        try {
            result = input.toLong()
        } catch (e: Exception) {
//            log.error("Error parsing " + input + " to long");
        }
        return result
    }
}