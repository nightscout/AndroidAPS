package info.nightscout.sdk.utils

@JvmSynthetic
internal fun <E> List<E?>?.toNotNull(): List<E> = this?.filterNotNull() ?: listOf()
