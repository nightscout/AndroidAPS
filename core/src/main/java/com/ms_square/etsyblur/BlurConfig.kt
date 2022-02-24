package com.ms_square.etsyblur

import android.graphics.Color
import androidx.annotation.ColorInt

/**
 * BlurConfig.java
 *
 * @author Manabu-GT on 3/17/17.
 */
class BlurConfig private constructor(private val radius: Int, private val downScaleFactor: Int, @field:ColorInt @param:ColorInt private val overlayColor: Int,
                                     private val allowFallback: Boolean, private val asyncPolicy: AsyncPolicy, private val debug: Boolean) {

    fun radius(): Int {
        return radius
    }

    fun downScaleFactor(): Int {
        return downScaleFactor
    }

    fun overlayColor(): Int {
        return overlayColor
    }

    fun allowFallback(): Boolean {
        return allowFallback
    }

    fun asyncPolicy(): AsyncPolicy {
        return asyncPolicy
    }

    fun debug(): Boolean {
        return debug
    }

    class Builder {
        private var radius: Int
        private var downScaleFactor: Int

        @ColorInt
        private var overlayColor: Int
        private var allowFallback: Boolean
        private var asyncPolicy: AsyncPolicy
        private var debug: Boolean
        fun radius(radius: Int): Builder {
            checkRadius(radius)
            this.radius = radius
            return this
        }

        fun downScaleFactor(downScaleFactor: Int): Builder {
            checkDownScaleFactor(downScaleFactor)
            this.downScaleFactor = downScaleFactor
            return this
        }

        fun overlayColor(overlayColor: Int): Builder {
            this.overlayColor = overlayColor
            return this
        }

        fun allowFallback(allowFallback: Boolean): Builder {
            this.allowFallback = allowFallback
            return this
        }

        fun asyncPolicy(asyncPolicy: AsyncPolicy): Builder {
            this.asyncPolicy = asyncPolicy
            return this
        }

        fun debug(debug: Boolean): Builder {
            this.debug = debug
            return this
        }

        fun build(): BlurConfig {
            return BlurConfig(radius, downScaleFactor, overlayColor,
                allowFallback, asyncPolicy, debug)
        }

        init {
            radius = DEFAULT_RADIUS
            downScaleFactor = DEFAULT_DOWN_SCALE_FACTOR
            overlayColor = DEFAULT_OVERLAY_COLOR
            allowFallback = DEFAULT_ALLOW_FALLBACK
            asyncPolicy = DEFAULT_ASYNC_POLICY
            debug = DEFAULT_DEBUG
        }
    }

    companion object {
        const val DEFAULT_RADIUS = 10
        const val DEFAULT_DOWN_SCALE_FACTOR = 4
        const val DEFAULT_OVERLAY_COLOR = Color.TRANSPARENT
        const val DEFAULT_ALLOW_FALLBACK = true
        val DEFAULT_ASYNC_POLICY: AsyncPolicy = SimpleAsyncPolicy()
        const val DEFAULT_DEBUG = false
        @JvmField val DEFAULT_CONFIG = BlurConfig(DEFAULT_RADIUS,
            DEFAULT_DOWN_SCALE_FACTOR, DEFAULT_OVERLAY_COLOR, DEFAULT_ALLOW_FALLBACK,
            DEFAULT_ASYNC_POLICY, DEFAULT_DEBUG)

        fun checkRadius(radius: Int) {
            require(!(radius <= 0 || radius > 25)) { "radius must be greater than 0 and less than or equal to 25" }
        }

        fun checkDownScaleFactor(downScaleFactor: Int) {
            require(downScaleFactor > 0) { "downScaleFactor must be greater than 0." }
        }
    }

}