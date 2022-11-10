package info.nightscout.interfaces.iob

class Iob {

    var iobContrib = 0.0
    var activityContrib = 0.0

    fun iobContrib(iobContrib: Double): Iob {
        this.iobContrib = iobContrib
        return this
    }

    fun activityContrib(activityContrib: Double): Iob {
        this.activityContrib = activityContrib
        return this
    }

    operator fun plus(iob: Iob): Iob {
        iobContrib += iob.iobContrib
        activityContrib += iob.activityContrib
        return this
    }

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