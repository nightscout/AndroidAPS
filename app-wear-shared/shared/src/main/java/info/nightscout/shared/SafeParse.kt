package info.nightscout.shared

object SafeParse {

    fun stringToDouble(inputString: String?, defaultValue: Double = 0.0): Double {
        var input = inputString ?: return defaultValue
        var result = defaultValue
        input = input.replace(",", ".")
        input = input.replace("−", "-")
        if (input == "") return defaultValue
        try {
            result = input.toDouble()
        } catch (ignored: Exception) {
            // fail over to default
        }
        return result
    }

    fun stringToFloat(inputString: String?, defaultValue: Float = 0f): Float {
        var input = inputString ?: return defaultValue
        var result = defaultValue
        input = input.replace(",", ".")
        input = input.replace("−", "-")
        if (input == "") return defaultValue
        try {
            result = input.toFloat()
        } catch (ignored: Exception) {
            // fail over to default
        }
        return result
    }

    fun stringToInt(inputString: String?, defaultValue: Int = 0): Int {
        var input = inputString ?: return defaultValue
        var result = defaultValue
        input = input.replace(",", ".")
        input = input.replace("−", "-")
        if (input == "") return defaultValue
        try {
            result = input.toInt()
        } catch (ignored: Exception) {
            // fail over to default
        }
        return result
    }

    fun stringToLong(inputString: String?, defaultValue: Long = 0L): Long {
        var input = inputString ?: return defaultValue
        var result = defaultValue
        input = input.replace(",", ".")
        input = input.replace("−", "-")
        if (input == "") return defaultValue
        try {
            result = input.toLong()
        } catch (ignored: Exception) {
            // fail over to default
        }
        return result
    }
}