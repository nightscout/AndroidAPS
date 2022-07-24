package info.nightscout.shared

object SafeParse {

    //    private static Logger log = StacktraceLoggerWrapper.getLogger(SafeParse.class);
    fun stringToDouble(inputString: String?, defaultValue: Double = 0.0): Double {
        var input = inputString ?: return defaultValue
        var result = defaultValue
        input = input.replace(",", ".")
        input = input.replace("−", "-")
        if (input == "") return defaultValue
        try {
            result = input.toDouble()
        } catch (e: Exception) {
//            log.error("Error parsing " + input + " to double");
        }
        return result
    }

    fun stringToFloat(inputString: String?): Float {
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

    fun stringToInt(inputString: String?): Int {
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