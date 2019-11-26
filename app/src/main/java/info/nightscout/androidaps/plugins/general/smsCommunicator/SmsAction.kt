package info.nightscout.androidaps.plugins.general.smsCommunicator

abstract class SmsAction : Runnable {
    var aDouble: Double? = null
    var anInteger: Int? = null
    var secondInteger: Int? = null
    var secondLong: Long? = null
    var aString: String? = null

    internal constructor()
    internal constructor(aDouble: Double) {
        this.aDouble = aDouble
    }

    internal constructor(aDouble: Double, secondInteger: Int) {
        this.aDouble = aDouble
        this.secondInteger = secondInteger
    }

    internal constructor(aString: String, secondInteger: Int) {
        this.aString = aString
        this.secondInteger = secondInteger
    }

    internal constructor(anInteger: Int) {
        this.anInteger = anInteger
    }

    internal constructor(anInteger: Int, secondInteger: Int) {
        this.anInteger = anInteger
        this.secondInteger = secondInteger
    }

    internal constructor(anInteger: Int, secondLong: Long) {
        this.anInteger = anInteger
        this.secondLong = secondLong
    }

    fun aDouble(): Double {
        return aDouble?.let {
            aDouble
        } ?: throw IllegalStateException()
    }

    fun anInteger(): Int {
        return anInteger?.let {
            anInteger
        } ?: throw IllegalStateException()
    }

    fun secondInteger(): Int {
        return secondInteger?.let {
            secondInteger
        } ?: throw IllegalStateException()
    }

    fun secondLong(): Long {
        return secondLong?.let {
            secondLong
        } ?: throw IllegalStateException()
    }

    fun aString(): String {
        return aString?.let {
            aString
        } ?: throw IllegalStateException()
    }
}