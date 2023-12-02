package app.aaps.core.interfaces.iob

class Iob {

    var iobContrib = 0.0
    var activityContrib = 0.0

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val iob = other as Iob
        return if (iob.iobContrib.compareTo(iobContrib) != 0) false
        else iob.activityContrib.compareTo(activityContrib) == 0
    }

    override fun hashCode(): Int {
        var result: Int
        var temp: Long = java.lang.Double.doubleToLongBits(iobContrib)
        result = (temp xor (temp ushr 32)).toInt()
        temp = java.lang.Double.doubleToLongBits(activityContrib)
        result = 31 * result + (temp xor (temp ushr 32)).toInt()
        return result
    }
}