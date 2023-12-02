package app.aaps.core.nssdk.utils

@JvmSynthetic
internal fun <E> List<E?>?.toNotNull(): List<E> = this?.filterNotNull() ?: listOf()
