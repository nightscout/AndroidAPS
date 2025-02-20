package app.aaps.plugins.main.general.smsCommunicator

abstract class SmsAction(val pumpCommand: Boolean) : Runnable {

    var aDouble: Double? = null
    var anInteger: Int? = null
    private var aLong: Long? = null
    private var secondInteger: Int? = null
    private var secondLong: Long? = null
    private var aString: String? = null

    internal constructor(pumpCommand: Boolean, aDouble: Double) : this(pumpCommand) {
        this.aDouble = aDouble
    }

    internal constructor(pumpCommand: Boolean, aDouble: Double, secondInteger: Int) : this(pumpCommand) {
        this.aDouble = aDouble
        this.secondInteger = secondInteger
    }

    internal constructor(pumpCommand: Boolean, aString: String, secondInteger: Int) : this(pumpCommand) {
        this.aString = aString
        this.secondInteger = secondInteger
    }

    internal constructor(pumpCommand: Boolean, anInteger: Int) : this(pumpCommand) {
        this.anInteger = anInteger
    }

    internal constructor(pumpCommand: Boolean, anInteger: Int, secondInteger: Int) : this(pumpCommand) {
        this.anInteger = anInteger
        this.secondInteger = secondInteger
    }

    internal constructor(pumpCommand: Boolean, anInteger: Int, aLong: Long) : this(pumpCommand) {
        this.anInteger = anInteger
        this.aLong = aLong
    }

    internal constructor(pumpCommand: Boolean, aDouble: Double, anInteger: Int, aLong: Long, secondLong: Long) : this(pumpCommand) {
        this.aDouble = aDouble
        this.anInteger = anInteger
        this.aLong = aLong
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

    fun aLong(): Long {
        return aLong?.let {
            aLong
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